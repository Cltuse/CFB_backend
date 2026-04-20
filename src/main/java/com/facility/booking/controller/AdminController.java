package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.*;
import com.facility.booking.repository.*;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 管理员专属功能控制器
 * 包含预约规则配置、黑名单管理、操作日志审计等功能
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FacilityCategoryRepository facilityCategoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * 预约规则配置管理
     */

    /**
     * 获取所有当前生效的规则配置
     * @return 规则配置列表
     */
    @GetMapping("/rule-configs")
    public Result<List<RuleConfig>> getAllRuleConfigs() {
        List<RuleConfig> rules = ruleConfigRepository.findAllCurrentRules();
        // 填充类别名称
        rules.forEach(rule -> {
            if (rule.getCategoryId() != null) {
                Optional<FacilityCategory> category = facilityCategoryRepository.findById(rule.getCategoryId());
                category.ifPresent(value -> rule.setCategoryName(value.getCategoryName()));
            } else {
                rule.setCategoryName("全局默认");
            }
        });
        return Result.success(rules);
    }

    /**
     * 获取指定类别的规则配置历史版本
     * @param categoryId 类别ID（可为null表示全局规则）
     * @return 历史版本列表
     */
    @GetMapping("/rule-configs/history")
    public Result<List<RuleConfig>> getRuleConfigHistory(@RequestParam(required = false) Long categoryId) {
        List<RuleConfig> history;
        if (categoryId == null) {
            // 获取全局规则的所有历史版本（按创建时间倒序）
            history = ruleConfigRepository.findByCategoryIdIsNullOrderByCreatedAtDesc();
        } else {
            history = ruleConfigRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId);
        }
        return Result.success(history);
    }

    /**
     * 创建或更新规则配置（保存为新版本）
     * @param ruleConfig 规则配置信息
     * @return 创建的规则配置
     */
    @PostMapping("/rule-configs")
    @OperationLog(operationType = "UPDATE_RULE", detail = "创建或更新规则配置")
    public Result<RuleConfig> createRuleConfig(@RequestBody RuleConfig ruleConfig) {
        try {
            RuleConfig savedRule = ruleConfigRepository.save(ruleConfig);
            return Result.success("规则配置保存成功", savedRule);
        } catch (Exception e) {
            return Result.error("规则配置保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定ID的规则配置详情
     * @param id 规则配置ID
     * @return 规则配置详情
     */
    @GetMapping("/rule-configs/{id}")
    public Result<RuleConfig> getRuleConfigById(@PathVariable Long id) {
        Optional<RuleConfig> ruleOpt = ruleConfigRepository.findById(id);
        if (ruleOpt.isPresent()) {
            RuleConfig rule = ruleOpt.get();
            if (rule.getCategoryId() != null) {
                Optional<FacilityCategory> category = facilityCategoryRepository.findById(rule.getCategoryId());
                category.ifPresent(value -> rule.setCategoryName(value.getCategoryName()));
            } else {
                rule.setCategoryName("全局默认");
            }
            return Result.success(rule);
        }
        return Result.error("规则配置不存在");
    }

    /**
     * 黑名单管理
     */

    /**
     * 获取黑名单列表（支持分页和筛选）
     * @param status 状态筛选（可选）
     * @param userName 用户姓名筛选（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 黑名单列表
     */
    @GetMapping("/blacklist")
    public Result<Map<String, Object>> getBlacklist(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Blacklist> blacklistPage;
        
        if (userName != null && !userName.trim().isEmpty()) {
            blacklistPage = blacklistRepository.findByUserNameContaining(userName, pageable);
        } else if (status != null && !status.trim().isEmpty()) {
            blacklistPage = blacklistRepository.findByStatus(status, pageable);
        } else {
            blacklistPage = blacklistRepository.findAll(pageable);
        }
        
        // 填充用户信息
        blacklistPage.getContent().forEach(blacklist -> {
            Optional<User> user = userRepository.findById(blacklist.getUserId());
            user.ifPresent(value -> {
                blacklist.setUserName(value.getUsername());
                blacklist.setUserRealName(value.getRealName());
            });
            
            if (blacklist.getOperatorId() != null) {
                Optional<User> operator = userRepository.findById(blacklist.getOperatorId());
                operator.ifPresent(value -> blacklist.setOperatorName(value.getRealName()));
            }
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", blacklistPage.getContent());
        result.put("totalElements", blacklistPage.getTotalElements());
        result.put("totalPages", blacklistPage.getTotalPages());
        result.put("currentPage", page);
        
        return Result.success(result);
    }

    /**
     * 将用户加入黑名单
     * @param blacklistData 黑名单信息
     * @return 操作结果
     */
    @PostMapping("/blacklist")
    @OperationLog(operationType = "ADD_BLACKLIST", detail = "将用户加入黑名单")
    public Result<Blacklist> addToBlacklist(@RequestBody Map<String, Object> blacklistData) {
        try {
            Long userId = Long.valueOf(blacklistData.get("userId").toString());
            String reason = blacklistData.get("reason").toString();
            String endTimeStr = blacklistData.get("endTime") != null ? blacklistData.get("endTime").toString() : null;
            Long operatorId = currentUserService.getCurrentUserId();
            if (operatorId == null) {
                return Result.error(401, "Unauthorized");
            }
            
            // 检查用户是否已存在有效黑名单记录
            Optional<Blacklist> existingBlacklist = blacklistRepository.findByUserIdAndStatus(userId, "ACTIVE");
            if (existingBlacklist.isPresent()) {
                return Result.error("该用户已在黑名单中");
            }
            
            Blacklist blacklist = new Blacklist();
            blacklist.setUserId(userId);
            blacklist.setReason(reason);
            blacklist.setStartTime(LocalDateTime.now());
            blacklist.setStatus("ACTIVE");
            blacklist.setOperatorId(operatorId);
            
            if (endTimeStr != null && !endTimeStr.trim().isEmpty()) {
                blacklist.setEndTime(LocalDateTime.parse(endTimeStr));
            }
            
            Blacklist savedBlacklist = blacklistRepository.save(blacklist);
            
            return Result.success("加入黑名单成功", savedBlacklist);
        } catch (Exception e) {
            return Result.error("加入黑名单失败: " + e.getMessage());
        }
    }

    /**
     * 将用户移出黑名单
     * @param id 黑名单记录ID
     * @param operatorId 操作员ID
     * @return 操作结果
     */
    @PutMapping("/blacklist/{id}/remove")
    @OperationLog(operationType = "REMOVE_BLACKLIST", detail = "将用户移出黑名单")
    public Result<Blacklist> removeFromBlacklist(@PathVariable Long id) {
        Long operatorId = currentUserService.getCurrentUserId();
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        Optional<Blacklist> blacklistOpt = blacklistRepository.findById(id);
        if (!blacklistOpt.isPresent()) {
            return Result.error("黑名单记录不存在");
        }
        
        Blacklist blacklist = blacklistOpt.get();
        if (!"ACTIVE".equals(blacklist.getStatus())) {
            return Result.error("该黑名单记录状态不是生效中");
        }
        
        blacklist.setStatus("REMOVED");
        blacklist.setOperatorId(operatorId);
        Blacklist updatedBlacklist = blacklistRepository.save(blacklist);
        
        return Result.success("移出黑名单成功", updatedBlacklist);
    }

    /**
     * 自动更新过期黑名单状态
     * @return 更新的记录数量
     */
    @PutMapping("/blacklist/auto-expire")
    @OperationLog(operationType = "AUTO_EXPIRE_BLACKLIST", detail = "批量更新过期黑名单")
    public Result<Integer> autoExpireBlacklist() {
        LocalDateTime now = LocalDateTime.now();
        List<Blacklist> expiredList = blacklistRepository.findByStatusAndEndTimeBefore("ACTIVE", now);
        
        int updatedCount = 0;
        for (Blacklist blacklist : expiredList) {
            blacklist.setStatus("EXPIRED");
            blacklistRepository.save(blacklist);
            updatedCount++;
        }
        
        return Result.success("自动过期处理完成", updatedCount);
    }

    /**
     * 操作日志审计
     */

    /**
     * 获取操作日志列表（支持分页和筛选）
     * @param operatorId 操作人ID（可选）
     * @param operationType 操作类型（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 操作日志列表
     */
    @GetMapping("/operation-logs")
    public Result<Map<String, Object>> getOperationLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        System.out.println("操作日志查询参数 - operatorId: " + operatorId + ", operationType: " + operationType + ", startTime: " + startTime + ", endTime: " + endTime + ", page: " + page + ", size: " + size);
        
        Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<com.facility.booking.entity.OperationLog> logPage;
        
        LocalDateTime start = null;
        LocalDateTime end = null;
        
        try {
            if (startTime != null && !startTime.trim().isEmpty()) {
                // 尝试解析前端格式：YYYY-MM-DDTHH:mm:ss
                start = LocalDateTime.parse(startTime);
            }
            if (endTime != null && !endTime.trim().isEmpty()) {
                // 尝试解析前端格式：YYYY-MM-DDTHH:mm:ss
                end = LocalDateTime.parse(endTime);
            }
        } catch (Exception e) {
            // 如果解析失败，尝试其他格式
            try {
                if (startTime != null && !startTime.trim().isEmpty()) {
                    // 尝试空格格式：YYYY-MM-DD HH:mm:ss
                    start = LocalDateTime.parse(startTime.replace(" ", "T"));
                }
                if (endTime != null && !endTime.trim().isEmpty()) {
                    // 尝试空格格式：YYYY-MM-DD HH:mm:ss
                    end = LocalDateTime.parse(endTime.replace(" ", "T"));
                }
            } catch (Exception e2) {
                return Result.error("时间格式错误，请使用ISO格式（yyyy-MM-ddTHH:mm:ss）或（yyyy-MM-dd HH:mm:ss）");
            }
        }
        
        // 根据搜索条件选择合适的查询方法
        boolean hasSearchCondition = operatorId != null || 
                                   (operationType != null && !operationType.trim().isEmpty()) || 
                                   start != null || end != null;
        
        if (hasSearchCondition) {
            System.out.println("使用条件查询操作日志");
            // 设置默认时间范围（如果没有指定时间范围）
            if (start == null && end == null) {
                // 如果没有任何时间条件，查询所有时间的数据
                start = LocalDateTime.of(2000, 1, 1, 0, 0);
                end = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
            } else if (start == null) {
                start = LocalDateTime.of(2000, 1, 1, 0, 0);
            } else if (end == null) {
                end = LocalDateTime.now();
            }
            
            logPage = operationLogRepository.findByConditions(
                operatorId, 
                operationType, 
                start, 
                end, 
                pageable
            );
        } else {
            System.out.println("使用无条件查询所有操作日志");
            logPage = operationLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        
        System.out.println("查询完成，记录数: " + logPage.getTotalElements() + ", 当前页记录数: " + logPage.getContent().size());
        
        // 填充操作人姓名
        logPage.getContent().forEach(log -> {
            if (log.getOperatorId() != null) {
                Optional<User> operator = userRepository.findById(log.getOperatorId());
                operator.ifPresent(value -> log.setOperatorName(value.getRealName()));
            }
        });
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", logPage.getContent());
        result.put("totalElements", logPage.getTotalElements());
        result.put("totalPages", logPage.getTotalPages());
        result.put("currentPage", page);
        
        // 添加调试信息
        System.out.println("操作日志查询结果 - 总记录数: " + logPage.getTotalElements());
        System.out.println("操作日志查询结果 - 当前页记录数: " + logPage.getContent().size());
        
        return Result.success(result);
    }

    /**
     * 获取操作日志详情
     * @param id 日志ID
     * @return 日志详情
     */
    @GetMapping("/operation-logs/{id}")
    public Result<com.facility.booking.entity.OperationLog> getOperationLogById(@PathVariable Long id) {
        Optional<com.facility.booking.entity.OperationLog> logOpt = operationLogRepository.findById(id);
        if (logOpt.isPresent()) {
            com.facility.booking.entity.OperationLog log = logOpt.get();
            if (log.getOperatorId() != null) {
                Optional<User> operator = userRepository.findById(log.getOperatorId());
                operator.ifPresent(value -> log.setOperatorName(value.getRealName()));
            }
            return Result.success(log);
        }
        return Result.error("操作日志不存在");
    }

    /**
     * 获取操作类型列表
     * @return 操作类型列表
     */
    @GetMapping("/operation-logs/types")
    public Result<List<String>> getOperationTypes() {
        try {
            System.out.println("获取操作类型列表 - 开始");
            List<String> types = List.of(
                "APPROVE_BOOKING", "REJECT_BOOKING", "VERIFY_CHECKIN", "VERIFY_CHECKOUT",
                "ADD_BLACKLIST", "REMOVE_BLACKLIST", "AUTO_EXPIRE_BLACKLIST",
                "REPLY_FEEDBACK", "UPDATE_FEEDBACK_STATUS", "DELETE_FEEDBACK",
                "UPDATE_RULE", "DELETE_RULE", "PUBLISH_NOTICE", "UPDATE_NOTICE",
                "DELETE_NOTICE", "PUBLISH_SCHEDULED_NOTICE", "CREATE_FACILITY",
                "UPDATE_FACILITY", "UPDATE_FACILITY_STATUS", "DELETE_FACILITY",
                "UPLOAD_FACILITY_IMAGE", "DELETE_FACILITY_IMAGE", "CREATE_FACILITY_CATEGORY",
                "UPDATE_FACILITY_CATEGORY", "TOGGLE_FACILITY_CATEGORY_STATUS", "DELETE_FACILITY_CATEGORY",
                "CREATE_MAINTENANCE", "UPDATE_MAINTENANCE", "COMPLETE_MAINTENANCE",
                "DELETE_MAINTENANCE", "CREATE_VIOLATION", "APPROVE_VIOLATION",
                "REJECT_VIOLATION", "REVOKE_VIOLATION"
            );
            System.out.println("获取操作类型列表 - 成功，类型数量: " + types.size());
            return Result.success(types);
        } catch (Exception e) {
            System.err.println("获取操作类型列表 - 失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("获取操作类型列表失败: " + e.getMessage());
        }
    }

    /**
     * 预约管理统计
     */

    /**
     * 获取预约统计数据
     * @return 预约统计信息
     */
    @GetMapping("/reservation-stats")
    public Result<Map<String, Object>> getReservationStats() {
        try {
            // 获取各种状态的预约数量
            long totalReservations = reservationRepository.count();
            long pendingReservations = reservationRepository.countByStatus("PENDING");
            long approvedReservations = reservationRepository.countByStatus("APPROVED");
            long completedReservations = reservationRepository.countByStatus("COMPLETED");
            long rejectedReservations = reservationRepository.countByStatus("REJECTED");
            long cancelledReservations = reservationRepository.countByStatus("CANCELLED");
            
            // 获取签到相关统计
            long notCheckedReservations = reservationRepository.countByCheckinStatus("NOT_CHECKED");
            long checkedInReservations = reservationRepository.countByCheckinStatus("CHECKED_IN");
            long checkedOutReservations = reservationRepository.countByCheckinStatus("CHECKED_OUT");
            long missedReservations = reservationRepository.countByCheckinStatus("MISSED");
            
            // 获取今日数据
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayTotal = reservationRepository.countByCreatedAtAfter(todayStart);
            long todayPending = reservationRepository.countByCreatedAtAfterAndStatus(todayStart, "PENDING");
            long todayApproved = reservationRepository.countByCreatedAtAfterAndStatus(todayStart, "APPROVED");
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalReservations", totalReservations);
            stats.put("pendingReservations", pendingReservations);
            stats.put("approvedReservations", approvedReservations);
            stats.put("completedReservations", completedReservations);
            stats.put("rejectedReservations", rejectedReservations);
            stats.put("cancelledReservations", cancelledReservations);
            stats.put("notCheckedReservations", notCheckedReservations);
            stats.put("checkedInReservations", checkedInReservations);
            stats.put("checkedOutReservations", checkedOutReservations);
            stats.put("missedReservations", missedReservations);
            stats.put("todayTotal", todayTotal);
            stats.put("todayPending", todayPending);
            stats.put("todayApproved", todayApproved);
            
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error("获取预约统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取预约趋势数据（最近7天）
     * @return 预约趋势数据
     */
    @GetMapping("/reservation-trends")
    public Result<List<Map<String, Object>>> getReservationTrends() {
        try {
            List<Map<String, Object>> trends = new ArrayList<>();
            LocalDateTime endTime = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime startTime = endTime.minusDays(7);
            Map<String, ReservationRepository.DailyTrendView> trendsByDate = reservationRepository.countDailyTrends(startTime, endTime)
                    .stream()
                    .collect(Collectors.toMap(ReservationRepository.DailyTrendView::getDate, trend -> trend));
            
            for (int i = 6; i >= 0; i--) {
                LocalDateTime date = endTime.minusDays(i + 1);
                ReservationRepository.DailyTrendView dayTrend = trendsByDate.get(date.toLocalDate().toString());

                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", date.toLocalDate().toString());
                dayData.put("total", dayTrend == null ? 0L : dayTrend.getTotal());
                dayData.put("pending", dayTrend == null ? 0L : dayTrend.getPending());
                dayData.put("approved", dayTrend == null ? 0L : dayTrend.getApproved());
                dayData.put("completed", dayTrend == null ? 0L : dayTrend.getCompleted());
                
                trends.add(dayData);
            }
            
            return Result.success(trends);
        } catch (Exception e) {
            return Result.error("获取预约趋势数据失败: " + e.getMessage());
        }
    }
}
