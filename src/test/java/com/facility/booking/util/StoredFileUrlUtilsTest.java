package com.facility.booking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoredFileUrlUtilsTest {

    @Test
    void shouldKeepRelativeFilesPath() {
        assertEquals("/files/facility/demo.png",
                StoredFileUrlUtils.normalizeForClient("/files/facility/demo.png"));
    }

    @Test
    void shouldConvertAbsoluteFilesUrlToRelativePath() {
        assertEquals("/files/facility/demo.png",
                StoredFileUrlUtils.normalizeForClient("http://localhost:5681/files/facility/demo.png"));
    }

    @Test
    void shouldAddLeadingSlashForFilesRelativePath() {
        assertEquals("/files/facility/demo.png",
                StoredFileUrlUtils.normalizeForClient("files/facility/demo.png"));
    }

    @Test
    void shouldReturnNullForBlankValue() {
        assertNull(StoredFileUrlUtils.normalizeForClient("   "));
    }
}
