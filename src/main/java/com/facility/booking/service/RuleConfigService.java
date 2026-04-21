package com.facility.booking.service;

import com.facility.booking.common.Result;
import com.facility.booking.entity.Facility;
import com.facility.booking.entity.FacilityCategory;
import com.facility.booking.entity.RuleConfig;
import com.facility.booking.repository.FacilityCategoryRepository;
import com.facility.booking.repository.RuleConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 规则配置服务类
 * 提供规则配置相关的业务逻辑处理
 */
@Service
public class RuleConfigService {

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Autowired
    private FacilityCategoryRepository facilityCategoryRepository;

    /**
     * 获取适用的规则配置
     * 优先使用设施类别的特定规则，如果没有则使用全局默认规则
     * @param facility 设施信息
     * @return 适用的规则配置
     */
    public RuleConfig getApplicableRuleConfig(Facility facility) {
        // 1. 尝试获取设施类别的特定规则
        if (facility.getCategory() != null) {
            Optional<RuleConfig> categoryRuleOpt = ruleConfigRepository.findByCategoryName(facility.getCategory());
            if (categoryRuleOpt.isPresent()) {
                return categoryRuleOpt.get();
            }
        }

        // 2. 如果没有类别特定规则，使用全局默认规则
        Optional<RuleConfig> defaultRuleOpt = ruleConfigRepository.findByCategoryIdIsNull();
        return defaultRuleOpt.orElse(null);
    }

    /**
     * 验证预约时长是否符合规则
     * @param ruleConfig 规则配置
     * @param durationMinutes 预约时长（分钟）
     * @return 验证结果
     */
    public Result<String> validateDuration(RuleConfig ruleConfig, long durationMinutes) {
        if (ruleConfig == null) {
            return Result.success("无时长限制");
        }

        if (ruleConfig.getMinDurationMinutes() != null && durationMinutes < ruleConfig.getMinDurationMinutes()) {
            return Result.error("预约时长不能少于" + ruleConfig.getMinDurationMinutes() + "分钟");
        }

        if (ruleConfig.getMaxDurationMinutes() != null && durationMinutes > ruleConfig.getMaxDurationMinutes()) {
            return Result.error("预约时长不能超过" + ruleConfig.getMaxDurationMinutes() + "分钟");
        }

        return Result.success("时长验证通过");
    }

    /**
     * 验证预约时间是否在开放时间内
     * @param ruleConfig 规则配置
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 验证结果
     */
    public Result<String> validateOperatingHours(RuleConfig ruleConfig, LocalDateTime startTime, LocalDateTime endTime) {
        if (ruleConfig == null || ruleConfig.getOpenTime() == null || ruleConfig.getCloseTime() == null) {
            return Result.success("无开放时间限制");
        }

        LocalTime startLocalTime = startTime.toLocalTime();
        LocalTime endLocalTime = endTime.toLocalTime();
        
        if (startLocalTime.isBefore(ruleConfig.getOpenTime()) || endLocalTime.isAfter(ruleConfig.getCloseTime())) {
            return Result.error("预约时间必须在" + ruleConfig.getOpenTime() + "至" + ruleConfig.getCloseTime() + "之间");
        }

        return Result.success("开放时间验证通过");
    }

    /**
     * 验证提前预约时间是否符合规则
     * @param ruleConfig 规则配置
     * @param startTime 预约开始时间
     * @param currentTime 当前时间
     * @return 验证结果
     */
    public Result<String> validateAdvanceBooking(RuleConfig ruleConfig, LocalDateTime startTime, LocalDateTime currentTime) {
        if (ruleConfig == null) {
            return Result.success("无提前预约限制");
        }

        // 验证提前天数
        if (ruleConfig.getAdvanceDaysMax() != null) {
            LocalDateTime maxAdvanceTime = currentTime.plusDays(ruleConfig.getAdvanceDaysMax());
            if (startTime.isAfter(maxAdvanceTime)) {
                return Result.error("只能提前" + ruleConfig.getAdvanceDaysMax() + "天预约");
            }
        }

        // 验证最短提前预约时间
        if (ruleConfig.getAdvanceCutoffMinutes() != null) {
            LocalDateTime minAdvanceTime = currentTime.plusMinutes(ruleConfig.getAdvanceCutoffMinutes());
            if (startTime.isBefore(minAdvanceTime)) {
                return Result.error("需要提前" + ruleConfig.getAdvanceCutoffMinutes() + "分钟预约");
            }
        }

        // 验证是否允许当日预约
        if (ruleConfig.getAllowSameDayBooking() != null && !ruleConfig.getAllowSameDayBooking()) {
            if (startTime.toLocalDate().equals(currentTime.toLocalDate())) {
                return Result.error("不允许当日预约");
            }
        }

        return Result.success("提前预约验证通过");
    }

    /**
     * 验证用户预约数量限制
     * @param ruleConfig 规则配置
     * @param userId 用户ID
     * @param reservationDate 预约日期
     * @param currentActiveBookings 当前活跃预约数量
     * @return 验证结果
     */
    public Result<String> validateBookingLimits(RuleConfig ruleConfig, Long userId, LocalDate reservationDate, long currentActiveBookings) {
        if (ruleConfig == null) {
            return Result.success("无预约数量限制");
        }

        // 验证每日预约数量限制
        if (ruleConfig.getMaxBookingsPerDay() != null) {
            // 这里应该查询数据库获取用户当日的预约数量
            // 简化处理，实际使用时需要传入当日预约数量
            long dailyBookings = 0; // 需要外部提供
            if (dailyBookings >= ruleConfig.getMaxBookingsPerDay()) {
                return Result.error("当前类别设施当日预约次数已达上限（" + ruleConfig.getMaxBookingsPerDay() + "次），无法进行预约");
            }
        }

        // 验证活跃预约数量限制
        if (ruleConfig.getMaxActiveBookings() != null) {
            if (currentActiveBookings >= ruleConfig.getMaxActiveBookings()) {
                return Result.error("当前类别设施预约数已达上限（" + ruleConfig.getMaxActiveBookings() + "个），无法进行预约");
            }
        }

        return Result.success("预约数量验证通过");
    }

    /**
     * 验证取消预约是否符合规则
     * @param ruleConfig 规则配置
     * @param startTime 预约开始时间
     * @param currentTime 当前时间
     * @return 验证结果
     */
    public Result<String> validateCancellation(RuleConfig ruleConfig, LocalDateTime startTime, LocalDateTime currentTime) {
        if (ruleConfig == null || ruleConfig.getCancelDeadlineMinutes() == null) {
            return Result.success("无取消时间限制");
        }

        LocalDateTime cancelDeadline = startTime.minusMinutes(ruleConfig.getCancelDeadlineMinutes());
        
        if (currentTime.isAfter(cancelDeadline)) {
            return Result.error("预约开始时间前" + ruleConfig.getCancelDeadlineMinutes() + "分钟内不允许取消");
        }

        return Result.success("可以取消");
    }

    /**
     * 获取规则配置的详细描述
     * @param ruleConfig 规则配置
     * @return 规则描述
     */
    public String getRuleDescription(RuleConfig ruleConfig) {
        if (ruleConfig == null) {
            return "无特殊规则限制";
        }

        StringBuilder description = new StringBuilder();
        
        if (ruleConfig.getMinDurationMinutes() != null && ruleConfig.getMaxDurationMinutes() != null) {
            description.append("预约时长：").append(ruleConfig.getMinDurationMinutes()).append("-").append(ruleConfig.getMaxDurationMinutes()).append("分钟；");
        }
        
        if (ruleConfig.getOpenTime() != null && ruleConfig.getCloseTime() != null) {
            description.append("开放时间：").append(ruleConfig.getOpenTime()).append("-").append(ruleConfig.getCloseTime()).append("；");
        }
        
        if (ruleConfig.getAdvanceDaysMax() != null) {
            description.append("可提前").append(ruleConfig.getAdvanceDaysMax()).append("天预约；");
        }
        
        if (ruleConfig.getMaxBookingsPerDay() != null) {
            description.append("每日限").append(ruleConfig.getMaxBookingsPerDay()).append("次；");
        }
        
        if (ruleConfig.getMaxActiveBookings() != null) {
            description.append("最多").append(ruleConfig.getMaxActiveBookings()).append("个活跃预约；");
        }
        
        if (ruleConfig.getNeedApproval() != null && ruleConfig.getNeedApproval()) {
            description.append("需要审批；");
        } else {
            description.append("无需审批；");
        }
        
        return description.toString();
    }
}
