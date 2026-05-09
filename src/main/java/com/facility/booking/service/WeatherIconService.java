package com.facility.booking.service;

import com.facility.booking.util.FileStoragePathUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class WeatherIconService {

    @Value("${file.upload-dir:files}")
    private String uploadDir;

    public String getWeatherIconPath(String weatherType) {
        return "/files/weather/" + getWeatherIconFileName(weatherType);
    }

    public String getWeatherIconFileName(String weatherType) {
        return switch (normalizeWeatherType(weatherType)) {
            case "晴" -> "晴.ico";
            case "多云" -> "多云.ico";
            case "阴" -> "阴.ico";
            case "小雨" -> "小雨.ico";
            case "中雨" -> "中雨.ico";
            case "大雨" -> "大雨.ico";
            case "暴雨" -> "暴雨.ico";
            case "大暴雨" -> "大暴雨.ico";
            case "雷阵雨", "雷雨" -> "雷阵雨.ico";
            case "雷阵雨伴有冰雹", "冰雹" -> "冰雹.ico";
            case "阵雨" -> "阵雨.ico";
            case "冻雨" -> "冻雨.ico";
            case "雨夹雪" -> "雨夹雪.ico";
            case "小雪" -> "小雪.ico";
            case "中雪" -> "中雪.ico";
            case "大雪" -> "大雪.ico";
            case "暴雪" -> "暴雪.ico";
            case "阵雪" -> "阵雪.ico";
            case "雾" -> "雾.ico";
            case "雾霾" -> "雾霾.ico";
            case "霜" -> "霜.ico";
            case "扬沙" -> "杨尘.ico";
            case "浮尘" -> "浮尘.ico";
            case "沙尘暴", "强沙尘暴" -> "沙尘暴.ico";
            case "大风" -> "大风.ico";
            case "台风" -> "台风.ico";
            default -> "未知.ico";
        };
    }

    public boolean isIconFileExists(String weatherType) {
        String iconFileName = getWeatherIconFileName(weatherType);
        Path iconFile = FileStoragePathUtils.resolveUploadPath(uploadDir, "weather").resolve(iconFileName);
        return Files.exists(iconFile);
    }

    public String[] getSupportedWeatherTypes() {
        return new String[]{
                "晴", "多云", "阴", "小雨", "中雨", "大雨", "暴雨", "大暴雨",
                "雷阵雨", "雷雨", "雷阵雨伴有冰雹", "冰雹", "阵雨", "冻雨", "雨夹雪",
                "小雪", "中雪", "大雪", "暴雪", "阵雪", "雾", "雾霾", "霜",
                "扬沙", "浮尘", "沙尘暴", "强沙尘暴", "大风", "台风", "未知"
        };
    }

    private String normalizeWeatherType(String weatherType) {
        if (weatherType == null || weatherType.isBlank()) {
            return "未知";
        }

        String value = weatherType.trim().replace(" ", "");

        if (value.contains("雷阵雨伴有冰雹")) {
            return "雷阵雨伴有冰雹";
        }
        if (value.contains("强沙尘暴")) {
            return "强沙尘暴";
        }
        if (value.contains("沙尘暴")) {
            return "沙尘暴";
        }
        if (value.contains("大暴雨")) {
            return "大暴雨";
        }
        if (value.contains("暴雪")) {
            return "暴雪";
        }
        if (value.contains("暴雨")) {
            return "暴雨";
        }
        if (value.contains("雷阵雨")) {
            return "雷阵雨";
        }
        if (value.contains("雷雨")) {
            return "雷雨";
        }
        if (value.contains("雨夹雪")) {
            return "雨夹雪";
        }
        if (value.contains("冻雨")) {
            return "冻雨";
        }
        if (value.contains("阵雪")) {
            return "阵雪";
        }
        if (value.contains("阵雨")) {
            return "阵雨";
        }
        if (value.contains("中雪")) {
            return "中雪";
        }
        if (value.contains("大雪")) {
            return "大雪";
        }
        if (value.contains("小雪")) {
            return "小雪";
        }
        if (value.contains("中雨")) {
            return "中雨";
        }
        if (value.contains("大雨")) {
            return "大雨";
        }
        if (value.contains("小雨")) {
            return "小雨";
        }
        if (value.contains("冰雹")) {
            return "冰雹";
        }
        if (value.contains("雾霾")) {
            return "雾霾";
        }
        if (value.contains("雾")) {
            return "雾";
        }
        if (value.contains("霜")) {
            return "霜";
        }
        if (value.contains("扬沙")) {
            return "扬沙";
        }
        if (value.contains("浮尘")) {
            return "浮尘";
        }
        if (value.contains("大风")) {
            return "大风";
        }
        if (value.contains("台风")) {
            return "台风";
        }
        if (value.contains("多云")) {
            return "多云";
        }
        if (value.contains("阴")) {
            return "阴";
        }
        if (value.contains("晴")) {
            return "晴";
        }

        return "未知";
    }
}
