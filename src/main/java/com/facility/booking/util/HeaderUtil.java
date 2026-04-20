package com.facility.booking.util;

import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Base64;

@Component
public class HeaderUtil {

    /**
     * 解码Base64编码的HTTP请求头
     * @param encodedHeader 编码后的请求头字符串
     * @return 解码后的原始字符串
     */
    public static String decodeHeader(String encodedHeader) {
        if (encodedHeader == null || encodedHeader.isEmpty()) {
            return null;
        }

        try {
            // Base64解码
            byte[] decodedBytes = Base64.getDecoder().decode(encodedHeader);
            // 转换为字符串
            String decoded = new String(decodedBytes, "UTF-8");
            return decoded;
        } catch (Exception e) {
            // 如果解码失败，返回原始值
            return encodedHeader;
        }
    }
}