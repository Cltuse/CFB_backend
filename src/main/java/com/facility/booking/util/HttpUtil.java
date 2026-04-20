package com.facility.booking.util;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

public class HttpUtil {
    
    /**
     * 发送GET请求
     */
    public static String get(String url, Map<String, String> headers) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }
        
        HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        
        return response.getBody();
    }
    
    /**
     * 发送GET请求（无请求头）
     */
    public static String get(String url) {
        return get(url, null);
    }
}