package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.ViolationRecord;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.ViolationRecordService;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/violation")
public class ViolationRecordController {

    @Autowired
    private ViolationRecordService violationRecordService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * 记录违规
     */
    @PostMapping("/record")
    @OperationLog(operationType = "CREATE_VIOLATION", detail = "创建违规记录")
    public Result recordViolation(@RequestBody ViolationRecord violationRecord) {
        try {
            System.out.println("Received violation record request: " + violationRecord);
            System.out.println("Reported time: " + violationRecord.getReportedTime());
            
            ViolationRecord savedViolation = violationRecordService.recordViolation(violationRecord);
            return Result.success("违规记录成功", savedViolation);
        } catch (Exception e) {
            System.err.println("Error recording violation: " + e.getMessage());
            e.printStackTrace();
            return Result.error("违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的违规记录列表
     */
    @GetMapping("/user/{userId}")
    public Result getUserViolations(@PathVariable Long userId,
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
     * 获取违规记录详情
     */
    @GetMapping("/{id}")
    public Result getViolationDetail(@PathVariable Long id) {
        try {
            Optional<ViolationRecord> violation = violationRecordService.getViolationById(id);
            if (violation.isPresent()) {
                return Result.success("获取违规记录详情成功", violation.get());
            } else {
                return Result.error("违规记录不存在");
            }
        } catch (Exception e) {
            return Result.error("获取违规记录详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的活跃违规数量
     */
    @GetMapping("/user/{userId}/active/count")
    public Result getActiveViolationCount(@PathVariable Long userId) {
        try {
            Long count = violationRecordService.getActiveViolationCount(userId);
            return Result.success("获取活跃违规数量成功", count);
        } catch (Exception e) {
            return Result.error("获取活跃违规数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的总处罚分
     */
    @GetMapping("/user/{userId}/total-penalty")
    public Result getTotalPenaltyPoints(@PathVariable Long userId) {
        try {
            Integer penalty = violationRecordService.getTotalPenaltyPoints(userId);
            return Result.success("获取总处罚分成功", penalty);
        } catch (Exception e) {
            return Result.error("获取总处罚分失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的当前信用分
     */
    @GetMapping("/user/{userId}/credit-score")
    public Result getUserCurrentCreditScore(@PathVariable Long userId) {
        try {
            Integer creditScore = violationRecordService.getUserCurrentCreditScore(userId);
            return Result.success("获取用户当前信用分成功", creditScore);
        } catch (Exception e) {
            return Result.error("获取用户当前信用分失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的违规次数
     */
    @GetMapping("/user/{userId}/violation-count")
    public Result getUserViolationCount(@PathVariable Long userId) {
        try {
            Integer violationCount = violationRecordService.getUserViolationCount(userId);
            return Result.success("获取用户违规次数成功", violationCount);
        } catch (Exception e) {
            return Result.error("获取用户违规次数失败: " + e.getMessage());
        }
    }

    /**
     * 更新违规记录状态
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE_VIOLATION_STATUS", detail = "Update violation status")
    public Result updateViolationStatus(@PathVariable Long id,
                                       @RequestParam String status) {
        try {
            Long reportedBy = currentUserService.getCurrentUserId();
            if (reportedBy == null) {
                return Result.error(401, "Unauthorized");
            }
            boolean success = violationRecordService.updateViolationStatus(id, status, reportedBy);
            if (success) {
                return Result.success("更新违规记录状态成功");
            } else {
                return Result.error("违规记录不存在");
            }
        } catch (Exception e) {
            return Result.error("更新违规记录状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户某段时间内的违规记录
     */
    @GetMapping("/user/{userId}/time-range")
    public Result getUserViolationsByTimeRange(@PathVariable Long userId,
                                              @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                              @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getUserViolationsByTimeRange(userId, startTime, endTime, pageable);
            return Result.success("获取时间段内违规记录成功", violations);
        } catch (Exception e) {
            return Result.error("获取时间段内违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有违规记录（管理员使用）
     */
    @GetMapping("/all")
    public Result getAllViolations(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String userName,
                                  @RequestParam(required = false) String violationType,
                                  @RequestParam(required = false) String status) {
        try {
            System.out.println("Getting all violations - page: " + page + ", size: " + size + ", userName: " + userName + ", violationType: " + violationType + ", status: " + status);
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getAllViolations(pageable, userName, violationType, status);
            System.out.println("Successfully retrieved " + violations.getTotalElements() + " violations");
            return Result.success("获取所有违规记录成功", violations);
        } catch (Exception e) {
            System.err.println("Error getting all violations: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取所有违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取维护人员上报的违规记录
     */
    @GetMapping("/maintainer")
    public Result getMaintainerViolations(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) Long maintainerId,
                                         @RequestParam(required = false) String userName,
                                         @RequestParam(required = false) String violationType,
                                         @RequestParam(required = false) String status) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
            Page<ViolationRecord> violations = violationRecordService.getMaintainerViolations(pageable, maintainerId, userName, violationType, status);
            return Result.success("获取维护人员违规记录成功", violations);
        } catch (Exception e) {
            return Result.error("获取维护人员违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取违规记录统计数据
     * 返回完整的统计数据，不受分页和筛选条件影响
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getViolationStats() {
        try {
            Map<String, Object> stats = violationRecordService.getViolationStats();
            return Result.success("获取违规记录统计数据成功", stats);
        } catch (Exception e) {
            System.err.println("获取违规记录统计数据失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取违规记录统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 管理员确认违规记录
     * 确认后扣除用户信誉分，增加违规次数
     */
    @PostMapping("/{id}/approve")
    @OperationLog(operationType = "APPROVE_VIOLATION", detail = "确认违规记录")
    public Result<Map<String, Object>> approveViolation(@PathVariable Long id,
                                                        @RequestParam(required = false) String remark) {
        try {
            Long adminId = currentUserService.getCurrentUserId();
            if (adminId == null) {
                return Result.error(401, "Unauthorized");
            }
            Map<String, Object> result = violationRecordService.approveViolation(id, adminId, remark);
            if ((Boolean) result.get("success")) {
                return Result.success((String) result.get("message"), result);
            } else {
                return Result.error((String) result.get("message"));
            }
        } catch (Exception e) {
            System.err.println("确认违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("确认违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 管理员拒绝违规记录
     * 拒绝后不扣除用户信誉分
     */
    @PostMapping("/{id}/reject")
    @OperationLog(operationType = "REJECT_VIOLATION", detail = "驳回违规记录")
    public Result<Map<String, Object>> rejectViolation(@PathVariable Long id,
                                                       @RequestParam(required = false) String remark) {
        try {
            Long adminId = currentUserService.getCurrentUserId();
            if (adminId == null) {
                return Result.error(401, "Unauthorized");
            }
            Map<String, Object> result = violationRecordService.rejectViolation(id, adminId, remark);
            if ((Boolean) result.get("success")) {
                return Result.success((String) result.get("message"), result);
            } else {
                return Result.error((String) result.get("message"));
            }
        } catch (Exception e) {
            System.err.println("拒绝违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("拒绝违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 管理员取消已生效违规记录
     */
    @PostMapping("/{id}/revoke")
    @OperationLog(operationType = "REVOKE_VIOLATION", detail = "取消生效违规记录")
    public Result<Map<String, Object>> revokeViolation(@PathVariable Long id,
                                                       @RequestParam(required = false) String remark) {
        try {
            Long adminId = currentUserService.getCurrentUserId();
            if (adminId == null) {
                return Result.error(401, "Unauthorized");
            }
            Map<String, Object> result = violationRecordService.revokeViolation(id, adminId, remark);
            if ((Boolean) result.get("success")) {
                return Result.success((String) result.get("message"), result);
            } else {
                return Result.error((String) result.get("message"));
            }
        } catch (Exception e) {
            System.err.println("取消生效违规记录失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("取消生效违规记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的已生效信誉分（基于已确认的违规）
     */
    @GetMapping("/user/{userId}/processed-penalty")
    public Result getProcessedPenaltyPoints(@PathVariable Long userId) {
        try {
            Integer penalty = violationRecordService.getProcessedPenaltyPoints(userId);
            return Result.success("获取已生效处罚分成功", penalty);
        } catch (Exception e) {
            return Result.error("获取已生效处罚分失败: " + e.getMessage());
        }
    }
}
