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
            return "该用户已被列入黑名单，无法预约";
        }

        if (!reservation.getEndTime().isAfter(reservation.getStartTime())) {
            return "结束时间必须晚于开始时间";
        }

        if (reservation.getStartTime().isBefore(LocalDateTime.now())) {
            return "不能预约过去的时间";
        }

        long durationHours = java.time.Duration.between(reservation.getStartTime(), reservation.getEndTime()).toHours();
        if (durationHours > 24) {
            return "单次预约时长不能超过24小时";
        }

        return validateReservationRules(reservation, facilityOpt.get());
    }

    @Transactional
    public Reservation createReservation(Reservation reservation) {
        Facility facility = facilityRepository.findByIdWithLock(reservation.getFacilityId())
                .orElseThrow(() -> new IllegalArgumentException("设施不存在或已被删除"));

        String ruleError = validateReservationRules(reservation, facility);
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
        return reservationRepository.save(reservation);
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
            throw new IllegalStateException("该时间段已被预约，请选择其他时间");
        }
    }

    public String validateCheckin(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            return "只有预约用户本人才能进行签到操作";
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return "只有已通过的预约才能签到";
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
            return "还未到签到时间，可提前15分钟签到";
        }

        if (now.isAfter(reservation.getEndTime())) {
            return "预约时间已结束，无法签到";
        }

        return null;
    }

    public String validateCheckout(Reservation reservation, Long userId) {
        if (!reservation.getUserId().equals(userId)) {
            return "只有预约用户本人才能进行签退操作";
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return "只有已通过的预约才能签退";
        }

        if (!"CHECKED_IN".equals(reservation.getCheckinStatus())) {
            return "请先签到后再签退";
        }

        return null;
    }

    public String validateReservationRules(Reservation reservation, Facility facility) {
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

        if (ruleConfig.getAllowSameDayBooking() != null && !ruleConfig.getAllowSameDayBooking()
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
                    .filter(r -> r.getStartTime().toLocalDate().equals(reservationDate))
                    .filter(r -> !("REJECTED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus())))
                    .count();

            if (dailyCount >= ruleConfig.getMaxBookingsPerDay()) {
                return "您今日预约次数已达上限（" + ruleConfig.getMaxBookingsPerDay() + "次）";
            }
        }

        if (ruleConfig.getMaxActiveBookings() != null) {
            List<Reservation> userActiveReservations = reservationRepository.findByUserIdAndStatusIn(
                    reservation.getUserId(),
                    Arrays.asList("PENDING", "APPROVED")
            );
            if (userActiveReservations.size() >= ruleConfig.getMaxActiveBookings()) {
                return "您的活跃预约数量已达上限（" + ruleConfig.getMaxActiveBookings() + "个）";
            }
        }

        return null;
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
}
