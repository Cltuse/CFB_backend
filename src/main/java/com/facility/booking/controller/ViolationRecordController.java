package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Reservation;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.repository.ReservationRepository;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.ViolationRecordService;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/violation")
public class ViolationRecordController {

    @Autowired
    private ViolationRecordService violationRecordService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private ReservationRepository reservationRepository;

    /**
     * 上报违规记录。
     */
    @PostMapping("/record")
    @OperationLog(operationType = "CREATE_VIOLATION", detail = "创建违规记录")
    public Result<ViolationRecord> recordViolation(@RequestBody ViolationRecord violationRecord) {
        try {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (currentUserId == null) {
                return Result.error(401, "未登录或登录已失效");
            }
            if (!currentUserService.hasRole("ADMIN") && !currentUserService.hasRole("MAINTAINER")) {
                return Result.error(403, "当前角色无权上报违规");
            }

            violationRecord.setReportedBy(currentUserId);
            if (violationRecord.getReportedTime() == null) {
                violationRecord.setReportedTime(LocalDateTime.now());
            }

            Optional<Reservation> reservationOpt = Optional.empty();
            if (violationRecord.getReservationId() != null) {
                reservationOpt = reservationRepository.findById(violationRecord.getReservationId());
                if (reservationOpt.isEmpty()) {
                    return Result.error("关联预约不存在");
                }
            }

            if (currentUserService.hasRole("MAINTAINER")) {
                if (violationRecord.getReservationId() == null) {
                    return Result.error(400, "设施管理员上报违规时必须关联预约记录");
                }
                if (!violationRecordService.canMaintainerAccessReservation(currentUserId, violationRecord.getReservationId())) {
                    return Result.error(403, "只能上报自己负责设施的预约违规");
                }
            }

            if (reservationOpt.isPresent()) {
                Reservation reservation = reservationOpt.get();
                if (!Objects.equals(reservation.getUserId(), violationRecord.getUserId())) {
                    return Result.error("违规用户与关联预约不匹配");
                }
            }

            ViolationRecord savedViolation = violationRecordService.recordViolation(violationRecord);
            return Result.success("违规记录上报成功", savedViolation);
        } catch (Exception e) {
            System.err.println("上报违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("上报违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户违规记录。
     */
    @GetMapping("/user/{userId}")
    public Result<Page<ViolationRecord>> getUserViolations(@PathVariable Long userId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getUserViolations(userId, pageable);
            return Result.success("获取违规记录成功", violations);
        } catch (Exception e) {
            return Result.error("获取违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取违规记录详情。
     */
    @GetMapping("/{id}")
    public Result<ViolationRecord> getViolationDetail(@PathVariable Long id) {
        try {
            Optional<ViolationRecord> violationOpt = violationRecordService.getViolationById(id);
            if (violationOpt.isEmpty()) {
                return Result.error("违规记录不存在");
            }

            ViolationRecord violation = violationOpt.get();
            if (currentUserService.hasRole("MAINTAINER")) {
                Long currentUserId = currentUserService.getCurrentUserId();
                if (!violationRecordService.canMaintainerAccessViolation(currentUserId, violation)) {
                    return Result.error(403, "无权查看该违规记录");
                }
            }

            return Result.success("获取违规记录详情成功", violation);
        } catch (Exception e) {
            return Result.error("获取违规记录详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户已生效违规数量。
     */
    @GetMapping("/user/{userId}/active/count")
    public Result<Long> getActiveViolationCount(@PathVariable Long userId) {
        try {
            Long count = violationRecordService.getActiveViolationCount(userId);
            return Result.success("获取活跃违规数量成功", count);
        } catch (Exception e) {
            return Result.error("获取活跃违规数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户累计处罚分。
     */
    @GetMapping("/user/{userId}/total-penalty")
    public Result<Integer> getTotalPenaltyPoints(@PathVariable Long userId) {
        try {
            Integer penalty = violationRecordService.getTotalPenaltyPoints(userId);
            return Result.success("获取累计处罚分成功", penalty);
        } catch (Exception e) {
            return Result.error("获取累计处罚分失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户当前信用分。
     */
    @GetMapping("/user/{userId}/credit-score")
    public Result<Integer> getUserCurrentCreditScore(@PathVariable Long userId) {
        try {
            Integer creditScore = violationRecordService.getUserCurrentCreditScore(userId);
            return Result.success("获取当前信用分成功", creditScore);
        } catch (Exception e) {
            return Result.error("获取当前信用分失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户违规次数。
     */
    @GetMapping("/user/{userId}/violation-count")
    public Result<Integer> getUserViolationCount(@PathVariable Long userId) {
        try {
            Integer violationCount = violationRecordService.getUserViolationCount(userId);
            return Result.success("获取违规次数成功", violationCount);
        } catch (Exception e) {
            return Result.error("获取违规次数失败: " + e.getMessage());
        }
    }

    /**
     * 更新违规记录状态。
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE_VIOLATION_STATUS", detail = "更新违规状态")
    public Result<Void> updateViolationStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (currentUserId == null) {
                return Result.error(401, "未登录或登录已失效");
            }

            if (currentUserService.hasRole("MAINTAINER")) {
                Optional<ViolationRecord> violationOpt = violationRecordService.getViolationById(id);
                if (violationOpt.isEmpty()) {
                    return Result.error("违规记录不存在");
                }
                if (!violationRecordService.canMaintainerAccessViolation(currentUserId, violationOpt.get())) {
                    return Result.error(403, "无权处理该违规记录");
                }
            }

            boolean success = violationRecordService.updateViolationStatus(id, status, currentUserId);
            if (success) {
                return Result.success("更新违规状态成功", null);
            }
            return Result.error("违规记录不存在");
        } catch (Exception e) {
            return Result.error("更新违规状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户某段时间内的违规记录。
     */
    @GetMapping("/user/{userId}/time-range")
    public Result<Page<ViolationRecord>> getUserViolationsByTimeRange(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getUserViolationsByTimeRange(
                    userId, startTime, endTime, pageable
            );
            return Result.success("获取时间段内违规记录成功", violations);
        } catch (Exception e) {
            return Result.error("获取时间段内违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取全部违规记录。
     */
    @GetMapping("/all")
    public Result<Page<ViolationRecord>> getAllViolations(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String userName,
                                                          @RequestParam(required = false) String violationType,
                                                          @RequestParam(required = false) String status) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getAllViolations(pageable, userName, violationType, status);
            return Result.success("获取违规记录成功", violations);
        } catch (Exception e) {
            System.err.println("获取违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取设施管理员负责场地对应的违规记录。
     */
    @GetMapping("/maintainer")
    public Result<Page<ViolationRecord>> getMaintainerViolations(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size,
                                                                 @RequestParam(required = false) Long maintainerId,
                                                                 @RequestParam(required = false) String userName,
                                                                 @RequestParam(required = false) String violationType,
                                                                 @RequestParam(required = false) String status) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));

            Long scopedMaintainerId = maintainerId;
            if (currentUserService.hasRole("MAINTAINER")) {
                scopedMaintainerId = currentUserService.getCurrentUserId();
            }

            Page<ViolationRecord> violations = violationRecordService.getMaintainerViolations(
                    pageable, scopedMaintainerId, userName, violationType, status
            );
            return Result.success("获取设施管理员违规记录成功", violations);
        } catch (Exception e) {
            return Result.error("获取设施管理员违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取违规统计数据。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getViolationStats() {
        try {
            Map<String, Object> stats = violationRecordService.getViolationStats();
            return Result.success("获取违规统计数据成功", stats);
        } catch (Exception e) {
            System.err.println("获取违规统计数据失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取违规统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 确认违规记录。
     */
    @PostMapping("/{id}/approve")
    @OperationLog(operationType = "APPROVE_VIOLATION", detail = "确认违规记录")
    public Result<Map<String, Object>> approveViolation(@PathVariable Long id,
                                                        @RequestParam(required = false) String remark) {
        try {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (currentUserId == null) {
                return Result.error(401, "未登录或登录已失效");
            }

            if (currentUserService.hasRole("MAINTAINER")) {
                Optional<ViolationRecord> violationOpt = violationRecordService.getViolationById(id);
                if (violationOpt.isEmpty()) {
                    return Result.error("违规记录不存在");
                }
                if (!violationRecordService.canMaintainerAccessViolation(currentUserId, violationOpt.get())) {
                    return Result.error(403, "无权处理该违规记录");
                }
            }

            Map<String, Object> result = violationRecordService.approveViolation(id, currentUserId, remark);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return Result.success((String) result.get("message"), result);
            }
            return Result.error((String) result.get("message"));
        } catch (Exception e) {
            System.err.println("确认违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("确认违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 驳回违规记录。
     */
    @PostMapping("/{id}/reject")
    @OperationLog(operationType = "REJECT_VIOLATION", detail = "驳回违规记录")
    public Result<Map<String, Object>> rejectViolation(@PathVariable Long id,
                                                       @RequestParam(required = false) String remark) {
        try {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (currentUserId == null) {
                return Result.error(401, "未登录或登录已失效");
            }

            if (currentUserService.hasRole("MAINTAINER")) {
                Optional<ViolationRecord> violationOpt = violationRecordService.getViolationById(id);
                if (violationOpt.isEmpty()) {
                    return Result.error("违规记录不存在");
                }
                if (!violationRecordService.canMaintainerAccessViolation(currentUserId, violationOpt.get())) {
                    return Result.error(403, "无权处理该违规记录");
                }
            }

            Map<String, Object> result = violationRecordService.rejectViolation(id, currentUserId, remark);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return Result.success((String) result.get("message"), result);
            }
            return Result.error((String) result.get("message"));
        } catch (Exception e) {
            System.err.println("驳回违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("驳回违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 撤销已生效违规记录。
     */
    @PostMapping("/{id}/revoke")
    @OperationLog(operationType = "REVOKE_VIOLATION", detail = "撤销已生效违规")
    public Result<Map<String, Object>> revokeViolation(@PathVariable Long id,
                                                       @RequestParam(required = false) String remark) {
        try {
            Long currentUserId = currentUserService.getCurrentUserId();
            if (currentUserId == null) {
                return Result.error(401, "未登录或登录已失效");
            }

            if (currentUserService.hasRole("MAINTAINER")) {
                Optional<ViolationRecord> violationOpt = violationRecordService.getViolationById(id);
                if (violationOpt.isEmpty()) {
                    return Result.error("违规记录不存在");
                }
                if (!violationRecordService.canMaintainerAccessViolation(currentUserId, violationOpt.get())) {
                    return Result.error(403, "无权处理该违规记录");
                }
            }

            Map<String, Object> result = violationRecordService.revokeViolation(id, currentUserId, remark);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return Result.success((String) result.get("message"), result);
            }
            return Result.error((String) result.get("message"));
        } catch (Exception e) {
            System.err.println("撤销违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("撤销违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户已生效处罚分。
     */
    @GetMapping("/user/{userId}/processed-penalty")
    public Result<Integer> getProcessedPenaltyPoints(@PathVariable Long userId) {
        try {
            Integer penalty = violationRecordService.getProcessedPenaltyPoints(userId);
            return Result.success("获取已生效处罚分成功", penalty);
        } catch (Exception e) {
            return Result.error("获取已生效处罚分失败: " + e.getMessage());
        }
    }
}
