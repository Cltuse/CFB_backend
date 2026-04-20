package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.User;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.repository.FacilityRepository;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.repository.RuleConfigRepository;
import com.facility.booking.entity.RuleConfig;
import com.facility.booking.repository.UserRepository;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.ReservationService;
import com.facility.booking.service.ViolationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 设备预约控制器
 * 提供设备预约的增删改查、状态管理等功能
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
     * 获取所有预约记录
     * @return 预约记录列表
     */
    @GetMapping("/list")
    public Result<List<Reservation>> list() {
        List<Reservation> reservations = reservationRepository.findAll();
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 根据用户ID获取预约记录
     * @param userId 用户ID
     * @return 该用户的预约记录列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<Reservation>> getByUserId(@PathVariable Long userId) {
        List<Reservation> reservations = reservationRepository.findByUserId(userId);
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 获取待审核的预约记录
     * @return 待审核的预约记录列表
     */
    @GetMapping("/pending")
    public Result<List<Reservation>> getPending() {
        List<Reservation> reservations = reservationRepository.findByStatus("PENDING");
        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 根据ID获取预约记录详情
     * @param id 预约记录ID
     * @return 预约记录详情
     */
    @GetMapping("/{id}")
    public Result<Reservation> getById(@PathVariable Long id) {
        Optional<Reservation> reservation = reservationRepository.findById(id);
        if (reservation.isPresent()) {
            Reservation res = reservation.get();
            enrichReservation(res);
            return Result.success(res);
        }
        return Result.error("预约不存在");
    }

    /**
     * 创建预约记录
     * @param reservation 预约记录信息
     * @return 创建的预约记录信息
     */
    @PostMapping
    @OperationLog(operationType = "CREATE_BOOKING", detail = "Create reservation")
    public Result<Reservation> create(@RequestBody Reservation reservation) {
        try {
            String validationError = reservationService.validateReservationCreation(reservation);
            if (validationError != null) {
                return Result.error(validationError);
            }

            Reservation savedReservation = reservationService.createReservation(reservation);
            enrichReservation(savedReservation);

            String message = "Reservation created successfully";
            if ("PENDING".equals(savedReservation.getStatus())) {
                message += ", pending admin approval";
            }

            return Result.success(message, savedReservation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("Reservation creation failed: " + e.getMessage());
        }
    }

    /**
     * 更新预约记录
     * @param id 预约记录ID
     * @param reservation 更新的预约记录信息
     * @return 更新后的预约记录信息
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_BOOKING", detail = "Update reservation")
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
     * 获取预约二维码信息（核销码）
     */
    @GetMapping("/{id}/qrcode")
    public Result<String> getQRCode(@PathVariable Long id) {
        try {
            Optional<Reservation> reservationOpt = reservationRepository.findById(id);
            if (!reservationOpt.isPresent()) {
                return Result.error("预约不存在");
            }
            
            Reservation reservation = reservationOpt.get();
            
            // 检查预约状态
            if (!"APPROVED".equals(reservation.getStatus())) {
                return Result.error("只有已批准的预约才能获取二维码");
            }
            
            // 生成或获取核销码
            String verificationCode = reservation.getVerificationCode();
            if (verificationCode == null || verificationCode.isEmpty()) {
                verificationCode = generateVerificationCode(reservation.getId());
                reservation.setVerificationCode(verificationCode);
                reservationRepository.save(reservation);
            }
            
            return Result.success("获取二维码成功", verificationCode);
        } catch (Exception e) {
            return Result.error("获取二维码失败: " + e.getMessage());
        }
    }

    /**
     * 管理员扫码核验
     */
    @PostMapping("/verify")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "核校签到")
    public Result<Reservation> verifyByCode(@RequestParam String verificationCode) {
        try {
            Long adminId = currentUserService.getCurrentUserId();
            if (adminId == null) {
                return Result.error(401, "Unauthorized");
            }
            Optional<Reservation> reservationOpt = reservationRepository.findByVerificationCode(verificationCode);
            if (!reservationOpt.isPresent()) {
                return Result.error("核销码无效");
            }
            
            Reservation reservation = reservationOpt.get();
            
            // 检查预约状态
            if (!"APPROVED".equals(reservation.getStatus())) {
                return Result.error("该预约未批准，无法核验");
            }
            
            // 检查预约时间
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime allowCheckinTime = reservation.getStartTime().minusMinutes(15);
            LocalDateTime allowCheckoutTime = reservation.getEndTime().plusMinutes(15);
            
            if (now.isBefore(allowCheckinTime) || now.isAfter(allowCheckoutTime)) {
                return Result.error("不在预约时间范围内，无法核验");
            }
            
            // 根据当前状态执行相应操作
            if ("NOT_CHECKED".equals(reservation.getCheckinStatus())) {
                // 签到
                reservation.setCheckinStatus("CHECKED_IN");
                reservation.setCheckinTime(now);
                reservation.setVerifiedBy(adminId);
                reservation.setVerifiedTime(now);
                
                Reservation savedReservation = reservationRepository.save(reservation);
                enrichReservation(savedReservation);
                
                return Result.success("签到核验成功", savedReservation);
            } else if ("CHECKED_IN".equals(reservation.getCheckinStatus())) {
                // 签退
                reservation.setCheckinStatus("CHECKED_OUT");
                reservation.setCheckoutTime(now);
                reservation.setVerifiedBy(adminId);
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
     * 审核通过预约
     * @param id 预约记录ID
     * @param reservation 包含审核备注的预约记录信息
     * @return 审核通过的预约记录信息
     */
    @PutMapping("/{id}/approve")
    @OperationLog(operationType = "APPROVE_BOOKING", detail = "审核通过预约")
    public Result<Reservation> approve(@PathVariable Long id, @RequestBody Reservation reservation) {
        Long adminId = currentUserService.getCurrentUserId();
        if (adminId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("Reservation not found");
        }

        Reservation existingReservation = resOpt.get();
        if (!isValidStatusTransition(existingReservation.getStatus(), "APPROVED")) {
            return Result.error("Current status does not allow approval");
        }

        try {
            Reservation savedReservation = reservationService.approveReservation(id, reservation.getAdminRemark());
            enrichReservation(savedReservation);
            return Result.success("Reservation approved", savedReservation);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 拒绝预约
     * @param id 预约记录ID
     * @param reservation 包含拒绝原因的预约记录信息
     * @return 被拒绝的预约记录信息
     */
    @PutMapping("/{id}/reject")
    @OperationLog(operationType = "REJECT_BOOKING", detail = "拒绝预约")
    public Result<Reservation> reject(@PathVariable Long id, @RequestBody Reservation reservation) {
        Long adminId = currentUserService.getCurrentUserId();
        if (adminId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();
        
        // 检查状态流转是否合理
        if (!isValidStatusTransition(existingReservation.getStatus(), "REJECTED")) {
            return Result.error("当前状态不允许拒绝操作");
        }
        
        existingReservation.setStatus("REJECTED");
        existingReservation.setAdminRemark(reservation.getAdminRemark());

        Reservation savedReservation = reservationRepository.save(existingReservation);
        enrichReservation(savedReservation);
        return Result.success("已拒绝", savedReservation);
    }

    /**
     * 取消预约
     * @param id 预约记录ID
     * @return 取消后的预约记录信息
     */
    @PutMapping("/{id}/cancel")
    @OperationLog(operationType = "CANCEL_BOOKING", detail = "Cancel reservation")
    public Result<Reservation> cancel(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();
        if (!Objects.equals(existingReservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "Forbidden");
        }
        
        // 验证取消规则
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
     * 搜索预约记录
     * @param keyword 搜索关键词
     * @return 符合条件的预约记录列表
     */
    @GetMapping("/search")
    public Result<List<Reservation>> search(@RequestParam(required = false) String keyword) {
        List<Reservation> reservations;

        if (keyword == null || keyword.trim().isEmpty()) {
            reservations = reservationRepository.findAll();
        } else {
            reservations = reservationRepository.findByKeyword(keyword.trim());
        }

        enrichReservations(reservations);
        return Result.success(reservations);
    }

    /**
     * 完成预约
     * @param id 预约记录ID
     * @return 完成后的预约记录信息
     */
    @PutMapping("/{id}/complete")
    @OperationLog(operationType = "COMPLETE_BOOKING", detail = "Complete reservation")
    public Result<Reservation> complete(@PathVariable Long id) {
        Long adminId = currentUserService.getCurrentUserId();
        if (adminId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation existingReservation = resOpt.get();

        // 只有已通过的预约才能完成
        if (!"APPROVED".equals(existingReservation.getStatus())) {
            return Result.error("只有已通过的预约才能完成");
        }

        // 更新预约状态为已完成
        existingReservation.setStatus("COMPLETED");

        Reservation savedReservation = reservationRepository.save(existingReservation);
        enrichReservation(savedReservation);
        return Result.success("预约已完成", savedReservation);
    }

    /**
     * 删除预约记录
     * @param id 预约记录ID
     * @return 删除结果
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
     * 验证取消预约是否符合规则
     * @param reservation 预约信息
     * @return 验证结果
     */
    private Result<String> validateCancelRules(Reservation reservation) {
        // 检查当前状态是否允许取消
        if (!("PENDING".equals(reservation.getStatus()) || "APPROVED".equals(reservation.getStatus()))) {
            return Result.error("当前状态不允许取消预约");
        }

        // 检查签到状态，只有未签到的预约才能取消
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

        // 获取适用的规则配置
        Optional<Facility> facilityOpt = facilityRepository.findById(reservation.getFacilityId());
        if (!facilityOpt.isPresent()) {
            return Result.error("设施信息不存在");
        }
        
        RuleConfig ruleConfig = getApplicableRuleConfig(facilityOpt.get());
        if (ruleConfig == null || ruleConfig.getCancelDeadlineMinutes() == null) {
            return Result.success("无取消时间限制");
        }

        // 验证取消截止时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cancelDeadline = reservation.getStartTime().minusMinutes(ruleConfig.getCancelDeadlineMinutes());
        
        if (now.isAfter(cancelDeadline)) {
            return Result.error("预约开始时间前" + ruleConfig.getCancelDeadlineMinutes() + "分钟内不允许取消");
        }

        return Result.success("可以取消");
    }

    /**
     * 用户签到
     * @param id 预约记录ID
     * @return 签到结果
     */
    @PutMapping("/{id}/checkin")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "核校签到")
    public Result<Reservation> checkin(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "Forbidden");
        }
        
        // 检查预约状态
        if (!"APPROVED".equals(reservation.getStatus())) {
            return Result.error("只有已通过的预约才能签到");
        }
        
        // 检查签到状态
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
        
        // 检查是否在预约时间范围内（可提前15分钟签到）
        if (now.isBefore(reservation.getStartTime().minusMinutes(15))) {
            return Result.error("还未到签到时间，可提前15分钟签到");
        }
        
        if (now.isAfter(reservation.getEndTime())) {
            return Result.error("预约时间已结束，无法签到");
        }
        
        // 更新签到信息
        reservation.setCheckinStatus("CHECKED_IN");
        reservation.setCheckinTime(now);
        
        // 生成核销码
        String verificationCode = generateVerificationCode(id);
        reservation.setVerificationCode(verificationCode);
        
        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("签到成功", savedReservation);
    }

    /**
     * 用户签退
     * @param id 预约记录ID
     * @return 签退结果
     */
    @PutMapping("/{id}/checkout")
    @OperationLog(operationType = "VERIFY_CHECKOUT", detail = "核校签退")
    public Result<Reservation> checkout(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "Forbidden");
        }
        
        // 检查预约状态
        if (!"APPROVED".equals(reservation.getStatus())) {
            return Result.error("只有已通过的预约才能签退");
        }
        
        // 检查签到状态
        if (!"CHECKED_IN".equals(reservation.getCheckinStatus())) {
            return Result.error("请先签到后再签退");
        }
        
        // 更新签退信息
        reservation.setCheckinStatus("CHECKED_OUT");
        reservation.setCheckoutTime(LocalDateTime.now());
        
        // 签退时自动完成预约
        reservation.setStatus("COMPLETED");
        
        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("签退成功，预约已完成", savedReservation);
    }

    /**
     * 管理员核销预约
     * @param id 预约记录ID
     * @param adminId 管理员ID
     * @param verificationCode 核销码
     * @return 核销结果
     */
    @PutMapping("/{id}/verify")
    @OperationLog(operationType = "VERIFY_CHECKIN", detail = "核校签到")
    public Result<Reservation> verify(@PathVariable Long id, 
                                     @RequestParam String verificationCode) {
        Long adminId = currentUserService.getCurrentUserId();
        if (adminId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        
        // 检查核销码
        if (!verificationCode.equals(reservation.getVerificationCode())) {
            return Result.error("核销码错误");
        }
        
        // 检查预约状态
        if (!"CHECKED_IN".equals(reservation.getCheckinStatus())) {
            return Result.error("该预约状态无法核销");
        }
        
        // 更新核销信息
        reservation.setVerifiedBy(adminId);
        reservation.setVerifiedTime(LocalDateTime.now());
        reservation.setCheckinStatus("CHECKED_OUT");
        reservation.setCheckoutTime(LocalDateTime.now());
        
        // 核销时自动完成预约
        reservation.setStatus("COMPLETED");
        
        Reservation savedReservation = reservationRepository.save(reservation);
        enrichReservation(savedReservation);
        return Result.success("核销成功，预约已完成", savedReservation);
    }

    /**
     * 检查设施在指定时间段是否可用
     * @param facilityId 设施ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 检查结果
     */
    @GetMapping("/availability")
    public Result<Map<String, Object>> checkAvailability(@RequestParam Long facilityId,
                                                         @RequestParam String startTime,
                                                         @RequestParam String endTime) {
        try {
            // 解析时间参数
            LocalDateTime start = LocalDateTime.parse(startTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime end = LocalDateTime.parse(endTime, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 检查设施是否存在
            Optional<Facility> facilityOpt = facilityRepository.findById(facilityId);
            if (!facilityOpt.isPresent()) {
                return Result.error("设施不存在");
            }
            
            // 检查时间有效性
            if (end.isBefore(start)) {
                return Result.error("结束时间不能早于开始时间");
            }
            
            if (start.isBefore(LocalDateTime.now())) {
                return Result.error("不能预约过去的时间");
            }
            
            // 检查时间冲突
            List<String> validStatuses = Arrays.asList("APPROVED", "PENDING", "COMPLETED");
            List<Reservation> conflictingReservations = reservationRepository.findConflictingReservations(
                facilityId, start, end, validStatuses
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("available", conflictingReservations.isEmpty());
            result.put("message", conflictingReservations.isEmpty() ? "该时间段可用" : "该时间段已被预约");
            
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("检查失败：" + e.getMessage());
        }
    }

    /**
     * 获取预约核销码
     * @param id 预约记录ID
     * @return 核销码
     */
    @GetMapping("/{id}/verification-code")
    public Result<Map<String, String>> getVerificationCode(@PathVariable Long id) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "Unauthorized");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (!resOpt.isPresent()) {
            return Result.error("预约不存在");
        }

        Reservation reservation = resOpt.get();
        if (!Objects.equals(reservation.getUserId(), currentUserId) && !currentUserService.hasRole("ADMIN")) {
            return Result.error(403, "Forbidden");
        }
        if (reservation.getVerificationCode() == null) {
            return Result.error("该预约暂无核销码");
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("verificationCode", reservation.getVerificationCode());
        return Result.success(result);
    }

    /**
     * 生成核销码
     * @param reservationId 预约ID
     * @return 核销码
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
            // 如果MD5不可用，使用简单的随机码
            return String.format("%08d", (int)(Math.random() * 100000000));
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
     * 检查时间是否冲突
     * @param start1 第一个时间段的开始时间
     * @param end1 第一个时间段的结束时间
     * @param start2 第二个时间段的开始时间
     * @param end2 第二个时间段的结束时间
     * @return 是否冲突
     */
    private boolean isTimeConflict(LocalDateTime start1, LocalDateTime end1, 
                                  LocalDateTime start2, LocalDateTime end2) {
        return !(end1.isBefore(start2) || start1.isAfter(end2));
    }

    /**
     * 验证预约状态流转的合理性
     * @param currentStatus 当前状态
     * @param targetStatus 目标状态
     * @return 是否允许状态转换
     */
    private boolean isValidStatusTransition(String currentStatus, String targetStatus) {
        // 定义允许的状态流转
        Map<String, Set<String>> allowedTransitions = Map.of(
            "PENDING", Set.of("APPROVED", "REJECTED", "CANCELLED"),      // 待审核 -> 已通过/已拒绝/已取消
            "APPROVED", Set.of("COMPLETED", "CANCELLED"),                // 已通过 -> 已完成/已取消
            "REJECTED", Set.of(),                                          // 已拒绝 -> 不可转换
            "COMPLETED", Set.of(),                                         // 已完成 -> 不可转换
            "CANCELLED", Set.of()                                          // 已取消 -> 不可转换
        );
        
        Set<String> allowedTargets = allowedTransitions.getOrDefault(currentStatus, Set.of());
        return allowedTargets.contains(targetStatus);
    }

    /**
     * 批量丰富预约记录信息，添加设备名称和用户姓名
     * @param reservations 预约记录列表
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
     * 丰富单个预约记录信息，添加设备名称和用户姓名
     * @param reservation 预约记录
     */
    private void enrichReservation(Reservation reservation) {
        // 设置设施名称
        Optional<Facility> facility = facilityRepository.findById(reservation.getFacilityId());
        facility.ifPresent(e -> reservation.setFacilityName(e.getName()));

        // 设置用户名称 - 优先使用realName，不存在时使用用户名
        Optional<User> userOpt = userRepository.findById(reservation.getUserId());
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            // 优先使用realName，如果为空则使用username
            String displayName = u.getRealName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = u.getUsername();
            }
            reservation.setUserName(displayName);
            reservation.setUserRole(u.getRole());
        } else {
            // 用户不存在时，显示"未知用户"并记录日志
            reservation.setUserName("未知用户");
            reservation.setUserRole(null);
        }
        
        // 设置核销管理员姓名
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

    /**
     * 系统启动时执行一次爽约检测
     */
    @Async
    public void onStartup() {
        System.out.println("系统启动，开始执行爽约检测...");
        autoMarkMissedReservations();
        System.out.println("系统启动爽约检测完成");
    }

    /**
     * 定时任务：自动标记爽约的预约并释放设施
     * 每5分钟执行一次，检查所有预约信息，若有预约符合爽约条件，则将该预约改为爽约，并释放对应的设施
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void autoMarkMissedReservations() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("开始执行爽约检测，当前时间：" + now);
        
        // 查找所有已批准但未签到的预约
        List<Reservation> missedReservations = reservationRepository.findByStatusAndCheckinStatus("APPROVED", "NOT_CHECKED");
        System.out.println("找到 " + missedReservations.size() + " 个待检测的预约");
        
        int missedCount = 0;
        int facilityReleasedCount = 0;
        
        for (Reservation reservation : missedReservations) {
            boolean isMissed = false;
            String missedReason = "";
            
            // 条件1：预约结束时间已经过去（无论是否超过30分钟）
            if (now.isAfter(reservation.getEndTime())) {
                isMissed = true;
                missedReason = "预约结束时间已过，用户未签到";
            }
            
            // 条件2：预约开始时间已经过去超过15分钟，但用户仍未签到
            // 这里假设允许提前15分钟签到，所以开始时间过去15分钟仍未签到视为爽约
            else if (now.isAfter(reservation.getStartTime().plusMinutes(15))) {
                isMissed = true;
                missedReason = "预约开始时间已过15分钟，用户未在规定时间内签到";
            }
            
            if (isMissed) {
                // 标记为爽约
                reservation.setCheckinStatus("MISSED");
                reservation.setStatus("COMPLETED"); // 爽约的预约也标记为已完成
                
                // 添加爽约备注
                if (reservation.getAdminRemark() == null || reservation.getAdminRemark().isEmpty()) {
                    reservation.setAdminRemark("系统自动标记爽约：" + missedReason);
                } else {
                    reservation.setAdminRemark(reservation.getAdminRemark() + " | 系统自动标记爽约：" + missedReason);
                }
                
                reservationRepository.save(reservation);
                missedCount++;
                
                // 自动创建违规记录
                try {
                    ViolationRecord violationRecord = new ViolationRecord();
                    violationRecord.setUserId(reservation.getUserId());
                    violationRecord.setReservationId(reservation.getId());
                    violationRecord.setViolationType("NO_SHOW");
                    violationRecord.setDescription("爽约记录：" + missedReason + "。预约时间：" + reservation.getStartTime() + " 至 " + reservation.getEndTime());
                    violationRecord.setPenaltyPoints(5); // 爽约默认扣5分
                    violationRecord.setReportedBy(76L); // reported_by固定为76
                violationRecord.setReportedTime(LocalDateTime.now());
                    
                    violationRecordService.recordViolation(violationRecord);
                    System.out.println("已自动创建违规记录：预约ID=" + reservation.getId() + 
                                     ", 用户ID=" + reservation.getUserId());
                } catch (Exception e) {
                    System.err.println("创建违规记录失败：" + e.getMessage());
                    e.printStackTrace();
                }
                
                // 记录爽约日志
                System.out.println("标记爽约预约：预约ID=" + reservation.getId() + 
                                 ", 用户ID=" + reservation.getUserId() + 
                                 ", 设施ID=" + reservation.getFacilityId() + 
                                 ", 预约时间=" + reservation.getStartTime() + "-" + reservation.getEndTime() +
                                 ", 爽约原因：" + missedReason);
            }
        }
        
        if (missedCount > 0) {
            System.out.println("自动标记爽约预约完成，共标记 " + missedCount + " 条记录，释放 " + facilityReleasedCount + " 个设施");
        }
    }

    /**
     * 验证预约是否符合规则配置
     * @param reservation 预约信息
     * @param facility 设施信息
     * @return 验证结果
     */
    private Result<String> validateReservationRules(Reservation reservation, Facility facility) {
        // 获取适用的规则配置
        RuleConfig ruleConfig = getApplicableRuleConfig(facility);
        if (ruleConfig == null) {
            return Result.success("无特定规则限制");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = reservation.getStartTime();
        LocalDateTime endTime = reservation.getEndTime();

        // 1. 验证预约时长
        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (ruleConfig.getMinDurationMinutes() != null && durationMinutes < ruleConfig.getMinDurationMinutes()) {
            return Result.error("预约时长不能少于" + ruleConfig.getMinDurationMinutes() + "分钟");
        }
        if (ruleConfig.getMaxDurationMinutes() != null && durationMinutes > ruleConfig.getMaxDurationMinutes()) {
            return Result.error("预约时长不能超过" + ruleConfig.getMaxDurationMinutes() + "分钟");
        }

        // 2. 验证提前预约时间
        if (ruleConfig.getAdvanceDaysMax() != null) {
            LocalDateTime maxAdvanceTime = now.plusDays(ruleConfig.getAdvanceDaysMax());
            if (startTime.isAfter(maxAdvanceTime)) {
                return Result.error("只能提前" + ruleConfig.getAdvanceDaysMax() + "天预约");
            }
        }

        if (ruleConfig.getAdvanceCutoffMinutes() != null) {
            LocalDateTime minAdvanceTime = now.plusMinutes(ruleConfig.getAdvanceCutoffMinutes());
            if (startTime.isBefore(minAdvanceTime)) {
                return Result.error("需要提前" + ruleConfig.getAdvanceCutoffMinutes() + "分钟预约");
            }
        }

        // 3. 验证是否允许当日预约
        if (ruleConfig.getAllowSameDayBooking() != null && !ruleConfig.getAllowSameDayBooking()) {
            if (startTime.toLocalDate().equals(now.toLocalDate())) {
                return Result.error("不允许当日预约");
            }
        }

        // 4. 验证开放时间
        if (ruleConfig.getOpenTime() != null && ruleConfig.getCloseTime() != null) {
            LocalTime startLocalTime = startTime.toLocalTime();
            LocalTime endLocalTime = endTime.toLocalTime();
            
            if (startLocalTime.isBefore(ruleConfig.getOpenTime()) || endLocalTime.isAfter(ruleConfig.getCloseTime())) {
                return Result.error("预约时间必须在" + ruleConfig.getOpenTime() + "至" + ruleConfig.getCloseTime() + "之间");
            }
        }

        // 5. 验证用户每日预约数量限制
        if (ruleConfig.getMaxBookingsPerDay() != null) {
            LocalDate reservationDate = startTime.toLocalDate();
            List<Reservation> userDailyReservations = reservationRepository.findByUserId(reservation.getUserId());
            long dailyCount = userDailyReservations.stream()
                    .filter(r -> r.getStartTime().toLocalDate().equals(reservationDate))
                    .filter(r -> !("REJECTED".equals(r.getStatus()) || "CANCELLED".equals(r.getStatus())))
                    .count();
            
            if (dailyCount >= ruleConfig.getMaxBookingsPerDay()) {
                return Result.error("您今日预约次数已达上限（" + ruleConfig.getMaxBookingsPerDay() + "次）");
            }
        }

        // 6. 验证用户活跃预约数量限制
        if (ruleConfig.getMaxActiveBookings() != null) {
            List<Reservation> userActiveReservations = reservationRepository.findByUserIdAndStatusIn(
                reservation.getUserId(), 
                Arrays.asList("PENDING", "APPROVED")
            );
            
            if (userActiveReservations.size() >= ruleConfig.getMaxActiveBookings()) {
                return Result.error("您的活跃预约数量已达上限（" + ruleConfig.getMaxActiveBookings() + "个）");
            }
        }

        return Result.success("规则验证通过");
    }

    /**
     * 获取适用的规则配置
     * 优先使用设施类别的特定规则，如果没有则使用全局默认规则
     * @param facility 设施信息
     * @return 适用的规则配置
     */
    private RuleConfig getApplicableRuleConfig(Facility facility) {
        // 1. 尝试获取设施类别的特定规则
        if (facility.getCategory() != null) {
            // 首先需要通过设施类别名称找到类别ID
            // 这里假设可以通过设施类别名称查询到对应的规则
            Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryName(facility.getCategory());
            if (categoryRuleOpt.isPresent()) {
                return categoryRuleOpt.get();
            }
        }

        // 2. 如果没有类别特定规则，使用全局默认规则
        Optional<RuleConfig> defaultRuleOpt = ruleConfigRepository.findByCategoryIdIsNull();
        return defaultRuleOpt.orElse(null);
    }
}
