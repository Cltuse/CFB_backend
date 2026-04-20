package com.facility.booking.controller;

import com.facility.booking.common.Result;
import com.facility.booking.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileUploadService fileUploadService;

    @Value("${file.upload-dir:files}")
    private String uploadDir;

    /**
     * 上传设施图片
     */
    @PostMapping("/upload/facility")
    public Result<String> uploadFacilityImage(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件类型
            if (!fileUploadService.isValidImageFile(file)) {
                return Result.error("只能上传图片文件（jpg, jpeg, png, gif, bmp, webp）");
            }

            // 验证文件大小（10MB）
            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.error("图片大小不能超过10MB");
            }

            String fileUrl = fileUploadService.uploadFile(file, "facility");
            return Result.success("上传成功", fileUrl);
            
        } catch (Exception e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传用户头像
     */
    @PostMapping("/upload/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件类型
            if (!fileUploadService.isValidImageFile(file)) {
                return Result.error("只能上传图片文件");
            }

            // 验证文件大小（5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error("头像大小不能超过5MB");
            }

            String fileUrl = fileUploadService.uploadFile(file, "user");
            return Result.success("上传成功", fileUrl);
            
        } catch (Exception e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public Result<Void> deleteFile(@RequestParam("fileUrl") String fileUrl) {
        try {
            fileUploadService.deleteFile(fileUrl);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }
}