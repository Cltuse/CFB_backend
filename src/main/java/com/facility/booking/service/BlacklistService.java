package com.facility.booking.service;

import com.facility.booking.entity.Blacklist;
import com.facility.booking.repository.BlacklistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 黑名单服务类
 * 处理黑名单相关的业务逻辑和定时任务
 */
@Service
public class BlacklistService {

    @Autowired
    private BlacklistRepository blacklistRepository;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 自动处理过期的黑名单记录
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void autoExpireBlacklist() {
        LocalDateTime now = LocalDateTime.now();
        List<Blacklist> expiredList = blacklistRepository.findByStatusAndEndTimeBefore("ACTIVE", now);

        int updatedCount = 0;
        for (Blacklist blacklist : expiredList) {
            blacklist.setStatus("EXPIRED");
            blacklistRepository.save(blacklist);
            updatedCount++;

            operationLogService.saveOperationLog(null, "SYSTEM", "AUTO_EXPIRE_BLACKLIST",
                    blacklist.getUserId(), "AUTO_EXPIRE_BLACKLIST: " + blacklist.getReason(), "127.0.0.1");
        }

        if (updatedCount > 0) {
            System.out.println("自动过期处理完成，共处理 " + updatedCount + " 条黑名单记录");
        }
    }

    /**
     * 检查用户是否在黑名单中
     * 
     * @param userId 用户ID
     * @return true如果在黑名单中，false否则
     */
    public boolean isUserBlacklisted(Long userId) {
        return blacklistRepository.findByUserIdAndStatus(userId, "ACTIVE").isPresent();
    }

    /**
     * 获取用户的有效黑名单记录
     * 
     * @param userId 用户ID
     * @return 黑名单记录，如果不存在则返回null
     */
    public Blacklist getUserActiveBlacklist(Long userId) {
        return blacklistRepository.findByUserIdAndStatus(userId, "ACTIVE").orElse(null);
    }

    /**
     * 添加用户到黑名单
     * 
     * @param userId  用户ID
     * @param reason  原因
     * @param days    天数
     * @param adminId 管理员ID
     */
    @Transactional
    public void addToBlacklist(Long userId, String reason, int days, Long adminId) {
        try {
            if (blacklistRepository.findByUserIdAndStatus(userId, "ACTIVE").isPresent()) {
                return;
            }

            Blacklist blacklist = new Blacklist();
            blacklist.setUserId(userId);
            blacklist.setReason(reason);
            blacklist.setStartTime(LocalDateTime.now());
            blacklist.setEndTime(LocalDateTime.now().plusDays(days));
            blacklist.setStatus("ACTIVE");
            blacklist.setOperatorId(adminId);
            blacklistRepository.save(blacklist);

            System.out.println("用户" + userId + "因" + reason + "被加入黑名单");
        } catch (Exception e) {
            System.err.println("加入黑名单失败：" + e.getMessage());
            throw new RuntimeException("加入黑名单失败：" + e.getMessage());
        }
    }
}
