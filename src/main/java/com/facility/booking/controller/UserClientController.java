package com.facility.booking.controller;

import com.facility.booking.entity.*;
import com.facility.booking.repository.*;
import com.facility.booking.service.RuleConfigService;
import com.facility.booking.common.Result;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户端功能控制器
 * 提供用户专属功能：违规记录查看、反馈建议提交与查看、预约规则查看等
 */
@RestController
@RequestMapping("/api/user-client")
public class UserClientController {

    @Autowired
    private ViolationRecordRepository violationRecordRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FacilityCategoryRepository facilityCategoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RuleConfigService ruleConfigService;

    /**
     * 违规记录查看功能
     */

    /**
     * 获取当前用户的违规记录列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 违规记录列表
     */
    @GetMapping("/violation-records/{userId}")
    public Result<Map<String, Object>> getMyViolationRecords(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // 验证用户存在
        if (!userRepository.existsById(userId)) {
            return Result.error("用户不存在");
        }

        Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "reportedTime"));
        Page<ViolationRecord> violationPage = violationRecordRepository.findByUserIdOrderByReportedTimeDesc(userId, pageable);
        
        // 丰富违规记录信息
        List<ViolationRecord> violations = violationPage.getContent();
        enrichViolationRecords(violations);
        
        Map<String, Object> result = new HashMap<>();
        result.put("violations", violations);
        result.put("total", violationPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 获取违规记录详情
     * @param id 违规记录ID
     * @param userId 用户ID（用于权限验证）
     * @return 违规记录详情
     */
    @GetMapping("/violation-records/{id}/detail")
    public Result<ViolationRecord> getViolationRecordDetail(
            @PathVariable Long id,
            @RequestParam Long userId) {
        
        Optional<ViolationRecord> violationOpt = violationRecordRepository.findById(id);
        if (!violationOpt.isPresent()) {
            return Result.error("违规记录不存在");
        }
        
        ViolationRecord violation = violationOpt.get();
        
        // 权限验证：用户只能查看自己的违规记录
        if (!violation.getUserId().equals(userId)) {
            return Result.error("无权查看该违规记录");
        }
        
        enrichViolationRecord(violation);
        
        return Result.success(violation);
    }

    /**
     * 反馈建议管理功能
     */

    /**
     * 获取当前用户的反馈列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 反馈列表
     */
    @GetMapping("/feedbacks/{userId}")
    public Result<Map<String, Object>> getMyFeedbacks(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // 验证用户存在
        if (!userRepository.existsById(userId)) {
            return Result.error("用户不存在");
        }

        Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Feedback> feedbackPage = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        // 丰富反馈信息
        List<Feedback> feedbacks = feedbackPage.getContent();
        enrichFeedbacks(feedbacks);
        
        Map<String, Object> result = new HashMap<>();
        result.put("feedbacks", feedbacks);
        result.put("total", feedbackPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 提交新的反馈
     * @param feedback 反馈信息
     * @return 提交结果
     */
    @PostMapping("/feedbacks")
    public Result<Feedback> submitFeedback(@RequestBody Feedback feedback) {
        
        // 验证用户存在
        if (!userRepository.existsById(feedback.getUserId())) {
            return Result.error("用户不存在");
        }

        // 数据验证
        if (feedback.getTitle() == null || feedback.getTitle().trim().isEmpty()) {
            return Result.error("反馈标题不能为空");
        }
        
        if (feedback.getContent() == null || feedback.getContent().trim().isEmpty()) {
            return Result.error("反馈内容不能为空");
        }

        // 设置默认值
        if (feedback.getType() == null || feedback.getType().trim().isEmpty()) {
            feedback.setType("SUGGESTION");
        }
        
        feedback.setStatus("PENDING");
        feedback.setReply(null);
        feedback.setReplyTime(null);
        feedback.setReplyBy(null);

        Feedback savedFeedback = feedbackRepository.save(feedback);
        enrichFeedback(savedFeedback);
        
        return Result.success("反馈提交成功", savedFeedback);
    }

    /**
     * 获取反馈详情
     * @param id 反馈ID
     * @param userId 用户ID（用于权限验证）
     * @return 反馈详情
     */
    @GetMapping("/feedbacks/{id}/detail")
    public Result<Feedback> getFeedbackDetail(
            @PathVariable Long id,
            @RequestParam Long userId) {
        
        Optional<Feedback> feedbackOpt = feedbackRepository.findById(id);
        if (!feedbackOpt.isPresent()) {
            return Result.error("反馈不存在");
        }
        
        Feedback feedback = feedbackOpt.get();
        
        // 权限验证：用户只能查看自己的反馈
        if (!feedback.getUserId().equals(userId)) {
            return Result.error("无权查看该反馈");
        }
        
        enrichFeedback(feedback);
        
        return Result.success(feedback);
    }

    /**
     * 预约规则查看功能
     */

    /**
     * 获取所有当前生效的预约规则
     * @return 预约规则列表
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
     * 根据设施类别获取适用的预约规则
     * @param categoryId 设施类别ID
     * @return 适用的预约规则
     */
    @GetMapping("/rule-configs/category/{categoryId}")
    public Result<RuleConfig> getRuleConfigByCategory(@PathVariable Long categoryId) {
        
        // 先查找该类别是否有特定规则
        Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryId(categoryId);
        if (categoryRuleOpt.isPresent()) {
            RuleConfig rule = categoryRuleOpt.get();
            Optional<FacilityCategory> category = facilityCategoryRepository.findById(categoryId);
            category.ifPresent(value -> rule.setCategoryName(value.getCategoryName()));
            return Result.success(rule);
        }
        
        // 如果没有特定规则，返回全局默认规则
        Optional<RuleConfig> globalRuleOpt = ruleConfigRepository.findByCategoryIdIsNull();
        if (globalRuleOpt.isPresent()) {
            RuleConfig rule = globalRuleOpt.get();
            rule.setCategoryName("全局默认");
            return Result.success(rule);
        }
        
        return Result.error("未找到适用的预约规则");
    }

    /**
     * 获取规则配置的详细描述
     * @param categoryId 设施类别ID
     * @return 规则描述
     */
    @GetMapping("/rule-configs/{categoryId}/description")
    public Result<String> getRuleDescription(@PathVariable Long categoryId) {
        
        // 获取适用的规则配置
        RuleConfig ruleConfig = null;
        Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryId(categoryId);
        if (categoryRuleOpt.isPresent()) {
            ruleConfig = categoryRuleOpt.get();
        } else {
            Optional<RuleConfig> globalRuleOpt = ruleConfigRepository.findByCategoryIdIsNull();
            if (globalRuleOpt.isPresent()) {
                ruleConfig = globalRuleOpt.get();
            }
        }
        
        if (ruleConfig == null) {
            return Result.error("未找到适用的预约规则");
        }
        
        String description = ruleConfigService.getRuleDescription(ruleConfig);
        return Result.success(description);
    }

    /**
     * 辅助方法：丰富违规记录信息
     */
    private void enrichViolationRecords(List<ViolationRecord> violations) {
        for (ViolationRecord violation : violations) {
            enrichViolationRecord(violation);
        }
    }

    private void enrichViolationRecord(ViolationRecord violation) {
        // 设置用户名称
        Optional<User> user = userRepository.findById(violation.getUserId());
        user.ifPresent(value -> violation.setUserName(value.getRealName()));
        
        // 设置上报人名称
        if (violation.getReportedBy() != null) {
            Optional<User> reporter = userRepository.findById(violation.getReportedBy());
            reporter.ifPresent(value -> violation.setReporterName(value.getRealName()));
        }
        
        // 设置上报人名称
            if (violation.getReportedBy() != null) {
                Optional<User> reporter = userRepository.findById(violation.getReportedBy());
                reporter.ifPresent(value -> violation.setReporterName(value.getRealName()));
            }
        
        // 设置设施名称（如果有关联的预约）
        if (violation.getReservationId() != null) {
            Optional<Reservation> reservation = reservationRepository.findById(violation.getReservationId());
            if (reservation.isPresent()) {
                Optional<Facility> facility = facilityRepository.findById(reservation.get().getFacilityId());
                facility.ifPresent(value -> violation.setFacilityName(value.getName()));
            }
        }
    }

    /**
     * 辅助方法：丰富反馈信息
     */
    private void enrichFeedbacks(List<Feedback> feedbacks) {
        for (Feedback feedback : feedbacks) {
            enrichFeedback(feedback);
        }
    }

    private void enrichFeedback(Feedback feedback) {
        // 设置用户名称
        Optional<User> user = userRepository.findById(feedback.getUserId());
        user.ifPresent(value -> feedback.setUserName(value.getRealName()));
        
        // 设置回复人名称
        if (feedback.getReplyBy() != null) {
            Optional<User> replyUser = userRepository.findById(feedback.getReplyBy());
            replyUser.ifPresent(value -> feedback.setReplyByName(value.getRealName()));
        }
    }

    @Autowired
    private FacilityRepository facilityRepository;
}
