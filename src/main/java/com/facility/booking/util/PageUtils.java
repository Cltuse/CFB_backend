package com.facility.booking.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageUtils {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int normalizedPage = Math.max(page, DEFAULT_PAGE);
        int normalizedSize = normalizeSize(size);
        return PageRequest.of(normalizedPage, normalizedSize, sort);
    }

    public static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
