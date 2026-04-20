package com.facility.booking.util;

public final class StoredFileUrlUtils {

    private StoredFileUrlUtils() {
    }

    public static String normalizeForClient(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }

        String normalizedFileUrl = fileUrl.trim().replace('\\', '/');
        if (normalizedFileUrl.isEmpty()) {
            return null;
        }

        int filesIndex = normalizedFileUrl.indexOf("/files/");
        if (filesIndex >= 0) {
            return normalizedFileUrl.substring(filesIndex);
        }

        if (normalizedFileUrl.startsWith("files/")) {
            return "/" + normalizedFileUrl;
        }

        return normalizedFileUrl;
    }
}
