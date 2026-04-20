package com.facility.booking.service;

import com.facility.booking.entity.Feedback;
import com.facility.booking.repository.FeedbackRepository;
import com.facility.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 提交反馈
     */
    public Feedback submitFeedback(Feedback feedback) {
        feedback.setStatus("PENDING");
        feedback.setReply(null);
        feedback.setReplyTime(null);
        feedback.setReplyBy(null);
        return feedbackRepository.save(feedback);
    }

    /**
     * 获取用户的反馈列表
     */
    public Page<Feedback> getUserFeedbacks(Long userId, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }

    /**
     * 根据ID获取反馈详情
     */
    public Optional<Feedback> getFeedbackById(Long id) {
        Optional<Feedback> feedbackOpt = feedbackRepository.findById(id);
        if (feedbackOpt.isPresent()) {
            Feedback feedback = feedbackOpt.get();
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        }
        return feedbackOpt;
    }

    /**
     * 管理员回复反馈
     */
    @Transactional
    public boolean replyFeedback(Long id, String reply, Long adminId) {
        Optional<Feedback> feedbackOpt = feedbackRepository.findById(id);
        if (feedbackOpt.isPresent()) {
            Feedback feedback = feedbackOpt.get();
            feedback.setReply(reply);
            feedback.setReplyBy(adminId);
            feedback.setReplyTime(LocalDateTime.now());
            feedback.setStatus("PROCESSED");
            feedbackRepository.save(feedback);
            return true;
        }
        return false;
    }

    /**
     * 获取待处理的反馈数量
     */
    public Long getPendingFeedbackCount() {
        return feedbackRepository.countByStatus("PENDING");
    }

    /**
     * 搜索用户的反馈
     */
    public Page<Feedback> searchUserFeedbacks(Long userId, String keyword, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByUserIdAndKeyword(userId, keyword, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 管理员获取所有反馈
     */
    public Page<Feedback> getAllFeedbacks(Pageable pageable) {
        System.out.println("Getting all feedbacks with pageable: " + pageable);
        try {
            Page<Feedback> feedbacks = feedbackRepository.findAllByOrderByCreatedAtDesc(pageable);
            System.out.println("Found " + feedbacks.getTotalElements() + " feedbacks");
            // 设置用户名和头像
            feedbacks.forEach(feedback -> {
                userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                    feedback.setUserName(user.getName());
                    feedback.setUserRole(user.getRole());
                    feedback.setUserAvatar(user.getAvatar());
                });
                if (feedback.getReplyBy() != null) {
                    userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                        feedback.setReplyByName(user.getName()));
                }
            });
            return feedbacks;
        } catch (Exception e) {
            System.out.println("Error getting all feedbacks: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 根据状态获取反馈
     */
    public Page<Feedback> getFeedbacksByStatus(String status, Pageable pageable) {
        // 使用JPA的命名查询方法
        Page<Feedback> feedbacks = feedbackRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 根据类型获取反馈
     */
    public Page<Feedback> getFeedbacksByType(String type, Pageable pageable) {
        // 使用JPA的命名查询方法
        Page<Feedback> feedbacks = feedbackRepository.findByTypeOrderByCreatedAtDesc(type, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 根据状态和类型获取反馈
     */
    public Page<Feedback> getFeedbacksByStatusAndType(String status, String type, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByStatusAndTypeOrderByCreatedAtDesc(status, type, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 搜索反馈
     */
    public Page<Feedback> searchFeedbacks(String keyword, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByKeywordOrderByCreatedAtDesc(keyword, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 根据状态和关键词搜索反馈
     */
    public Page<Feedback> searchFeedbacksByStatusAndKeyword(String status, String keyword, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByStatusAndKeywordOrderByCreatedAtDesc(status, keyword, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 根据类型和关键词搜索反馈
     */
    public Page<Feedback> searchFeedbacksByTypeAndKeyword(String type, String keyword, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByTypeAndKeywordOrderByCreatedAtDesc(type, keyword, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 根据状态、类型和关键词搜索反馈
     */
    public Page<Feedback> searchFeedbacksByStatusAndTypeAndKeyword(String status, String type, String keyword, Pageable pageable) {
        Page<Feedback> feedbacks = feedbackRepository.findByStatusAndTypeAndKeywordOrderByCreatedAtDesc(status, type, keyword, pageable);
        // 设置用户名和头像
        feedbacks.forEach(feedback -> {
            userRepository.findById(feedback.getUserId()).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserRole(user.getRole());
                feedback.setUserAvatar(user.getAvatar());
            });
            if (feedback.getReplyBy() != null) {
                userRepository.findById(feedback.getReplyBy()).ifPresent(user -> 
                    feedback.setReplyByName(user.getName()));
            }
        });
        return feedbacks;
    }
    
    /**
     * 删除反馈
     */
    public boolean deleteFeedback(Long id) {
        try {
            feedbackRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
