package com.facility.booking.service;

import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.RuleConfig;
import com.facility.booking.entity.User;
import com.facility.booking.repository.BlacklistRepository;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.RuleConfigRepository;
import com.facility.booking.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private static final List<String> CONFLICT_STATUSES = Arrays.asList("APPROVED", "PENDING", "COMPLETED");

    private final ReservationRepository reservationRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final BlacklistRepository blacklistRepository;
    private final RuleConfigRepository ruleConfigRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            BlacklistRepository blacklistRepository,
            RuleConfigRepository ruleConfigRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.blacklistRepository = blacklistRepository;
        this.ruleConfigRepository = ruleConfigRepository;
    }

    public String validateReservationCreation(Reservation reservation) {
        if (reservation.getFacilityId() == null) {
            return "设施ID不能为空";
        }
        if (reservation.getUserId() == null) {
            return "用户ID不能为空";
        }
        if (reservation.getStartTime() == null) {
            return "开始时间不能为空";
        }
        if (reservation.getEndTime() == null) {
            return "结束时间不能为空";
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(reservation.getFacilityId());
        if (facilityOpt.isEmpty()) {
            return "设施不存在";
        }

        Optional<User> userOpt = userRepository.findById(reservation.getUserId());
        if (userOpt.isEmpty()) {
            return "用户不存在";
        }

        if (blacklistRepository.findByUserIdAndStatus(reservation.getUserId(), "ACTIVE").isPresent()) {
            return "您当前已被限制预约，如有疑问请联系管理员";
        }

        if (!reservation.getEndTime().isAfter(reservation.getStartTime())) {
            return "结束时间必须晚于开始时间";
        }

        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            return "开始时间不能早于当前时间，请重新选择";
        }

        long durationHours = java.time.Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (durationHours > 24) {
            return "单次预约时长不能超过24小时，请调整预约时段";
        }

        return validateReservationRules(reservation, facilityOpt.get(), null);
    }

    @Transactional
    public Reservation createReservation(Reservation reservation) {
        Facility facility = facilityRepository.findByIdWithLock(reservation.getFacilityId())
                .orElseThrow(() -> new IllegalArgumentException("设施不存在或已被删除"));

        String ruleError = validateReservationRules(reservation, facility, null);
        if (ruleError != null) {
            throw new IllegalArgumentException(ruleError);
        }

        ensureNoConflicts(reservation.getFacilityId(), reservation.getStartTime(), reservation.getEndTime(), null);

        RuleConfig ruleConfig = getApplicableRuleConfig(facility);
        String initialStatus = (ruleConfig != null && Boolean.TRUE.equals(ruleConfig.getNeedApproval()))
                ? "PENDING"
                : "APPROVED";

        reservation.setStatus(initialStatus);
        reservation.setCheckinStatus("NOT_CHECKED");
        if ("APPROVED".equals(initialStatus) && isBlank(reservation.getVerificationCode())) {
            reservation.setVerificationCode(generateVerificationCode());
        }
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation updateReservation(Long reservationId, Reservation updates) {
        Reservation existingReservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        if (!Arrays.asList("PENDING", "APPROVED").contains(existingReservation.getStatus())) {
            throw new IllegalStateException("只有待审核和已通过的预约可以编辑");
        }

        if (!"NOT_CHECKED".equals(existingReservation.getCheckinStatus())) {
            throw new IllegalStateException("已进入签到流程的预约不支持编辑");
        }

        Facility facility = facilityRepository.findByIdWithLock(existingReservation.getFacilityId())
                .orElseThrow(() -> new IllegalArgumentException("设施不存在或已被删除"));

        LocalDateTime startTime = updates.getStartTime() != null ? updates.getStartTime() : existingReservation.getStartTime();
        LocalDateTime endTime = updates.getEndTime() != null ? updates.getEndTime() : existingReservation.getEndTime();
        String purpose = updates.getPurpose() != null ? updates.getPurpose().trim() : existingReservation.getPurpose();
        String adminRemark = updates.getAdminRemark() != null ? updates.getAdminRemark().trim() : existingReservation.getAdminRemark();

        if (isBlank(purpose)) {
            throw new IllegalArgumentException("预约用途不能为空");
        }

        existingReservation.setStartTime(startTime);
        existingReservation.setEndTime(endTime);
        existingReservation.setPurpose(purpose);
        existingReservation.setAdminRemark(isBlank(adminRemark) ? null : adminRemark);

        String validationError = validateReservationForUpdate(existingReservation, facility);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        ensureNoConflicts(
                existingReservation.getFacilityId(),
                existingReservation.getStartTime(),
                existingReservation.getEndTime(),
                existingReservation.getId()
        );

        if ("APPROVED".equals(existingReservation.getStatus()) && isBlank(existingReservation.getVerificationCode())) {
            existingReservation.setVerificationCode(generateVerificationCode());
        }

        return reservationRepository.save(existingReservation);
    }

    @Transactional
    public Reservation approveReservation(Long reservationId, String adminRemark) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        facilityRepository.findByIdWithLock(reservation.getFacilityId())
                .orElseThrow(() -> new IllegalArgumentException("设施不存在或已被删除"));

        ensureNoConflicts(reservation.getFacilityId(), reservation.getStartTime(), reservation.getEndTime(), reservation.getId());

        reservation.setStatus("APPROVED");
        reservation.setAdminRemark(adminRemark);
        if (isBlank(reservation.getVerificationCode())) {
            reservation.setVerificationCode(generateVerificationCode());
        }
        return reservationRepository.save(reservation);
    }

    public void ensureNoConflicts(Long facilityId, LocalDateTime startTime, LocalDateTime endTime, Long excludeReservationId) {
        List<Reservation> conflictingReservations = reservationRepository.findConflictingReservations(
                facilityId,
                startTime,
                endTime,
                CONFLICT_STATUSES
        );

        boolean hasConflict = conflictingReservations.stream()
                .anyMatch(existing -> excludeReservationId == null || !existing.getId().equals(excludeReservationId));

        if (hasConflict) {
            throw new IllegalStateException("当前时段已被其他预约占用，请重新选择时间");
        }
    }

    public String validateCheckin(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            return "仅限预约人本人执行签到";
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return "只有审核通过的预约才能签到";
        }

        if (!"NOT_CHECKED".equals(reservation.getCheckinStatus())) {
            if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
                return "该预约已签到，请勿重复操作";
            } else if ("CHECKED_OUT".equals(reservation.getCheckinStatus())) {
                return "该预约已完成签退，无法再次签到";
            } else {
                return "该预约签到状态异常";
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(reservation.getStartTime().minusMinutes(15))) {
            return "当前还未到可签到时间，最多可提前15分钟签到";
        }

        if (now.isAfter(reservation.getEndTime())) {
            return "当前预约时段已结束，无法签到";
        }

        return null;
    }

    public String validateCheckout(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            return "仅限预约人本人执行签退";
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return "只有审核通过的预约才能签退";
        }

        if (!"CHECKED_IN".equals(reservation.getCheckinStatus())) {
            return "请先完成签到，再进行签退";
        }

        return null;
    }

    public String validateReservationRules(Reservation reservation, Facility facility) {
        return validateReservationRules(reservation, facility, null);
    }

    public String validateReservationRules(Reservation reservation, Facility facility, Long excludeReservationId) {
        RuleConfig ruleConfig = getApplicableRuleConfig(facility);
        if (ruleConfig == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = reservation.getStartTime();
        LocalDateTime endTime = reservation.getEndTime();

        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (ruleConfig.getMinDurationMinutes() != null && durationMinutes < ruleConfig.getMinDurationMinutes()) {
            return "预约时长不能少于" + ruleConfig.getMinDurationMinutes() + "分钟";
        }
        if (ruleConfig.getMaxDurationMinutes() != null && durationMinutes > ruleConfig.getMaxDurationMinutes()) {
            return "预约时长不能超过" + ruleConfig.getMaxDurationMinutes() + "分钟";
        }

        if (ruleConfig.getAdvanceDaysMax() != null) {
            LocalDateTime maxAdvanceTime = now.plusDays(ruleConfig.getAdvanceDaysMax());
            if (startTime.isAfter(maxAdvanceTime)) {
                return "只能提前" + ruleConfig.getAdvanceDaysMax() + "天预约";
            }
        }

        if (ruleConfig.getAdvanceCutoffMinutes() != null) {
            LocalDateTime minAdvanceTime = now.plusMinutes(ruleConfig.getAdvanceCutoffMinutes());
            if (startTime.isBefore(minAdvanceTime)) {
                return "需要提前" + ruleConfig.getAdvanceCutoffMinutes() + "分钟预约";
            }
        }

        if (ruleConfig.getAllowSameDayBooking() != null
                && !ruleConfig.getAllowSameDayBooking()
                && startTime.toLocalDate().equals(now.toLocalDate())) {
            return "不允许当日预约";
        }

        if (ruleConfig.getOpenTime() != null && ruleConfig.getCloseTime() != null) {
            LocalTime startLocalTime = startTime.toLocalTime();
            LocalTime endLocalTime = endTime.toLocalTime();
            if (startLocalTime.isBefore(ruleConfig.getOpenTime()) || endLocalTime.isAfter(ruleConfig.getCloseTime())) {
                return "预约时间必须在" + ruleConfig.getOpenTime() + "至" + ruleConfig.getCloseTime() + "之间";
            }
        }

        if (ruleConfig.getMaxBookingsPerDay() != null) {
            LocalDate reservationDate = startTime.toLocalDate();
            long dailyCount = reservationRepository.findByUserId(reservation.getUserId()).stream()
                    .filter(r -> excludeReservationId == null || !excludeReservationId.equals(r.getId()))
                    .filter(r -> r.getStartTime().toLocalDate().equals(reservationDate))
                    .filter(r -> !("REJECTED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus())))
                    .count();

            if (dailyCount >= ruleConfig.getMaxBookingsPerDay()) {
                return "当前类别设施当日预约次数已达上限（" + ruleConfig.getMaxBookingsPerDay() + "次），无法进行预约";
            }
        }

        if (ruleConfig.getMaxActiveBookings() != null) {
            List<Reservation> userActiveReservations = reservationRepository.findByUserIdAndStatusIn(
                    reservation.getUserId(),
                    Arrays.asList("PENDING", "APPROVED")
            );
            long activeCount = userActiveReservations.stream()
                    .filter(r -> excludeReservationId == null || !excludeReservationId.equals(r.getId()))
                    .count();
            if (activeCount >= ruleConfig.getMaxActiveBookings()) {
                return "当前类别设施预约数已达上限（" + ruleConfig.getMaxActiveBookings() + "个），无法进行预约";
            }
        }

        return null;
    }

    private String validateReservationForUpdate(Reservation reservation, Facility facility) {
        if (reservation.getStartTime() == null) {
            return "开始时间不能为空";
        }
        if (reservation.getEndTime() == null) {
            return "结束时间不能为空";
        }
        if (!reservation.getEndTime().isAfter(reservation.getStartTime())) {
            return "结束时间必须晚于开始时间";
        }
        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            return "只能编辑当前或未来的预约时段";
        }

        long durationHours = java.time.Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (durationHours > 24) {
            return "单次预约时长不能超过24小时，请调整预约时段";
        }

        return validateReservationRules(reservation, facility, reservation.getId());
    }

    private RuleConfig getApplicableRuleConfig(Facility facility) {
        if (facility.getCategory() != null) {
            Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryName(facility.getCategory());
            if (categoryRuleOpt.isPresent()) {
                return categoryRuleOpt.get();
            }
        }

        return ruleConfigRepository.findByCategoryIdIsNull().orElse(null);
    }

    private String generateVerificationCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
