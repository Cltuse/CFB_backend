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
    
    /**
     * 根据天气类型获取图标文件路径
     */
    public String getWeatherIconPath(String weatherType) {
        String iconFileName = getWeatherIconFileName(weatherType);
        return "/files/weather/" + iconFileName;
    }
    
    /**
     * 根据天气类型获取图标文件名
     */
    public String getWeatherIconFileName(String weatherType) {
        // 图标文件名映射表
        switch (weatherType) {
            case "晴": return "晴.ico";
            case "多云": return "多云.ico";
            case "阴": return "阴.ico";
            case "小雨": return "小雨.ico";
            case "中雨": return "中雨.ico";
            case "大雨": return "大雨.ico";
            case "暴雨": return "暴雨.ico";
            case "雷阵雨": return "雷阵雨.ico";
            case "雷阵雨伴有冰雹": return "冰雹.ico";
            case "阵雨": return "阵雨.ico";
            case "雨夹雪": return "雨夹雪.ico";
            case "冻雨": return "冻雨.ico";
            case "小雪": return "小雪.ico";
            case "中雪": return "中雪.ico";
            case "大雪": return "大雪.ico";
            case "暴雪": return "暴雪.ico";
            case "阵雪": return "阵雪.ico";
            case "雾": return "雾.ico";
            case "霾": return "雾霾.ico";
            case "沙尘暴": return "沙尘暴.ico";
            case "扬沙": return "杨尘.ico";
            case "浮尘": return "浮尘.ico";
            case "强沙尘暴": return "沙尘暴.ico";
            case "大风": return "大风.ico";
            case "台风": return "台风.ico";
            case "冰雹": return "冰雹.ico";
            case "霜": return "霜.ico";
            case "雷雨": return "雷雨.ico";
            case "大暴雨": return "大暴雨.ico";
            case "多云_夜晚": return "多云_夜晚.ico";
            case "晴_夜晚": return "晴_夜晚.ico";
            case "未知": return "未知.ico";
            default: return "晴.ico";
        }
    }
    
    /**
     * 检查图标文件是否存在
     */
    public boolean isIconFileExists(String weatherType) {
        String iconFileName = getWeatherIconFileName(weatherType);
        Path iconFile = FileStoragePathUtils.resolveUploadPath(uploadDir, "weather").resolve(iconFileName);
        return Files.exists(iconFile);
    }

    /**
     * 获取所有支持的天气类型
     */
    public String[] getSupportedWeatherTypes() {
        return new String[]{
            "晴", "多云", "阴", "小雨", "中雨", "大雨", "暴雨", 
            "雷阵雨", "雷阵雨伴有冰雹", "阵雨", "雨夹雪", "冻雨", 
            "小雪", "中雪", "大雪", "暴雪", "阵雪", "雾", "霾", 
            "沙尘暴", "扬沙", "浮尘", "强沙尘暴", "大风", "台风", 
            "冰雹", "霜", "雷雨", "大暴雨", "多云_夜晚", "晴_夜晚", "未知"
        };
    }
}
