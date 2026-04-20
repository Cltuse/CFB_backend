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

    @GetMapping("/get")
    public Result<Weather> getWeather(@RequestParam(defaultValue = "北京") String city) {
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