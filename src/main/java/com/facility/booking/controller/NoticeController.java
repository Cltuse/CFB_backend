package com.facility.booking.controller;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.entity.Notice;
import com.facility.booking.repository.NoticeRepository;
import com.facility.booking.util.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 通知公告控制器
 * 提供通知公告的增删改查等功能
 */
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    @Autowired
    private NoticeRepository noticeRepository;


    /**
     * 获取所有通知公告（分页）
     * @return 通知公告列表
     */
    @GetMapping("/list")
    public Result<Page<Notice>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PageUtils.normalizeSize(size), Sort.by(Sort.Direction.DESC, "publishTime"));
        Page<Notice> notices = noticeRepository.findAll(pageRequest);
        return Result.success(notices);
    }

    /**
     * 获取已发布的通知公告（按时间倒序）
     * @return 已发布的通知公告列表
     */
    @GetMapping("/published")
    public Result<List<Notice>> getPublished() {
        List<Notice> notices = noticeRepository.findByStatus("PUBLISHED");
        // 按发布时间倒序排列
        notices.sort((a, b) -> b.getPublishTime().compareTo(a.getPublishTime()));
        return Result.success(notices);
    }
    


    /**
     * 根据ID获取通知公告详情
     * @param id 通知公告ID
     * @return 通知公告详情
     */
    @GetMapping("/{id}")
    public Result<Notice> getById(@PathVariable Long id) {
        Optional<Notice> notice = noticeRepository.findById(id);
        if (notice.isPresent()) {
            return Result.success(notice.get());
        }
        return Result.error("通知不存在");
    }

    /**
     * 创建通知公告
     * @param notice 通知公告信息
     * @return 创建的通知公告信息
     */
    @PostMapping
    @OperationLog(operationType = "PUBLISH_NOTICE", detail = "发布通知")
    public Result<Notice> create(@RequestBody Notice notice) {
        // 处理定时发布
        if ("SCHEDULED".equals(notice.getStatus()) && notice.getScheduledTime() != null) {
            notice.setPublishTime(notice.getScheduledTime());
        } else if ("PUBLISHED".equals(notice.getStatus())) {
            notice.setPublishTime(LocalDateTime.now());
        }
        Notice savedNotice = noticeRepository.save(notice);
        return Result.success("创建成功", savedNotice);
    }

    /**
     * 更新通知公告
     * @param id 通知公告ID
     * @param notice 更新的通知公告信息
     * @return 更新后的通知公告信息
     */
    @PutMapping("/{id}")
    @OperationLog(operationType = "UPDATE_NOTICE", detail = "更新通知")
    public Result<Notice> update(@PathVariable Long id, @RequestBody Notice notice) {
        if (!noticeRepository.existsById(id)) {
            return Result.error("通知不存在");
        }
        notice.setId(id);
        // 处理定时发布
        if ("SCHEDULED".equals(notice.getStatus()) && notice.getScheduledTime() != null) {
            notice.setPublishTime(notice.getScheduledTime());
        } else if ("PUBLISHED".equals(notice.getStatus())) {
            notice.setPublishTime(LocalDateTime.now());
        }
        Notice savedNotice = noticeRepository.save(notice);
        return Result.success("更新成功", savedNotice);
    }

    /**
     * 删除通知公告
     * @param id 通知公告ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @OperationLog(operationType = "DELETE_NOTICE", detail = "删除通知")
    public Result<Void> delete(@PathVariable Long id) {
        if (!noticeRepository.existsById(id)) {
            return Result.error("通知不存在");
        }
        noticeRepository.deleteById(id);
        return Result.success("删除成功", null);
    }

    /**
     * 定时发布任务 - 检查并发布已到时的定时通知
     * @return 发布结果
     */
    @PostMapping("/publish-scheduled")
    @OperationLog(operationType = "PUBLISH_SCHEDULED_NOTICE", detail = "发布定时通知")
    public Result<Integer> publishScheduled() {
        int count = noticeRepository.publishScheduledNotices(LocalDateTime.now());
        return Result.success("发布" + count + "条定时通知", count);
    }
}
