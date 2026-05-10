package com.facility.booking.util;

import java.util.List;

public final class WeatherTypeUtils {

    public static final String UNKNOWN = "未知";

    private static final List<String> ORDERED_TYPES = List.of(
            "雷阵雨伴有冰雹",
            "强沙尘暴",
            "沙尘暴",
            "大暴雨",
            "暴雪",
            "暴雨",
            "雷阵雨",
            "雷雨",
            "雨夹雪",
            "冻雨",
            "阵雪",
            "阵雨",
            "中雪",
            "大雪",
            "小雪",
            "中雨",
            "大雨",
            "小雨",
            "冰雹",
            "雾霾",
            "扬沙",
            "浮尘",
            "大风",
            "台风",
            "多云",
            "阴",
            "晴",
            "雾",
            "霜"
    );

    private WeatherTypeUtils() {}

    public static String normalizeWeatherType(String weatherType) {
        if (weatherType == null || weatherType.isBlank()) {
            return UNKNOWN;
        }

        String value = weatherType.trim().replace(" ", "");
        if (value.contains("霾")) {
            return "雾霾";
        }

        for (String type : ORDERED_TYPES) {
            if (value.contains(type)) {
                return type;
            }
        }

        return UNKNOWN;
    }
}
