package com.facility.booking.service;

import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.repository.ViolationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private BlacklistService blacklistService;

    /**
     * 系统启动时执行一次违规检测
     */
    @Async
    public void onStartup() {
        System.out.println("系统启动，开始执行违规检测...");
        syncAllUserViolationStats();
        autoDetectViolations();
        System.out.println("系统启动违规检测完成");
    }

    /**
     * 定时任务：自动检测违规记录
     * 每5分钟执行一次，检测超时使用和爽约违规
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    @Transactional
    public void autoDetectViolations() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("开始执行自动违规检测，当前时间：" + now);

        int overdueCount = 0;
        int noShowCount = 0;

        try {
            overdueCount = detectOverdueViolations(now);
            noShowCount = detectNoShowViolations(now);

            int totalCount = overdueCount + noShowCount;
            if (totalCount > 0) {
                System.out.println("自动违规检测完成，检测到 " + totalCount + " 条违规记录，超时使用 "
                        + overdueCount + " 条，爽约 " + noShowCount + " 条");
            } else {
                System.out.println("自动违规检测完成，未发现新的违规记录");
            }
        } catch (Exception e) {
            System.err.println("自动违规检测失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检测超时使用违规
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
                boolean exists = violationRecordRepository
                        .existsByReservationIdAndViolationType(reservation.getId(), "OVERDUE");

                if (!exists) {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("OVERDUE");
                    violationRecord.setDescription("超时使用：预约结束时间已超过30分钟，用户仍未签退。预约时间："
                            + reservation.getStartTime() + " 至 " + reservation.getEndTime());
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
                System.err.println("创建超时使用违规记录失败：" + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 检测爽约违规（NO_SHOW）
     * 预约开始时间已超过15分钟，用户仍未签到
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
                boolean exists = violationRecordRepository
                        .existsByReservationIdAndViolationType(reservation.getId(), "NO_SHOW");

                if (!exists) {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("NO_SHOW");
                    violationRecord.setDescription("爽约：预约开始时间已超过15分钟，用户仍未签到。预约时间："
                            + reservation.getStartTime() + " 至 " + reservation.getEndTime());
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
                System.err.println("创建爽约违规记录失败：" + e.getMessage());
            }
        }

        return count;
    }

    /**
     * 记录违规
     * 初始状态为 PENDING，不立即扣分，等待管理员审核
     */
    @Transactional
    public ViolationRecord recordViolation(ViolationRecord violationRecord) {
        if (violationRecord == null) {
            throw new IllegalArgumentException("违规记录不能为空");
        }
        if (violationRecord.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        violationRecord.setStatus("PENDING");

        String[] allowedTypes = {"NO_SHOW", "OVERDUE", "CANCEL_FREQ", "DAMAGE", "OTHER"};
        if (violationRecord.getViolationType() != null
                && !java.util.Arrays.asList(allowedTypes).contains(violationRecord.getViolationType())) {
            violationRecord.setViolationType("OTHER");
        }

        if (violationRecord.getPenaltyPoints() == null) {
            violationRecord.setPenaltyPoints(0);
        }

        return violationRecordRepository.save(violationRecord);
    }

    /**
     * 重新计算用户信用分和违规次数
     * 信用分 = 100 - 已生效违规记录的处罚分总和
     * 违规次数 = 所有违规记录总数
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
        userRepository.findAll().forEach(user ->
                recalculateUserCreditScoreAndViolationCount(user.getId()));
    }

    /**
     * 获取用户已生效违规记录的处罚分
     */
    public Integer getProcessedPenaltyPoints(Long userId) {
        Integer penalty = violationRecordRepository.sumProcessedPenaltyPointsByUserId(userId);
        return penalty != null ? penalty : 0;
    }

    /**
     * 获取用户的违规记录
     */
    public Page<ViolationRecord> getUserViolations(Long userId, Pageable pageable) {
        Page<ViolationRecord> violations = violationRecordRepository.findByUserIdOrderByReportedTimeDesc(userId, pageable);
        violations.forEach(violation -> {
            userRepository.findById(violation.getUserId()).ifPresent(user ->
                    violation.setUserName(user.getName()));
            if (violation.getReportedBy() != null) {
                userRepository.findById(violation.getReportedBy()).ifPresent(user ->
                        violation.setReporterName(user.getName()));
            }
            if (violation.getReservationId() != null) {
                // 如需展示设施名称，可根据预约ID查询并补充设施信息
            }
        });
        return violations;
    }

    /**
     * 获取用户的生效违规记录数量
     */
    public Long getActiveViolationCount(Long userId) {
        Integer count = violationRecordRepository.countProcessedViolationsByUserId(userId);
        return count != null ? count.longValue() : 0L;
    }

    /**
     * 获取用户的总处罚分（包含所有状态，用于统计）
     */
    public Integer getTotalPenaltyPoints(Long userId) {
        Integer penalty = violationRecordRepository.sumPenaltyPointsByUserId(userId);
        return penalty != null ? penalty : 0;
    }

    /**
     * 管理员确认违规记录
     * 将 PENDING 状态更新为 PROCESSED 并扣除信用分
     */
    @Transactional
    public Map<String, Object> approveViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (!violationOpt.isPresent()) {
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

        result.put("success", true);
        result.put("message", "违规确认成功，已扣除信用分 " + violation.getPenaltyPoints() + " 分");
        result.put("violation", violation);
        return result;
    }

    /**
     * 管理员拒绝违规记录
     * 拒绝后将状态更新为 REJECTED
     */
    @Transactional
    public Map<String, Object> rejectViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (!violationOpt.isPresent()) {
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

        result.put("success", true);
        result.put("message", "违规已拒绝");
        result.put("violation", violation);
        return result;
    }

    /**
     * 管理员撤销已生效的违规记录
     */
    @Transactional
    public Map<String, Object> revokeViolation(Long violationId, Long adminId, String remark) {
        Map<String, Object> result = new HashMap<>();

        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(violationId);
        if (!violationOpt.isPresent()) {
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

        result.put("success", true);
        result.put("message", "违规记录已撤销生效");
        result.put("violation", violation);
        return result;
    }

    /**
     * 检查用户是否需要加入黑名单
     * 信用分低于60分加入30天，30天内违规3次及以上加入7天
     */
    @Transactional
    public void checkAndAddToBlacklist(com.facility.booking.entity.User user, Long adminId) {
        if (user == null) {
            return;
        }

        recalculateUserCreditScoreAndViolationCount(user.getId());
        com.facility.booking.entity.User latestUser = userRepository.findById(user.getId()).orElse(user);
        Integer creditScore = latestUser.getCreditScore() != null ? latestUser.getCreditScore() : 100;

        if (creditScore < 60) {
            addToBlacklist(latestUser.getId(), "信用分低于60分", 30, adminId);
            return;
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Integer recentViolations = violationRecordRepository.countRecentProcessedViolations(
                latestUser.getId(), "PROCESSED", thirtyDaysAgo);
        if (recentViolations != null && recentViolations >= 3) {
            addToBlacklist(latestUser.getId(), "30天内违规" + recentViolations + "次", 7, adminId);
        }
    }

    /**
     * 添加用户到黑名单
     */
    private void addToBlacklist(Long userId, String reason, int days, Long adminId) {
        try {
            blacklistService.addToBlacklist(userId, reason, days, adminId);
            System.out.println("用户 " + userId + " 因 " + reason + " 被自动加入黑名单");
        } catch (Exception e) {
            System.err.println("自动加入黑名单失败：" + e.getMessage());
        }
    }

    /**
     * 根据 ID 获取违规记录详情
     */
    public Optional<ViolationRecord> getViolationById(Long id) {
        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(id);
        if (violationOpt.isPresent()) {
            ViolationRecord violation = violationOpt.get();
            userRepository.findById(violation.getUserId()).ifPresent(user ->
                    violation.setUserName(user.getName()));
            if (violation.getReportedBy() != null) {
                userRepository.findById(violation.getReportedBy()).ifPresent(user ->
                        violation.setReporterName(user.getName()));
            }
        }
        return violationOpt;
    }

    /**
     * 更新违规记录状态
     */
    @Transactional
    public boolean updateViolationStatus(Long id, String status, Long reportedBy) {
        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(id);
        if (violationOpt.isPresent()) {
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
        return false;
    }

    /**
     * 获取用户某段时间内的违规记录
     */
    public Page<ViolationRecord> getUserViolationsByTimeRange(Long userId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Page<ViolationRecord> violations = violationRecordRepository.findByUserIdAndTimeRange(userId, startTime, endTime, pageable);
        violations.forEach(violation -> {
            userRepository.findById(violation.getUserId()).ifPresent(user ->
                    violation.setUserName(user.getName()));
            if (violation.getReportedBy() != null) {
                userRepository.findById(violation.getReportedBy()).ifPresent(user ->
                        violation.setReporterName(user.getName()));
            }
        });
        return violations;
    }

    /**
     * 获取所有违规记录（管理员使用）
     */
    public Page<ViolationRecord> getAllViolations(Pageable pageable, String userName, String violationType, String status) {
        try {
            if (violationRecordRepository == null) {
                throw new RuntimeException("ViolationRecordRepository is null - database connection issue");
            }
            if (userRepository == null) {
                throw new RuntimeException("UserRepository is null - database connection issue");
            }

            System.out.println("Fetching violations with filters - userName: " + userName + ", violationType: " + violationType + ", status: " + status);

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

            violations.forEach(violation -> {
                try {
                    userRepository.findById(violation.getUserId()).ifPresent(user -> {
                        if (user.getName() != null) {
                            violation.setUserName(user.getName());
                        } else {
                            violation.setUserName("未知用户");
                        }
                    });
                    if (violation.getReportedBy() != null) {
                        userRepository.findById(violation.getReportedBy()).ifPresent(user -> {
                            if (user.getName() != null) {
                                violation.setReporterName(user.getName());
                            } else {
                                violation.setReporterName("未知上报人");
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Error enriching violation record " + violation.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });

            return violations;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取所有违规记录失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户当前信用分
     */
    public Integer getUserCurrentCreditScore(Long userId) {
        recalculateUserCreditScoreAndViolationCount(userId);
        return Math.max(0, 100 - getProcessedPenaltyPoints(userId));
    }

    /**
     * 获取用户违规次数
     */
    public Integer getUserViolationCount(Long userId) {
        recalculateUserCreditScoreAndViolationCount(userId);
        Integer count = violationRecordRepository.countAllViolationsByUserId(userId);
        return count != null ? count : 0;
    }

    /**
     * 获取用户生效中的违规次数
     */
    public Integer getUserActiveViolationCount(Long userId) {
        Integer count = violationRecordRepository.countProcessedViolationsByUserId(userId);
        return count != null ? count : 0;
    }

    /**
     * 获取维护人员上报的违规记录
     */
    public Page<ViolationRecord> getMaintainerViolations(Pageable pageable, Long maintainerId, String userName, String violationType, String status) {
        try {
            Page<ViolationRecord> violations;
            if (maintainerId != null) {
                violations = violationRecordRepository.findByReportedByOrderByReportedTimeDesc(maintainerId, pageable);
            } else {
                violations = violationRecordRepository.findByReportedByIsNotNullOrderByReportedTimeDesc(pageable);
            }

            violations.forEach(violation -> {
                try {
                    userRepository.findById(violation.getUserId()).ifPresent(user -> {
                        if (user.getName() != null) {
                            violation.setUserName(user.getName());
                        } else {
                            violation.setUserName("未知用户");
                        }
                    });

                    if (violation.getReportedBy() != null) {
                        userRepository.findById(violation.getReportedBy()).ifPresent(user -> {
                            if (user.getName() != null) {
                                violation.setReporterName(user.getName());
                            } else {
                                violation.setReporterName("未知上报人");
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Error enriching violation record " + violation.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });

            if ((userName != null && !userName.trim().isEmpty())
                    || (violationType != null && !violationType.trim().isEmpty())
                    || (status != null && !status.trim().isEmpty())) {

                List<ViolationRecord> filteredList = violations.getContent().stream()
                        .filter(violation -> {
                            if (userName != null && !userName.trim().isEmpty()) {
                                if (violation.getUserName() == null
                                        || !violation.getUserName().toLowerCase().contains(userName.toLowerCase())) {
                                    return false;
                                }
                            }

                            if (violationType != null && !violationType.trim().isEmpty()) {
                                if (!violationType.equals(violation.getViolationType())) {
                                    return false;
                                }
                            }

                            if (status != null && !status.trim().isEmpty()) {
                                if (!status.equals(violation.getStatus())) {
                                    return false;
                                }
                            }

                            return true;
                        })
                        .collect(Collectors.toList());

                return new org.springframework.data.domain.PageImpl<>(
                        filteredList,
                        pageable,
                        filteredList.size()
                );
            }

            return violations;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("获取维护人员违规记录失败：" + e.getMessage());
        }
    }

    /**
     * 获取违规记录统计数据
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
            System.err.println("获取违规记录统计数据失败：" + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("获取违规记录统计数据失败：" + e.getMessage());
        }
    }
}
