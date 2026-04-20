package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.FacilityCategory;
import com.facility.booking.entity.RuleConfig;
import com.facility.booking.repository.FacilityCategoryRepository;
import com.facility.booking.repository.RuleConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 规则配置控制器
 * 提供预约规则的配置管理功能
 */
@RestController
@RequestMapping("/api/rule-config")
public class RuleConfigController {

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Autowired
    private FacilityCategoryRepository facilityCategoryRepository;

    /**
     * 获取所有规则配置
     * @return 规则配置列表
     */
    @GetMapping("/list")
    public Result<List<RuleConfig>> list() {
        List<RuleConfig> rules = ruleConfigRepository.findAllByOrderByCreatedAtDesc();
        enrichRuleConfigs(rules);
        return Result.success(rules);
    }

    /**
     * 获取当前生效的所有规则
     * @return 当前生效的规则配置列表
     */
    @GetMapping("/current")
    public Result<List<RuleConfig>> getCurrentRules() {
        List<RuleConfig> rules = ruleConfigRepository.findAllCurrentRules();
        enrichRuleConfigs(rules);
        return Result.success(rules);
    }

    /**
     * 根据ID获取规则配置
     * @param id 规则配置ID
     * @return 规则配置信息
     */
    @GetMapping("/{id}")
    public Result<RuleConfig> getById(@PathVariable Long id) {
        Optional<RuleConfig> ruleOpt = ruleConfigRepository.findById(id);
        if (ruleOpt.isPresent()) {
            RuleConfig rule = ruleOpt.get();
            enrichRuleConfig(rule);
            return Result.success(rule);
        }
        return Result.error("规则配置不存在");
    }

    /**
     * 根据设施类别ID获取规则配置
     * @param categoryId 设施类别ID
     * @return 该类别的规则配置
     */
    @GetMapping("/category/{categoryId}")
    public Result<RuleConfig> getByCategoryId(@PathVariable Long categoryId) {
        Optional<RuleConfig> ruleOpt = ruleConfigRepository.findByCategoryId(categoryId);
        if (ruleOpt.isPresent()) {
            RuleConfig rule = ruleOpt.get();
            enrichRuleConfig(rule);
            return Result.success(rule);
        }
        return Result.error("该类别暂无规则配置");
    }

    /**
     * 获取全局默认规则
     * @return 全局默认规则配置
     */
    @GetMapping("/default")
    public Result<RuleConfig> getDefaultRule() {
        Optional<RuleConfig> ruleOpt = ruleConfigRepository.findByCategoryIdIsNull();
        if (ruleOpt.isPresent()) {
            RuleConfig rule = ruleOpt.get();
            enrichRuleConfig(rule);
            return Result.success(rule);
        }
        return Result.error("暂无全局默认规则配置");
    }

    /**
     * 创建或更新规则配置
     * 如果该类别已存在规则，则创建新版本
     * @param ruleConfig 规则配置信息
     * @return 创建或更新的规则配置
     */
    @PostMapping
    @OperationLog(operationType = "UPDATE_RULE", detail = "创建或更新规则配置")
    public Result<RuleConfig> createOrUpdate(@RequestBody RuleConfig ruleConfig) {
        try {
            // 数据验证
            if (ruleConfig.getMinDurationMinutes() != null && ruleConfig.getMaxDurationMinutes() != null) {
                if (ruleConfig.getMinDurationMinutes() > ruleConfig.getMaxDurationMinutes()) {
                    return Result.error("最短预约时长不能大于最长预约时长");
                }
            }

            if (ruleConfig.getOpenTime() != null && ruleConfig.getCloseTime() != null) {
                if (ruleConfig.getOpenTime().isAfter(ruleConfig.getCloseTime())) {
                    return Result.error("开放时间不能晚于关闭时间");
                }
            }

            if (ruleConfig.getCategoryId() != null) {
                // 检查设施类别是否存在
                Optional<FacilityCategory> categoryOpt = facilityCategoryRepository.findById(ruleConfig.getCategoryId());
                if (!categoryOpt.isPresent()) {
                    return Result.error("设施类别不存在");
                }
            }

            // 设置默认值
            if (ruleConfig.getMinDurationMinutes() == null) {
                ruleConfig.setMinDurationMinutes(30);
            }
            if (ruleConfig.getMaxDurationMinutes() == null) {
                ruleConfig.setMaxDurationMinutes(120);
            }
            if (ruleConfig.getAdvanceDaysMax() == null) {
                ruleConfig.setAdvanceDaysMax(7);
            }
            if (ruleConfig.getAdvanceCutoffMinutes() == null) {
                ruleConfig.setAdvanceCutoffMinutes(60);
            }
            if (ruleConfig.getAllowSameDayBooking() == null) {
                ruleConfig.setAllowSameDayBooking(true);
            }
            if (ruleConfig.getMaxBookingsPerDay() == null) {
                ruleConfig.setMaxBookingsPerDay(2);
            }
            if (ruleConfig.getMaxActiveBookings() == null) {
                ruleConfig.setMaxActiveBookings(3);
            }
            if (ruleConfig.getCancelDeadlineMinutes() == null) {
                ruleConfig.setCancelDeadlineMinutes(30);
            }
            if (ruleConfig.getNeedApproval() == null) {
                ruleConfig.setNeedApproval(false);
            }
            if (ruleConfig.getTimeSlotMinutes() == null) {
                ruleConfig.setTimeSlotMinutes(30);
            }
            if (ruleConfig.getOpenTime() == null) {
                ruleConfig.setOpenTime(LocalTime.of(8, 0));
            }
            if (ruleConfig.getCloseTime() == null) {
                ruleConfig.setCloseTime(LocalTime.of(22, 0));
            }

            RuleConfig savedRule = ruleConfigRepository.save(ruleConfig);
            enrichRuleConfig(savedRule);
            
            String message = ruleConfig.getId() == null ? "规则配置创建成功" : "规则配置更新成功";
            return Result.success(message, savedRule);
            
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("规则配置失败：" + e.getMessage());
        }
    }

    /**
     * 删除规则配置
     * @param id 规则配置ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_RULE", detail = "删除规则配置")
    public Result<Void> delete(@PathVariable Long id) {
        if (!ruleConfigRepository.existsById(id)) {
            return Result.error("规则配置不存在");
        }
        ruleConfigRepository.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 获取规则配置摘要信息
     * @return 规则配置摘要
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> getRuleSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // 统计各类别的规则数量
        List<RuleConfig> allRules = ruleConfigRepository.findAllCurrentRules();
        summary.put("totalRules", allRules.size());
        
        // 统计全局规则
        long globalRules = allRules.stream().filter(r -> r.getCategoryId() == null).count();
        summary.put("globalRules", globalRules);
        
        // 统计按类别的规则
        long categoryRules = allRules.stream().filter(r -> r.getCategoryId() != null).count();
        summary.put("categoryRules", categoryRules);
        
        // 统计需要审批的规则
        long approvalRules = allRules.stream().filter(RuleConfig::getNeedApproval).count();
        summary.put("approvalRules", approvalRules);
        
        return Result.success(summary);
    }

    /**
     * 丰富规则配置信息，添加类别名称
     * @param ruleConfigs 规则配置列表
     */
    private void enrichRuleConfigs(List<RuleConfig> ruleConfigs) {
        for (RuleConfig ruleConfig : ruleConfigs) {
            enrichRuleConfig(ruleConfig);
        }
    }

    /**
     * 丰富单个规则配置信息，添加类别名称
     * @param ruleConfig 规则配置
     */
    private void enrichRuleConfig(RuleConfig ruleConfig) {
        if (ruleConfig.getCategoryId() != null) {
            Optional<FacilityCategory> category = facilityCategoryRepository.findById(ruleConfig.getCategoryId());
            if (category.isPresent()) {
                ruleConfig.setCategoryName(category.get().getCategoryName());
            } else {
                ruleConfig.setCategoryName("未知类别");
            }
        } else {
            ruleConfig.setCategoryName("全局默认");
        }
    }
}
