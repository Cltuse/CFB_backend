package com.facility.booking.service;

import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.User;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.repository.ViolationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ViolationRecordService {

    @Autowired
    private ViolationRecordRepository violationRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private BlacklistService blacklistService;

    /**
     * 系统启动后执行一次违规数据同步与自动检测。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        System.out.println("系统启动，开始同步违规统计并执行自动违规检测...");
        syncAllUserViolationStats();
        autoDetectViolations();
        System.out.println("违规统计同步与自动检测完成");
    }

    /**
     * 定时任务：自动检测超时使用与爽约违规。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    @Transactional
    public void autoDetectViolations() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("开始执行自动违规检测，当前时间: " + now);

        int overdueCount = 0;
        int noShowCount = 0;

        try {
            overdueCount = detectOverdueViolations(now);
            noShowCount = detectNoShowViolations(now);

            int totalCount = overdueCount + noShowCount;
            if (totalCount > 0) {
                System.out.println("自动违规检测完成，共发现 " + totalCount + " 条记录，其中超时使用 "
                        + overdueCount + " 条，爽约 " + noShowCount + " 条");
            } else {
                System.out.println("自动违规检测完成，未发现新的违规记录");
            }
        } catch (Exception e) {
            System.err.println("自动违规检测失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检测超时使用违规。
     */
    private int detectOverdueViolations(LocalDateTime now) {
        int count = 0;

        List<Reservation> overdueReservations = reservationRepository
                .findByCheckinStatusAndEndTimeBefore("CHECKED_IN", now.minusMinutes(30))
                .stream()
                .filter(reservation -> reservation.getCheckoutTime() == null)
                .collect(Collectors.toList());

        for (Reservation reservation : overdueReservations) {
            try {
                boolean exists = violationRecordRepository.existsByReservationIdAndViolationType(
                        reservation.getId(), "OVERDUE"
                );

                if (!exists) {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("OVERDUE");
                    violationRecord.setDescription(
                            "超时使用：预约结束后已超过 30 分钟，用户仍未签退。预约时间："
                                    + reservation.getStartTime() + " 至 " + reservation.getEndTime()
                    );
                    violationRecord.setPenaltyPoints(3);
                    violationRecord.setReportedBy(1L);
                    violationRecord.setReportedTime(now);
                    violationRecord.setStatus("PENDING");

                    violationRecordRepository.save(violationRecord);
                    count++;

                    reservation.setCheckinStatus("COMPLETED");
                    reservationRepository.save(reservation);

                    System.out.println("检测到超时使用违规：用户ID=" + reservation.getUserId()
                            + "，预约ID=" + reservation.getId());
                }
            } catch (Exception e) {
                System.err.println("创建超时使用违规记录失败: " + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 检测爽约违规。
     */
    private int detectNoShowViolations(LocalDateTime now) {
        int count = 0;

        List<Reservation> noShowReservations = reservationRepository
                .findByStatusAndCheckinStatus("APPROVED", "NOT_CHECKED")
                .stream()
                .filter(reservation -> reservation.getStartTime() != null
                        && now.isAfter(reservation.getStartTime().plusMinutes(15)))
                .collect(Collectors.toList());

        for (Reservation reservation : noShowReservations) {
            try {
                boolean exists = violationRecordRepository.existsByReservationIdAndViolationType(
                        reservation.getId(), "NO_SHOW"
                );

                if (!exists) {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("NO_SHOW");
                    violationRecord.setDescription(
                            "爽约：预约开始超过 15 分钟仍未签到。预约时间："
                                    + reservation.getStartTime() + " 至 " + reservation.getEndTime()
                    );
                    violationRecord.setPenaltyPoints(5);
                    violationRecord.setReportedBy(1L);
                    violationRecord.setReportedTime(now);
                    violationRecord.setStatus("PENDING");

                    violationRecordRepository.save(violationRecord);
                    count++;

                    reservation.setCheckinStatus("MISSED");
                    reservationRepository.save(reservation);

                    System.out.println("检测到爽约违规：用户ID=" + reservation.getUserId()
                            + "，预约ID=" + reservation.getId());
                }
            } catch (Exception e) {
                System.err.println("创建爽约违规记录失败: " + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 新增违规记录。
     */
    @Transactional
    public ViolationRecord recordViolation(ViolationRecord violationRecord) {
        if (violationRecord == null) {
            throw new IllegalArgumentException("违规记录不能为空");
        }
        if (violationRecord.getUserId() == null) {
            throw new IllegalArgumentException("违规用户不能为空");
        }

        if (violationRecord.getReservationId() != null) {
            Reservation reservation = reservationRepository.findById(violationRecord.getReservationId())
                    .orElseThrow(() -> new IllegalArgumentException("关联预约不存在"));
            if (!Objects.equals(reservation.getUserId(), violationRecord.getUserId())) {
                throw new IllegalArgumentException("违规用户与关联预约不匹配");
            }
        }

        if (violationRecord.getReportedTime() == null) {
            violationRecord.setReportedTime(LocalDateTime.now());
        }
        if (violationRecord.getStatus() == null || violationRecord.getStatus().isBlank()) {
            violationRecord.setStatus("PENDING");
        }
        if (violationRecord.getPenaltyPoints() == null) {
            violationRecord.setPenaltyPoints(0);
        }

        String[] allowedTypes = {"NO_SHOW", "OVERDUE", "CANCEL_FREQ", "DAMAGE", "OTHER"};
        if (violationRecord.getViolationType() == null
                || !java.util.Arrays.asList(allowedTypes).contains(violationRecord.getViolationType())) {
            violationRecord.setViolationType("OTHER");
        }

        ViolationRecord savedViolation = violationRecordRepository.save(violationRecord);
        enrichViolation(savedViolation);
        return savedViolation;
    }

    /**
     * 重新计算用户信用分与违规次数。
     */
    @Transactional
    public void recalculateUserCreditScoreAndViolationCount(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            Integer totalPenaltyPoints = getProcessedPenaltyPoints(userId);
            Integer totalViolationCount = violationRecordRepository.countAllViolationsByUserId(userId);
            user.setCreditScore(Math.max(0, 100 - totalPenaltyPoints));
            user.setViolationCount(totalViolationCount != null ? totalViolationCount : 0);
            userRepository.save(user);
        });
    }

    @Transactional
    public void syncAllUserViolationStats() {
        userRepository.findAll().forEach(user -> recalculateUserCreditScoreAndViolationCount(user.getId()));
    }

    public Integer getProcessedPenaltyPoints(Long userId) {
        Integer penalty = violationRecordRepository.sumProcessedPenaltyPointsByUserId(userId);
        return penalty != null ? penalty : 0;
    }

    public Page<ViolationRecord> getUserViolations(Long userId, Pageable pageable) {
        Page<ViolationRecord> page = violationRecordRepository.findByUserIdOrderByReportedTimeDesc(userId, pageable);
        enrichViolations(page.getContent());
        return page;
    }

    public Long getActiveViolationCount(Long userId) {
        Integer count = violationRecordRepository.countProcessedViolationsByUserId(userId);
        return count != null ? count.longValue() : 0L;
    }

    public Integer getTotalPenaltyPoints(Long userId) {
        Integer penalty = violationRecordRepository.sumPenaltyPointsByUserId(userId);
        return penalty != null ? penalty : 0;
    }

    /**
     * 确认违规记录。
     */
    @Transactional
    public Map<String, Object> approveViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (violationOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "违规记录不存在");
            return result;
        }

        ViolationRecord violation = violationOpt.get();
        if (!"PENDING".equals(violation.getStatus())) {
            result.put("success", false);
            result.put("message", "该违规记录已处理，不能重复确认");
            return result;
        }

        violation.setStatus("PROCESSED");
        violation.setRemark(remark);
        violation.setReportedBy(adminId);
        violationRecordRepository.save(violation);

        Long userId = violation.getUserId();
        recalculateUserCreditScoreAndViolationCount(userId);
        userRepository.findById(userId).ifPresent(user -> checkAndAddToBlacklist(user, adminId));

        enrichViolation(violation);
        result.put("success", true);
        result.put("message", "违规确认成功，已扣除信用分 " + violation.getPenaltyPoints() + " 分");
        result.put("violation", violation);
        return result;
    }

    /**
     * 驳回违规记录。
     */
    @Transactional
    public Map<String, Object> rejectViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (violationOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "违规记录不存在");
            return result;
        }

        ViolationRecord violation = violationOpt.get();
        if (!"PENDING".equals(violation.getStatus())) {
            result.put("success", false);
            result.put("message", "该违规记录已处理，不能重复操作");
            return result;
        }

        violation.setStatus("REJECTED");
        violation.setRemark(remark);
        violation.setReportedBy(adminId);
        violationRecordRepository.save(violation);
        recalculateUserCreditScoreAndViolationCount(violation.getUserId());

        enrichViolation(violation);
        result.put("success", true);
        result.put("message", "违规已驳回");
        result.put("violation", violation);
        return result;
    }

    /**
     * 撤销已生效的违规记录。
     */
    @Transactional
    public Map<String, Object> revokeViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (violationOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "违规记录不存在");
            return result;
        }

        ViolationRecord violation = violationOpt.get();
        if (!"PROCESSED".equals(violation.getStatus())) {
            result.put("success", false);
            result.put("message", "只能撤销已生效的违规记录");
            return result;
        }

        violation.setStatus("REVOKED");
        violation.setRemark(remark);
        violation.setReportedBy(adminId);
        violationRecordRepository.save(violation);

        recalculateUserCreditScoreAndViolationCount(violation.getUserId());

        enrichViolation(violation);
        result.put("success", true);
        result.put("message", "违规记录已撤销生效");
        result.put("violation", violation);
        return result;
    }

    /**
     * 检查用户是否满足加入黑名单的条件。
     */
    @Transactional
    public void checkAndAddToBlacklist(User user, Long adminId) {
        if (user == null) {
            return;
        }

        recalculateUserCreditScoreAndViolationCount(user.getId());
        User latestUser = userRepository.findById(user.getId()).orElse(user);
        Integer creditScore = latestUser.getCreditScore() != null ? latestUser.getCreditScore() : 100;

        if (creditScore < 60) {
            addToBlacklist(latestUser.getId(), "信用分低于 60 分", 30, adminId);
            return;
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Integer recentViolations = violationRecordRepository.countRecentProcessedViolations(
                latestUser.getId(), "PROCESSED", thirtyDaysAgo
        );
        if (recentViolations != null && recentViolations >= 3) {
            addToBlacklist(latestUser.getId(), "30 天内违规 " + recentViolations + " 次", 7, adminId);
        }
    }

    private void addToBlacklist(Long userId, String reason, int days, Long adminId) {
        try {
            blacklistService.addToBlacklist(userId, reason, days, adminId);
            System.out.println("用户 " + userId + " 因 \"" + reason + "\" 被加入黑名单");
        } catch (Exception e) {
            System.err.println("自动加入黑名单失败: " + e.getMessage());
        }
    }

    public Optional<ViolationRecord> getViolationById(Long id) {
        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(id);
        violationOpt.ifPresent(this::enrichViolation);
        return violationOpt;
    }

    /**
     * 更新违规记录状态。
     */
    @Transactional
    public boolean updateViolationStatus(Long id, String status, Long reportedBy) {
        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(id);
        if (violationOpt.isEmpty()) {
            return false;
        }

        ViolationRecord violation = violationOpt.get();
        String currentStatus = violation.getStatus();

        if ("PROCESSED".equals(status) && !"PROCESSED".equals(currentStatus)) {
            return Boolean.TRUE.equals(approveViolation(id, reportedBy, violation.getRemark()).get("success"));
        }
        if ("REJECTED".equals(status) && !"REJECTED".equals(currentStatus)) {
            return Boolean.TRUE.equals(rejectViolation(id, reportedBy, violation.getRemark()).get("success"));
        }
        if ("REVOKED".equals(status) && "PROCESSED".equals(currentStatus)) {
            return Boolean.TRUE.equals(revokeViolation(id, reportedBy, violation.getRemark()).get("success"));
        }

        violation.setStatus(status);
        violation.setReportedBy(reportedBy);
        violationRecordRepository.save(violation);
        recalculateUserCreditScoreAndViolationCount(violation.getUserId());
        return true;
    }

    public Page<ViolationRecord> getUserViolationsByTimeRange(Long userId,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime,
                                                              Pageable pageable) {
        Page<ViolationRecord> page = violationRecordRepository.findByUserIdAndTimeRange(userId, startTime, endTime, pageable);
        enrichViolations(page.getContent());
        return page;
    }

    /**
     * 获取全部违规记录，供系统管理员使用。
     */
    public Page<ViolationRecord> getAllViolations(Pageable pageable, String userName, String violationType, String status) {
        Page<ViolationRecord> violations;

        boolean hasUserName = userName != null && !userName.trim().isEmpty();
        boolean hasViolationType = violationType != null && !violationType.trim().isEmpty();
        boolean hasStatus = status != null && !status.trim().isEmpty();

        if (hasUserName && hasViolationType && hasStatus) {
            violations = violationRecordRepository.findByFilters(userName, violationType, status, pageable);
        } else if (hasUserName && hasViolationType) {
            violations = violationRecordRepository.findByUserNameAndViolationType(userName, violationType, pageable);
        } else if (hasUserName && hasStatus) {
            violations = violationRecordRepository.findByUserNameAndStatus(userName, status, pageable);
        } else if (hasViolationType && hasStatus) {
            violations = violationRecordRepository.findByViolationTypeAndStatus(violationType, status, pageable);
        } else if (hasUserName) {
            violations = violationRecordRepository.findByUserNameContainingIgnoreCase(userName, pageable);
        } else if (hasViolationType) {
            violations = violationRecordRepository.findByViolationType(violationType, pageable);
        } else if (hasStatus) {
            violations = violationRecordRepository.findByStatus(status, pageable);
        } else {
            violations = violationRecordRepository.findAllByOrderByReportedTimeDesc(pageable);
        }

        enrichViolations(violations.getContent());
        return violations;
    }

    public Integer getUserCurrentCreditScore(Long userId) {
        recalculateUserCreditScoreAndViolationCount(userId);
        return Math.max(0, 100 - getProcessedPenaltyPoints(userId));
    }

    public Integer getUserViolationCount(Long userId) {
        recalculateUserCreditScoreAndViolationCount(userId);
        Integer count = violationRecordRepository.countAllViolationsByUserId(userId);
        return count != null ? count : 0;
    }

    public Integer getUserActiveViolationCount(Long userId) {
        Integer count = violationRecordRepository.countProcessedViolationsByUserId(userId);
        return count != null ? count : 0;
    }

    /**
     * 获取设施管理员可处理的违规记录。
     */
    public Page<ViolationRecord> getMaintainerViolations(Pageable pageable,
                                                         Long maintainerId,
                                                         String userName,
                                                         String violationType,
                                                         String status) {
        if (maintainerId == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<Long> reservationIds = getMaintainerReservationIds(maintainerId);
        if (reservationIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<ViolationRecord> allViolations = violationRecordRepository
                .findByReservationIdInOrderByReportedTimeDesc(reservationIds);
        enrichViolations(allViolations);

        List<ViolationRecord> filtered = allViolations.stream()
                .filter(violation -> matchesUserName(violation, userName))
                .filter(violation -> matchesViolationType(violation, violationType))
                .filter(violation -> matchesStatus(violation, status))
                .toList();

        int fromIndex = Math.min((int) pageable.getOffset(), filtered.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
        List<ViolationRecord> pageContent = filtered.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    /**
     * 获取违规统计数据。
     */
    public Map<String, Object> getViolationStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long totalViolations = violationRecordRepository.count();
            long pendingViolations = violationRecordRepository.countByStatusPending();
            int totalPenaltyPoints = violationRecordRepository.sumAllProcessedPenaltyPoints();

            stats.put("totalViolations", totalViolations);
            stats.put("pendingViolations", pendingViolations);
            stats.put("totalPenaltyPoints", totalPenaltyPoints);
            return stats;
        } catch (Exception e) {
            System.err.println("获取违规统计数据失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("获取违规统计数据失败: " + e.getMessage());
        }
    }

    public boolean canMaintainerAccessReservation(Long maintainerId, Long reservationId) {
        if (maintainerId == null || reservationId == null) {
            return false;
        }

        Optional<Reservation> reservationOpt = reservationRepository.findById(reservationId);
        if (reservationOpt.isEmpty()) {
            return false;
        }

        Reservation reservation = reservationOpt.get();
        return facilityRepository.findById(reservation.getFacilityId())
                .map(Facility::getMaintainerId)
                .map(ownerId -> Objects.equals(ownerId, maintainerId))
                .orElse(false);
    }

    public boolean canMaintainerAccessViolation(Long maintainerId, ViolationRecord violation) {
        if (violation == null || violation.getReservationId() == null) {
            return false;
        }
        return canMaintainerAccessReservation(maintainerId, violation.getReservationId());
    }

    private List<Long> getMaintainerReservationIds(Long maintainerId) {
        List<Long> facilityIds = facilityRepository.findByMaintainerId(maintainerId)
                .stream()
                .map(Facility::getId)
                .filter(Objects::nonNull)
                .toList();

        if (facilityIds.isEmpty()) {
            return List.of();
        }

        return reservationRepository.findByFacilityIdIn(facilityIds)
                .stream()
                .map(Reservation::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean matchesUserName(ViolationRecord violation, String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            return true;
        }
        String keyword = userName.trim().toLowerCase();
        String displayName = violation.getUserName() == null ? "" : violation.getUserName().toLowerCase();
        return displayName.contains(keyword);
    }

    private boolean matchesViolationType(ViolationRecord violation, String violationType) {
        return violationType == null || violationType.trim().isEmpty()
                || violationType.equals(violation.getViolationType());
    }

    private boolean matchesStatus(ViolationRecord violation, String status) {
        return status == null || status.trim().isEmpty()
                || status.equals(violation.getStatus());
    }

    private void enrichViolations(List<ViolationRecord> violations) {
        if (violations == null || violations.isEmpty()) {
            return;
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> reporterIds = new HashSet<>();
        Set<Long> reservationIds = new HashSet<>();

        for (ViolationRecord violation : violations) {
            if (violation.getUserId() != null) {
                userIds.add(violation.getUserId());
            }
            if (violation.getReportedBy() != null) {
                reporterIds.add(violation.getReportedBy());
            }
            if (violation.getReservationId() != null) {
                reservationIds.add(violation.getReservationId());
            }
        }

        Map<Long, User> usersById = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, User> reportersById = userRepository.findAllById(reporterIds)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Reservation> reservationsById = reservationRepository.findAllById(reservationIds)
                .stream()
                .collect(Collectors.toMap(Reservation::getId, reservation -> reservation));

        Set<Long> facilityIds = reservationsById.values().stream()
                .map(Reservation::getFacilityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Facility> facilitiesById = facilityRepository.findAllById(facilityIds)
                .stream()
                .collect(Collectors.toMap(Facility::getId, facility -> facility));

        for (ViolationRecord violation : violations) {
            User user = usersById.get(violation.getUserId());
            if (user != null) {
                violation.setUserName(resolveUserDisplayName(user));
            } else {
                violation.setUserName("未知用户");
            }

            if (violation.getReportedBy() != null) {
                User reporter = reportersById.get(violation.getReportedBy());
                violation.setReporterName(reporter != null ? resolveUserDisplayName(reporter) : "系统记录");
            } else {
                violation.setReporterName("系统记录");
            }

            if (violation.getReservationId() != null) {
                Reservation reservation = reservationsById.get(violation.getReservationId());
                if (reservation != null) {
                    Facility facility = facilitiesById.get(reservation.getFacilityId());
                    if (facility != null) {
                        violation.setFacilityName(facility.getName());
                    }
                }
            }
        }
    }

    private void enrichViolation(ViolationRecord violation) {
        if (violation == null) {
            return;
        }
        List<ViolationRecord> single = new ArrayList<>();
        single.add(violation);
        enrichViolations(single);
    }

    private String resolveUserDisplayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getUsername() != null ? user.getUsername() : "未知用户";
    }
}
