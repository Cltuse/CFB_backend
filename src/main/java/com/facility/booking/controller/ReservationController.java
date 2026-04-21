package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.RuleConfig;
import com.facility.booking.entity.User;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.RuleConfigRepository;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.ReservationService;
import com.facility.booking.service.ViolationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 预约管理控制器。
 * 负责预约创建、审核、签到签退、核销、统计等功能。
 */
@RestController
@RequestMapping("/api/reservation")
public class ReservationController {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Autowired
    private ViolationRecordService violationRecordService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * 获取所有预约记录。
     */
    @GetMapping("/list")
    public Result<List<Reservation>> list() {
        List<Reservation> reservations = filterReservationsForCurrentMaintainer(reservationRepository.findAll());
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 根据用户 ID 获取预约记录。
     */
    @GetMapping("/user/{userId}")
    public Result<List<Reservation>> getByUserId(@PathVariable Long userId) {
        List<Reservation> reservations = reservationRepository.findByUserId(userId);
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 获取待审核预约。
     */
    @GetMapping("/pending")
    public Result<List<Reservation>> getPending() {
        List<Reservation> reservations = filterReservationsForCurrentMaintainer(reservationRepository.findByStatus("PENDING"));
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 根据 ID 获取预约详情。
     */
    @GetMapping("/{id}")
    public Result<Reservation> getById(@PathVariable Long id) {
        Optional<Reservation> reservation = reservationRepository.findById(id);
        if (reservation.isPresent()) {
            Reservation res = reservation.get();
            if (!canCurrentMaintainerManageFacility(res.getFacilityId())) {
                return Result.error(403, "无权查看该预约记录");
            }
            enrichReservation(res);
            return Result.success(res);
        }
        return Result.error("预约不存在");
    }

    /**
     * 创建预约。
     */
    @PostMapping
    @OperationLog(operationType = "CREATE_BOOKING", detail = "创建预约")
    public Result<Reservation> create(@RequestBody Reservation reservation) {
        try {
            String validationError = reservationService.validateReservationCreation(reservation);
            if (validationError != null) {
                return Result.error(validationError);
            }

            Reservation savedReservation = reservationService.createReservation(reservation);
            enrichReservation(savedReservation);

            String message = "预约提交成功";
            if ("PENDING".equals(savedReservation.getStatus())) {
                message = "预约申请已提交，待设施管理员审核";
            }

            return Result.success(message, savedReservation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("预约提交失败: " + e.getMessage());
        }
    }

    /**
     * 更新预约。
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_BOOKING", detail = "更新预约")
    public Result<Reservation> update(@PathVariable Long id, @RequestBody Reservation reservation) {
        if (!reservationRepository.existsById(id)) {
            return Result.error("预约不存在");
        }
        reservation.setId(id);
        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("更新成功", savedReservation);
    }

    /**
     * 获取预约核销码。
     */
    @GetMapping("/{id}/qrcode")
    public Result<String> getQRCode(@PathVariable Long id) {
        try {
            Optional<Reservation> reservationOpt = reservationRepository.findById(id);
            if (!reservationOpt.isPresent()) {
                return Result.error("预约不存在");
            }

            Reservation reservation = reservationOpt.get();
            if (!canCurrentMaintainerManageFacility(reservation.getFacilityId())) {
                return Result.error(403, "无权核销该设施预约");
            }

            if (!"APPROVED".equals(reservation.getStatus())) {
                return Result.error("只有审核通过的预约才能获取核销码");
            }

            String verificationCode = reservation.getVerificationCode();
            if (verificationCode == null || verificationCode.isEmpty()) {
                verificationCode = generateVerificationCode(reservation.getId());
                reservation.setVerificationCode(verificationCode);
                reservationRepository.save(reservation);
            }

            return Result.success("获取核销码成功", verificationCode);
        } catch (Exception e) {
            return Result.error("获取核销码失败: " + e.getMessage());
        }
    }

    /**
     * 设施管理员扫码核销。
     */
    @PostMapping("/verify")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "扫码核销预约")
    public Result<Reservation> verifyByCode(@RequestParam String verificationCode) {
        try {
            Long operatorId = currentUserService.getCurrentUserId();
            if (operatorId == null) {
                return Result.error(401, "未登录或登录已失效");
            }
            if (!currentUserService.hasRole("MAINTAINER")) {
                return Result.error(403, "仅设施管理员可以执行预约核销");
            }

            Optional<Reservation> reservationOpt = reservationRepository.findByVerificationCode(verificationCode);
            if (!reservationOpt.isPresent()) {
                return Result.error("核销码无效");
            }

            Reservation reservation = reservationOpt.get();
            if (!"APPROVED".equals(reservation.getStatus())) {
                return Result.error("该预约尚未审核通过，无法核验");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime allowCheckinTime = reservation.getStartTime().minusMinutes(15);
            LocalDateTime allowCheckoutTime = reservation.getEndTime().plusMinutes(15);

            if (now.isBefore(allowCheckinTime) || now.isAfter(allowCheckoutTime)) {
                return Result.error("不在预约时间范围内，无法核验");
            }

            if ("NOT_CHECKED".equals(reservation.getCheckinStatus())) {
                reservation.setCheckinStatus("CHECKED_IN");
                reservation.setCheckinTime(now);
                reservation.setVerifiedBy(operatorId);
                reservation.setVerifiedTime(now);

                Reservation savedReservation = reservationRepository.save(reservation);
                enrichReservation(savedReservation);

                return Result.success("签到核验成功", savedReservation);
            } else if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
                reservation.setCheckinStatus("CHECKED_OUT");
                reservation.setStatus("COMPLETED");
                reservation.setCheckoutTime(now);
                reservation.setVerifiedBy(operatorId);
                reservation.setVerifiedTime(now);

                Reservation savedReservation = reservationRepository.save(reservation);
                enrichReservation(savedReservation);

                return Result.success("签退核验成功", savedReservation);
            } else {
                return Result.error("该预约已完成签到签退流程");
            }
        } catch (Exception e) {
            return Result.error("核验失败: " + e.getMessage());
        }
    }

    /**
     * 审核通过预约。
     */
    @PutMapping("/{id}/approve")
    @OperationLog(operationType = "APPROVE_BOOKING", detail = "审核通过预约")
    public Result<Reservation> approve(@PathVariable Long id, @RequestBody Reservation reservation) {
        Long operatorId = currentUserService.getCurrentUserId();
        if (operatorId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        if (!currentUserService.hasRole("MAINTAINER")) {
            return Result.error(403, "仅设施管理员可以审核预约");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();
        if (!canCurrentMaintainerManageFacility(existingReservation.getFacilityId())) {
            return Result.error(403, "无权审核该设施预约");
        }
        if (!isValidStatusTransition(existingReservation.getStatus(), "APPROVED")) {
            return Result.error("当前预约状态不允许审核通过");
        }

        try {
            Reservation savedReservation = reservationService.approveReservation(id, reservation.getAdminRemark());
            enrichReservation(savedReservation);
            return Result.success("审核通过", savedReservation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 驳回预约。
     */
    @PutMapping("/{id}/reject")
    @OperationLog(operationType = "REJECT_BOOKING", detail = "驳回预约")
    public Result<Reservation> reject(@PathVariable Long id, @RequestBody Reservation reservation) {
        Long operatorId = currentUserService.getCurrentUserId();
        if (operatorId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        if (!currentUserService.hasRole("MAINTAINER")) {
            return Result.error(403, "仅设施管理员可以审核预约");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();
        if (!canCurrentMaintainerManageFacility(existingReservation.getFacilityId())) {
            return Result.error(403, "无权审核该设施预约");
        }
        if (!isValidStatusTransition(existingReservation.getStatus(), "REJECTED")) {
            return Result.error("当前预约状态不允许驳回");
        }

        existingReservation.setStatus("REJECTED");
        existingReservation.setAdminRemark(reservation.getAdminRemark());

        Reservation savedReservation = reservationRepository.save(existingReservation);
        enrichReservation(savedReservation);
        return Result.success("已驳回", savedReservation);
    }

    /**
     * 取消预约。
     */
    @PutMapping("/{id}/cancel")
    @OperationLog(operationType = "CANCEL_BOOKING", detail = "取消预约")
    public Result<Reservation> cancel(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();
        if (!Objects.equals(existingReservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "无权执行当前操作");
        }

        Result<String> cancelValidationResult = validateCancelRules(existingReservation);
        if (!cancelValidationResult.isSuccess()) {
            return Result.error(cancelValidationResult.getMessage());
        }

        existingReservation.setStatus("CANCELLED");

        Reservation savedReservation = reservationRepository.save(existingReservation);
        enrichReservation(savedReservation);
        return Result.success("已取消", savedReservation);
    }

    /**
     * 搜索预约记录。
     */
    @GetMapping("/search")
    public Result<List<Reservation>> search(@RequestParam(required = false) String keyword) {
        List<Reservation> reservations;

        if (keyword == null || keyword.trim().isEmpty()) {
            reservations = reservationRepository.findAll();
        } else {
            reservations = reservationRepository.findByKeyword(keyword.trim());
        }

        reservations = filterReservationsForCurrentMaintainer(reservations);
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 完成预约。
     */
    @PutMapping("/{id}/complete")
    @OperationLog(operationType = "COMPLETE_BOOKING", detail = "完成预约")
    public Result<Reservation> complete(@PathVariable Long id) {
        Long operatorId = currentUserService.getCurrentUserId();
        if (operatorId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();

        if (!"APPROVED".equals(existingReservation.getStatus())) {
            return Result.error("只有审核通过的预约才能完成");
        }

        existingReservation.setStatus("COMPLETED");

        Reservation savedReservation = reservationRepository.save(existingReservation);
        enrichReservation(savedReservation);
        return Result.success("预约已完成", savedReservation);
    }

    /**
     * 删除预约。
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_BOOKING", detail = "删除预约")
    public Result<Void> delete(@PathVariable Long id) {
        if (!reservationRepository.existsById(id)) {
            return Result.error("预约不存在");
        }
        reservationRepository.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 校验预约是否满足取消规则。
     */
    private Result<String> validateCancelRules(Reservation reservation) {
        if (!("PENDING".equals(reservation.getStatus()) || "APPROVED".equals(reservation.getStatus()))) {
            return Result.error("当前状态不允许取消预约");
        }

        if (!"NOT_CHECKED".equals(reservation.getCheckinStatus())) {
            if ("MISSED".equals(reservation.getCheckinStatus())) {
                return Result.error("爽约的预约不能取消");
            } else if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
                return Result.error("已签到的预约不能取消");
            } else if ("CHECKED_OUT".equals(reservation.getCheckinStatus())) {
                return Result.error("已签退的预约不能取消");
            } else {
                return Result.error("当前签到状态不允许取消预约");
            }
        }

        Optional<Facility> facilityOpt = facilityRepository.findById(reservation.getFacilityId());
        if (!facilityOpt.isPresent()) {
            return Result.error("设施信息不存在");
        }

        RuleConfig ruleConfig = getApplicableRuleConfig(facilityOpt.get());
        if (ruleConfig == null || ruleConfig.getCancelDeadlineMinutes() == null) {
            return Result.success("未设置取消截止时间");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cancelDeadline = reservation.getStartTime().minusMinutes(ruleConfig.getCancelDeadlineMinutes());

        if (now.isAfter(cancelDeadline)) {
            return Result.error("预约开始前 " + ruleConfig.getCancelDeadlineMinutes() + " 分钟内不允许取消");
        }

        return Result.success("可以取消");
    }

    /**
     * 用户签到。
     */
    @PutMapping("/{id}/checkin")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "用户签到")
    public Result<Reservation> checkin(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId)) {
            return Result.error(403, "无权执行当前操作");
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return Result.error("只有审核通过的预约才能签到");
        }

        if (!"NOT_CHECKED".equals(reservation.getCheckinStatus())) {
            if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
                return Result.error("该预约已签到，请勿重复操作");
            } else if ("CHECKED_OUT".equals(reservation.getCheckinStatus())) {
                return Result.error("该预约已完成签退，无法再次签到");
            } else {
                return Result.error("该预约签到状态异常");
            }
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(reservation.getStartTime().minusMinutes(15))) {
            return Result.error("当前还未到可签到时间，最多可提前 15 分钟签到");
        }

        if (now.isAfter(reservation.getEndTime())) {
            return Result.error("当前预约时段已结束，无法签到");
        }

        reservation.setCheckinStatus("CHECKED_IN");
        reservation.setCheckinTime(now);

        if (reservation.getVerificationCode() == null || reservation.getVerificationCode().isBlank()) {
            String verificationCode = generateVerificationCode(id);
            reservation.setVerificationCode(verificationCode);
        }

        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("签到成功", savedReservation);
    }

    /**
     * 用户签退。
     */
    @PutMapping("/{id}/checkout")
    @OperationLog(operationType = "VERIFY_CHECKOUT", detail = "用户签退")
    public Result<Reservation> checkout(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId)) {
            return Result.error(403, "无权执行当前操作");
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return Result.error("只有审核通过的预约才能签退");
        }

        if (!"CHECKED_IN".equals(reservation.getCheckinStatus())) {
            return Result.error("请先完成签到，再进行签退");
        }

        reservation.setCheckinStatus("CHECKED_OUT");
        reservation.setCheckoutTime(LocalDateTime.now());
        reservation.setStatus("COMPLETED");

        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("签退成功，预约已完成", savedReservation);
    }

    /**
     * 设施管理员核销指定预约。
     */
    @PutMapping("/{id}/verify")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "设施管理员核销预约")
    public Result<Reservation> verify(@PathVariable Long id, @RequestParam String verificationCode) {
        Long operatorId = currentUserService.getCurrentUserId();
        if (operatorId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        if (!currentUserService.hasRole("MAINTAINER")) {
            return Result.error(403, "仅设施管理员可以执行预约核销");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!canCurrentMaintainerManageFacility(reservation.getFacilityId())) {
            return Result.error(403, "无权核销该设施预约");
        }
        if (!verificationCode.equals(reservation.getVerificationCode())) {
            return Result.error("核销码错误");
        }

        if (!"APPROVED".equals(reservation.getStatus())) {
            return Result.error("只有审核通过的预约才能核销");
        }

        LocalDateTime now = LocalDateTime.now();
        reservation.setVerifiedBy(operatorId);
        reservation.setVerifiedTime(now);

        if ("NOT_CHECKED".equals(reservation.getCheckinStatus())) {
            reservation.setCheckinStatus("CHECKED_IN");
            reservation.setCheckinTime(now);
        } else if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
            reservation.setCheckinStatus("CHECKED_OUT");
            reservation.setCheckoutTime(now);
            reservation.setStatus("COMPLETED");
        } else {
            return Result.error("该预约无法继续核销");
        }

        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("核销成功", savedReservation);
    }

    /**
     * 检查设施在指定时间段是否可预约。
     */
    @GetMapping("/availability")
    public Result<Map<String, Object>> checkAvailability(@RequestParam Long facilityId,
                                                         @RequestParam String startTime,
                                                         @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime end = LocalDateTime.parse(endTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            Optional<Facility> facilityOpt = facilityRepository.findById(facilityId);
            if (!facilityOpt.isPresent()) {
                return Result.error("设施不存在");
            }

            if (end.isBefore(start)) {
                return Result.error("结束时间不能早于开始时间");
            }

            if (start.isBefore(LocalDateTime.now())) {
                return Result.error("开始时间不能早于当前时间，请重新选择");
            }

            List<String> validStatuses = Arrays.asList("APPROVED", "PENDING", "COMPLETED");
            List<Reservation> conflictingReservations = reservationRepository.findConflictingReservations(
                    facilityId, start, end, validStatuses
            );

            Map<String, Object> result = new HashMap<>();
            result.put("available", conflictingReservations.isEmpty());
            result.put("message", conflictingReservations.isEmpty() ? "当前时段可以预约" : "当前时段已被其他预约占用");

            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取预约核销码。
     */
    @GetMapping("/{id}/verification-code")
    public Result<Map<String, String>> getVerificationCode(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已失效");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "无权执行当前操作");
        }
        if (reservation.getVerificationCode() == null) {
            return Result.error("该预约暂无核销码");
        }

        Map<String, String> result = new HashMap<>();
        result.put("verificationCode", reservation.getVerificationCode());
        return Result.success(result);
    }

    /**
     * 生成核销码。
     */
    private String generateVerificationCode(Long reservationId) {
        try {
            String input = reservationId + System.currentTimeMillis() + "campus_facility";
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 8).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            return String.format("%08d", (int) (Math.random() * 100000000));
        }
    }

    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(@RequestParam String range) {
        LocalDateTime startTime = getStartTimeByRange(range);
        List<Reservation> reservations = reservationRepository.findByCreatedAtAfter(startTime);

        Map<String, Object> result = new HashMap<>();
        result.put("total", reservations.size());
        result.put("reservations", reservations);

        return Result.success(result);
    }

    @GetMapping("/stats/category")
    public Result<Map<String, Object>> getCategoryStats(@RequestParam(required = false) String range) {
        LocalDateTime startTime = range != null ? getStartTimeByRange(range) : LocalDateTime.of(2000, 1, 1, 0, 0);
        List<ReservationRepository.CategoryCountView> categoryStats = reservationRepository.countCategoryStatsAfter(startTime);

        List<Map<String, Object>> pieData = new ArrayList<>();
        String[] colors = {"#409eff", "#67c23a", "#e6a23c", "#f56c6c", "#909399", "#c71585", "#00ced1", "#ff6347"};
        int colorIndex = 0;
        long total = 0;
        for (ReservationRepository.CategoryCountView entry : categoryStats) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getCategory());
            item.put("value", entry.getTotal());
            item.put("itemStyle", Map.of("color", colors[colorIndex % colors.length]));
            pieData.add(item);
            colorIndex++;
            total += entry.getTotal();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("categoryData", pieData);
        result.put("total", total);

        return Result.success(result);
    }

    private LocalDateTime getStartTimeByRange(String range) {
        LocalDateTime now = LocalDateTime.now();
        return switch (range) {
            case "1d" -> now.minusDays(1);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            case "180d" -> now.minusDays(180);
            case "365d" -> now.minusDays(365);
            default -> now.minusDays(7);
        };
    }

    /**
     * 检查两个时间段是否冲突。
     */
    private boolean isTimeConflict(LocalDateTime start1, LocalDateTime end1,
                                   LocalDateTime start2, LocalDateTime end2) {
        return !(end1.isBefore(start2) || start1.isAfter(end2));
    }

    /**
     * 校验预约状态流转是否合法。
     */
    private boolean isValidStatusTransition(String currentStatus, String targetStatus) {
        Map<String, Set<String>> allowedTransitions = Map.of(
                "PENDING", Set.of("APPROVED", "REJECTED", "CANCELLED"),
                "APPROVED", Set.of("COMPLETED", "CANCELLED"),
                "REJECTED", Set.of(),
                "COMPLETED", Set.of(),
                "CANCELLED", Set.of()
        );

        Set<String> allowedTargets = allowedTransitions.getOrDefault(currentStatus, Set.of());
        return allowedTargets.contains(targetStatus);
    }

    /**
     * 批量补充预约记录的展示字段。
     */
    private void enrichReservations(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return;
        }

        Map<Long, Facility> facilitiesById = facilityRepository.findAllById(
                        reservations.stream()
                                .map(Reservation::getFacilityId)
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Facility::getId, facility -> facility));

        Map<Long, User> usersById = userRepository.findAllById(
                        reservations.stream()
                                .flatMap(reservation -> java.util.stream.Stream.of(reservation.getUserId(), reservation.getVerifiedBy()))
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet()))
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));

        for (Reservation reservation : reservations) {
            Facility facility = facilitiesById.get(reservation.getFacilityId());
            if (facility != null) {
                reservation.setFacilityName(facility.getName());
            }

            User user = usersById.get(reservation.getUserId());
            if (user != null) {
                reservation.setUserName(getDisplayName(user));
                reservation.setUserRole(user.getRole());
            } else {
                reservation.setUserName("未知用户");
                reservation.setUserRole(null);
            }

            User verifiedBy = usersById.get(reservation.getVerifiedBy());
            if (verifiedBy != null) {
                reservation.setVerifiedByName(getDisplayName(verifiedBy));
            }
        }
    }

    /**
     * 补充单条预约记录的展示字段。
     */
    private void enrichReservation(Reservation reservation) {
        Optional<Facility> facility = facilityRepository.findById(reservation.getFacilityId());
        facility.ifPresent(e -> reservation.setFacilityName(e.getName()));

        Optional<User> userOpt = userRepository.findById(reservation.getUserId());
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            String displayName = u.getRealName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = u.getUsername();
            }
            reservation.setUserName(displayName);
            reservation.setUserRole(u.getRole());
        } else {
            reservation.setUserName("未知用户");
            reservation.setUserRole(null);
        }

        if (reservation.getVerifiedBy() != null) {
            Optional<User> verifiedByOpt = userRepository.findById(reservation.getVerifiedBy());
            if (verifiedByOpt.isPresent()) {
                User verifiedByUser = verifiedByOpt.get();
                String verifiedByName = verifiedByUser.getRealName();
                if (verifiedByName == null || verifiedByName.trim().isEmpty()) {
                    verifiedByName = verifiedByUser.getUsername();
                }
                reservation.setVerifiedByName(verifiedByName);
            }
        }
    }

    private String getDisplayName(User user) {
        String displayName = user.getRealName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = user.getUsername();
        }
        return displayName;
    }

    private List<Reservation> filterReservationsForCurrentMaintainer(List<Reservation> reservations) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return reservations;
        }

        Set<Long> facilityIds = getCurrentMaintainerFacilityIds();
        return reservations.stream()
                .filter(reservation -> reservation.getFacilityId() != null && facilityIds.contains(reservation.getFacilityId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Set<Long> getCurrentMaintainerFacilityIds() {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Set.of();
        }

        return facilityRepository.findByMaintainerId(currentUserId).stream()
                .map(Facility::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean canCurrentMaintainerManageFacility(Long facilityId) {
        if (!currentUserService.hasRole("MAINTAINER")) {
            return true;
        }
        return facilityId != null && getCurrentMaintainerFacilityIds().contains(facilityId);
    }

    /**
     * 系统启动时执行一次爽约检查。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        System.out.println("系统启动，开始执行爽约检查...");
        autoMarkMissedReservations();
        System.out.println("系统启动爽约检查完成");
    }

    /**
     * 定时任务：自动标记爽约预约。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void autoMarkMissedReservations() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("开始执行爽约检查，当前时间: " + now);

        List<Reservation> missedReservations = reservationRepository.findByStatusAndCheckinStatus("APPROVED", "NOT_CHECKED");
        System.out.println("找到 " + missedReservations.size() + " 条待检查的预约");

        int missedCount = 0;
        int facilityReleasedCount = 0;

        for (Reservation reservation : missedReservations) {
            boolean isMissed = false;
            String missedReason = "";

            if (now.isAfter(reservation.getEndTime())) {
                isMissed = true;
                missedReason = "预约结束时间已过，用户未签到";
            } else if (now.isAfter(reservation.getStartTime().plusMinutes(15))) {
                isMissed = true;
                missedReason = "预约开始后超过 15 分钟仍未签到";
            }

            if (isMissed) {
                reservation.setCheckinStatus("MISSED");
                reservation.setStatus("COMPLETED");

                if (reservation.getAdminRemark() == null || reservation.getAdminRemark().isEmpty()) {
                    reservation.setAdminRemark("系统自动标记爽约: " + missedReason);
                } else {
                    reservation.setAdminRemark(reservation.getAdminRemark() + " | 系统自动标记爽约: " + missedReason);
                }

                reservationRepository.save(reservation);
                missedCount++;

                try {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("NO_SHOW");
                    violationRecord.setDescription(
                            "爽约记录: " + missedReason + "。预约时间: " + reservation.getStartTime() + " 至 " + reservation.getEndTime()
                    );
                    violationRecord.setPenaltyPoints(5);
                    violationRecord.setReportedBy(76L);
                    violationRecord.setReportedTime(LocalDateTime.now());

                    violationRecordService.recordViolation(violationRecord);
                    System.out.println("已自动创建违规记录: 预约ID=" + reservation.getId() + ", 用户ID=" + reservation.getUserId());
                } catch (Exception e) {
                    System.err.println("创建违规记录失败: " + e.getMessage());
                    e.printStackTrace();
                }

                System.out.println(
                        "标记爽约预约: 预约ID=" + reservation.getId()
                                + ", 用户ID=" + reservation.getUserId()
                                + ", 设施ID=" + reservation.getFacilityId()
                                + ", 预约时间=" + reservation.getStartTime() + "-" + reservation.getEndTime()
                                + ", 原因=" + missedReason
                );
            }
        }

        if (missedCount > 0) {
            System.out.println("自动标记爽约完成，共标记 " + missedCount + " 条记录，释放 " + facilityReleasedCount + " 个设施");
        }
    }

    /**
     * 校验预约是否符合规则配置。
     */
    private Result<String> validateReservationRules(Reservation reservation, Facility facility) {
        RuleConfig ruleConfig = getApplicableRuleConfig(facility);
        if (ruleConfig == null) {
            return Result.success("没有额外规则限制");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = reservation.getStartTime();
        LocalDateTime endTime = reservation.getEndTime();

        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (ruleConfig.getMinDurationMinutes() != null && durationMinutes < ruleConfig.getMinDurationMinutes()) {
            return Result.error("预约时长不能少于 " + ruleConfig.getMinDurationMinutes() + " 分钟");
        }
        if (ruleConfig.getMaxDurationMinutes() != null && durationMinutes > ruleConfig.getMaxDurationMinutes()) {
            return Result.error("预约时长不能超过 " + ruleConfig.getMaxDurationMinutes() + " 分钟");
        }

        if (ruleConfig.getAdvanceDaysMax() != null) {
            LocalDateTime maxAdvanceTime = now.plusDays(ruleConfig.getAdvanceDaysMax());
            if (startTime.isAfter(maxAdvanceTime)) {
                return Result.error("只能提前 " + ruleConfig.getAdvanceDaysMax() + " 天预约");
            }
        }

        if (ruleConfig.getAdvanceCutoffMinutes() != null) {
            LocalDateTime minAdvanceTime = now.plusMinutes(ruleConfig.getAdvanceCutoffMinutes());
            if (startTime.isBefore(minAdvanceTime)) {
                return Result.error("需要提前 " + ruleConfig.getAdvanceCutoffMinutes() + " 分钟预约");
            }
        }

        if (ruleConfig.getAllowSameDayBooking() != null && !ruleConfig.getAllowSameDayBooking()) {
            if (startTime.toLocalDate().equals(now.toLocalDate())) {
                return Result.error("不允许当日预约");
            }
        }

        if (ruleConfig.getOpenTime() != null && ruleConfig.getCloseTime() != null) {
            LocalTime startLocalTime = startTime.toLocalTime();
            LocalTime endLocalTime = endTime.toLocalTime();

            if (startLocalTime.isBefore(ruleConfig.getOpenTime()) || endLocalTime.isAfter(ruleConfig.getCloseTime())) {
                return Result.error("预约时间必须在 " + ruleConfig.getOpenTime() + " 至 " + ruleConfig.getCloseTime() + " 之间");
            }
        }

        if (ruleConfig.getMaxBookingsPerDay() != null) {
            LocalDate reservationDate = startTime.toLocalDate();
            List<Reservation> userDailyReservations = reservationRepository.findByUserId(reservation.getUserId());
            long dailyCount = userDailyReservations.stream()
                    .filter(r -> r.getStartTime().toLocalDate().equals(reservationDate))
                    .filter(r -> !("REJECTED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus())))
                    .count();

            if (dailyCount >= ruleConfig.getMaxBookingsPerDay()) {
                return Result.error("当日预约次数已达上限（" + ruleConfig.getMaxBookingsPerDay() + " 次）");
            }
        }

        if (ruleConfig.getMaxActiveBookings() != null) {
            List<Reservation> userActiveReservations = reservationRepository.findByUserIdAndStatusIn(
                    reservation.getUserId(),
                    Arrays.asList("PENDING", "APPROVED")
            );

            if (userActiveReservations.size() >= ruleConfig.getMaxActiveBookings()) {
                return Result.error("当前有效预约数量已达上限（" + ruleConfig.getMaxActiveBookings() + " 个）");
            }
        }

        return Result.success("规则校验通过");
    }

    /**
     * 获取适用的预约规则。
     */
    private RuleConfig getApplicableRuleConfig(Facility facility) {
        if (facility.getCategory() != null) {
            Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryName(facility.getCategory());
            if (categoryRuleOpt.isPresent()) {
                return categoryRuleOpt.get();
            }
        }

        Optional<RuleConfig> defaultRuleOpt = ruleConfigRepository.findByCategoryIdIsNull();
        return defaultRuleOpt.orElse(null);
    }
}
