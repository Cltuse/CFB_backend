package com.facility.booking.controller;

import com.facility.booking.common.Result;
import com.facility.booking.entity.Weather;
import com.facility.booking.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    /**
     * 根据城市获取天气信息。
     * 如果城市不存在，返回错误结果。
     * 如果获取天气信息失败，返回错误结果。
     */
    @GetMapping("/get")
    public Result<Weather> getWeather(@RequestParam(defaultValue = "重庆市") String city) {
        Weather weather = weatherService.getWeatherByCity(city);
        return Result.success("获取天气信息成功", weather);
    }

    /**
     * 根据IP自动定位获取天气信息
     */
    @GetMapping("/auto")
    public Result<Weather> getWeatherByIp(HttpServletRequest request) {
        Weather weather = weatherService.getWeatherByIp(request);
        return Result.success("自动定位获取天气信息成功", weather);
    }
}
