package com.facility.booking.service;

import com.facility.booking.util.FileStoragePathUtils;
import com.facility.booking.util.WeatherTypeUtils;
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
        return switch (WeatherTypeUtils.normalizeWeatherType(weatherType)) {
            case "晴" -> "晴.ico";
            case "多云" -> "多云.ico";
            case "阴" -> "阴.ico";
            case "小雨" -> "小雨.ico";
            case "中雨" -> "中雨.ico";
            case "大雨" -> "大雨.ico";
            case "暴雨" -> "暴雨.ico";
            case "大暴雨" -> "大暴雨.ico";
            case "雷阵雨" -> "雷阵雨.ico";
            case "雷雨" -> "雷雨.ico";
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
                "扬沙", "浮尘", "沙尘暴", "强沙尘暴", "大风", "台风", WeatherTypeUtils.UNKNOWN
        };
    }
}
