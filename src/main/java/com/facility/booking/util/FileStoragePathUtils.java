package com.facility.booking.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileStoragePathUtils {

    private static final String DEFAULT_UPLOAD_DIR = "files";
    private static final String BACKEND_DIR = "backend";

    private FileStoragePathUtils() {
    }

    public static Path resolveUploadRoot(String uploadDir) {
        String configuredUploadDir = uploadDir == null || uploadDir.trim().isEmpty()
                ? DEFAULT_UPLOAD_DIR
                : uploadDir.trim();

        Path configuredPath = Paths.get(configuredUploadDir);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        Path directPath = workingDirectory.resolve(configuredPath).normalize();
        if (Files.exists(directPath)) {
            return directPath;
        }

        Path backendSourcePath = workingDirectory.resolve(BACKEND_DIR).resolve(configuredPath).normalize();
        if (Files.exists(backendSourcePath)) {
            return backendSourcePath;
        }

        return directPath;
    }

    public static Path resolveUploadPath(String uploadDir, String subDir) {
        Path uploadRoot = resolveUploadRoot(uploadDir);
        if (subDir == null || subDir.trim().isEmpty()) {
            return uploadRoot;
        }

        Path resolvedPath = uploadRoot.resolve(Paths.get(subDir.trim())).normalize();
        if (!resolvedPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid upload sub directory: " + subDir);
        }
        return resolvedPath;
    }

    public static Path resolveStoredFile(String uploadDir, String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return null;
        }

        Path uploadRoot = resolveUploadRoot(uploadDir);
        String relativePath = extractRelativeFilePath(fileUrl);
        Path resolvedPath = uploadRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid file path: " + fileUrl);
        }
        return resolvedPath;
    }

    static String extractRelativeFilePath(String fileUrl) {
        String normalizedFileUrl = fileUrl.trim().replace('\\', '/');
        int filesIndex = normalizedFileUrl.indexOf("/files/");
        if (filesIndex >= 0) {
            normalizedFileUrl = normalizedFileUrl.substring(filesIndex + 7);
        }

        while (normalizedFileUrl.startsWith("/")) {
            normalizedFileUrl = normalizedFileUrl.substring(1);
        }

        return normalizedFileUrl;
    }
}
