package com.facility.booking.service;

import com.facility.booking.util.FileStoragePathUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileUploadService {

    @Value("${file.upload-dir:files}")
    private String uploadDir;

    @Value("${file.base-url:/files}")
    private String baseFileUrl;

    public String uploadFile(MultipartFile file) {
        return uploadFile(file, "");
    }

    public String uploadFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            Path uploadPath = FileStoragePathUtils.resolveUploadPath(uploadDir, subDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String uniqueFilename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + fileExtension;

            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            if (subDir != null && !subDir.isEmpty()) {
                return baseFileUrl + "/" + subDir + "/" + uniqueFilename;
            }
            return baseFileUrl + "/" + uniqueFilename;

        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            Path filePath = FileStoragePathUtils.resolveStoredFile(uploadDir, fileUrl);
            if (filePath != null && Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        if (contentType == null || !contentType.startsWith("image/")) {
            return false;
        }

        if (originalFilename != null && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            return extension.matches("jpg|jpeg|png|gif|bmp|webp");
        }

        return false;
    }
}
