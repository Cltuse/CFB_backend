package com.facility.booking.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStoragePathUtilsTest {

    @Test
    void shouldResolveRelativeUploadDirectoryAgainstWorkingDirectory() throws IOException {
        Path workingDirectory = Files.createTempDirectory("facility-working-dir").toAbsolutePath().normalize();
        Path expected = Files.createDirectories(workingDirectory.resolve("files"));
        String originalUserDir = System.getProperty("user.dir");

        System.setProperty("user.dir", workingDirectory.toString());
        try {
            Path actual = FileStoragePathUtils.resolveUploadRoot("files");
            assertEquals(expected, actual);
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void shouldKeepAbsoluteUploadDirectoryUnchanged() {
        Path absolutePath = Path.of(System.getProperty("java.io.tmpdir"), "facility-files").toAbsolutePath().normalize();

        Path actual = FileStoragePathUtils.resolveUploadRoot(absolutePath.toString());

        assertEquals(absolutePath, actual);
    }

    @Test
    void shouldFallbackToBackendFilesDirectoryWhenRunningFromProjectRoot() throws IOException {
        Path projectRoot = Files.createTempDirectory("facility-project").toAbsolutePath().normalize();
        Path backendFilesPath = Files.createDirectories(projectRoot.resolve("backend").resolve("files"));
        String originalUserDir = System.getProperty("user.dir");

        System.setProperty("user.dir", projectRoot.toString());
        try {
            Path actual = FileStoragePathUtils.resolveUploadRoot("files");
            assertEquals(backendFilesPath, actual);
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void shouldResolveStoredFileInsideUploadRoot() {
        Path uploadRoot = Path.of(System.getProperty("java.io.tmpdir"), "facility-files").toAbsolutePath().normalize();
        Path expected = uploadRoot.resolve("facility/demo.png").normalize();

        Path actual = FileStoragePathUtils.resolveStoredFile(uploadRoot.toString(), "/files/facility/demo.png");

        assertEquals(expected, actual);
    }

    @Test
    void shouldRejectPathTraversalWhenDeletingStoredFile() {
        Path uploadRoot = Path.of(System.getProperty("java.io.tmpdir"), "facility-files").toAbsolutePath().normalize();

        assertThrows(IllegalArgumentException.class,
                () -> FileStoragePathUtils.resolveStoredFile(uploadRoot.toString(), "/files/../secret.txt"));
    }
}
