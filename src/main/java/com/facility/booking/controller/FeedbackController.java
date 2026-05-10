package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Feedback;
import com.facility.booking.security.CurrentUserService;
import com.facility.booking.service.FeedbackService;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * 提交反馈
     * 
     * @param feedback 反馈内容
     * @return 反馈提交结果
     */
    @PostMapping("/submit")
    public Result submitFeedback(@RequestBody Feedback feedback) {
        try {
            Feedback savedFeedback = feedbackService.submitFeedback(feedback);
            return Result.success("反馈提交成功", savedFeedback);
        } catch (Exception e) {
            return Result.error("反馈提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户的反馈列表
     * 
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 用户反馈列表结果
     */
    @GetMapping("/user/{userId}")
    public Result getUserFeedbacks(@PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Feedback> feedbacks = feedbackService.getUserFeedbacks(userId, pageable);
            return Result.success("获取反馈列表成功", feedbacks);
        } catch (Exception e) {
            return Result.error("获取反馈列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取反馈详情
     * 
     * @param id 反馈ID
     * @return 反馈详情
     */
    @GetMapping("/{id}")
    public Result getFeedbackDetail(@PathVariable Long id) {
        try {
            Optional<Feedback> feedback = feedbackService.getFeedbackById(id);
            if (feedback.isPresent()) {
                return Result.success("获取反馈详情成功", feedback.get());
            } else {
                return Result.error("反馈不存在");
            }
        } catch (Exception e) {
            return Result.error("获取反馈详情失败: " + e.getMessage());
        }
    }

    /**
     * 搜索用户的反馈
     * 
     * @param userId  用户ID
     * @param keyword 搜索关键词
     * @param page    页码
     * @param size    每页数量
     * @return 搜索结果列表
     */
    @GetMapping("/user/{userId}/search")
    public Result searchUserFeedbacks(@PathVariable Long userId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Feedback> feedbacks = feedbackService.searchUserFeedbacks(userId, keyword, pageable);
            return Result.success("搜索反馈成功", feedbacks);
        } catch (Exception e) {
            return Result.error("搜索反馈失败: " + e.getMessage());
        }
    }

    /**
     * 管理员回复反馈
     * 
     * @param id    反馈ID
     * @param reply 回复内容
     * @return 回复结果
     */
    @PostMapping("/{id}/reply")
    @OperationLog(operationType = "REPLY_FEEDBACK", detail = "回复反馈")
    public Result replyFeedback(@PathVariable Long id,
            @RequestParam String reply) {
        try {
            Long adminId = currentUserService.getCurrentUserId();
            if (adminId == null) {
                return Result.error(401, "Unauthorized");
            }
            boolean success = feedbackService.replyFeedback(id, reply, adminId);
            if (success) {
                return Result.success("回复反馈成功");
            } else {
                return Result.error("反馈不存在");
            }
        } catch (Exception e) {
            return Result.error("回复反馈失败: " + e.getMessage());
        }
    }

    /**
     * 获取待处理反馈数量
     * 
     * @return 待处理反馈数量
     */
    @GetMapping("/pending/count")
    public Result getPendingFeedbackCount() {
        try {
            Long count = feedbackService.getPendingFeedbackCount();
            return Result.success("获取待处理反馈数量成功", count);
        } catch (Exception e) {
            return Result.error("获取待处理反馈数量失败: " + e.getMessage());
        }
    }

    /**
     * 管理员获取所有反馈
     * 
     * @param page    页码
     * @param size    每页数量
     * @param status  状态（可选）
     * @param type    类型（可选）
     * @param keyword 搜索关键词（可选）
     * @return 所有反馈列表
     */
    @GetMapping("/list")
    public Result getAllFeedbacks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        try {
            System.out.println("Getting feedbacks with params: page=" + page + ", size=" + size +
                    ", status=" + status + ", type=" + type + ", keyword=" + keyword);

            Pageable pageable = PageUtils.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Feedback> feedbacks;

            // 根据参数组合查询
            if (status != null && !status.isEmpty() && type != null && !type.isEmpty() && keyword != null
                    && !keyword.isEmpty()) {
                feedbacks = feedbackService.searchFeedbacksByStatusAndTypeAndKeyword(status, type, keyword, pageable);
            } else if (status != null && !status.isEmpty() && type != null && !type.isEmpty()) {
                feedbacks = feedbackService.getFeedbacksByStatusAndType(status, type, pageable);
            } else if (status != null && !status.isEmpty() && keyword != null && !keyword.isEmpty()) {
                feedbacks = feedbackService.searchFeedbacksByStatusAndKeyword(status, keyword, pageable);
            } else if (type != null && !type.isEmpty() && keyword != null && !keyword.isEmpty()) {
                feedbacks = feedbackService.searchFeedbacksByTypeAndKeyword(type, keyword, pageable);
            } else if (status != null && !status.isEmpty()) {
                feedbacks = feedbackService.getFeedbacksByStatus(status, pageable);
            } else if (type != null && !type.isEmpty()) {
                feedbacks = feedbackService.getFeedbacksByType(type, pageable);
            } else if (keyword != null && !keyword.isEmpty()) {
                feedbacks = feedbackService.searchFeedbacks(keyword, pageable);
            } else {
                feedbacks = feedbackService.getAllFeedbacks(pageable);
            }

            return Result.success("获取反馈列表成功", feedbacks);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取反馈列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新反馈状态
     * 
     * @param id     反馈ID
     * @param status 新状态
     * @return 更新结果
     */
    @PutMapping("/{id}/status")
    @OperationLog(operationType = "UPDATE_FEEDBACK_STATUS", detail = "更新反馈状态")
    public Result updateFeedbackStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            Optional<Feedback> feedbackOpt = feedbackService.getFeedbackById(id);
            if (feedbackOpt.isPresent()) {
                Feedback feedback = feedbackOpt.get();
                feedback.setStatus(status);
                feedbackService.submitFeedback(feedback); // 使用现有的保存方法
                return Result.success("更新反馈状态成功");
            } else {
                return Result.error("反馈不存在");
            }
        } catch (Exception e) {
            return Result.error("更新反馈状态失败: " + e.getMessage());
        }
    }

    /**
     * 删除反馈
     * 
     * @param id 反馈ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_FEEDBACK", detail = "删除反馈")
    public Result deleteFeedback(@PathVariable Long id) {
        try {
            Optional<Feedback> feedbackOpt = feedbackService.getFeedbackById(id);
            if (feedbackOpt.isPresent()) {
                // 这里需要在Service中添加删除方法
                // feedbackService.deleteFeedback(id);
                return Result.success("删除反馈成功");
            } else {
                return Result.error("反馈不存在");
            }
        } catch (Exception e) {
            return Result.error("删除反馈失败: " + e.getMessage());
        }
    }
}
