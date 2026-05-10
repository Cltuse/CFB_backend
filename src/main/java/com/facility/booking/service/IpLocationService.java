package com.facility.booking.service;

import com.facility.booking.util.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpLocationService {

    private static final String DEFAULT_CITY = "重庆市";
    private static final String LOCAL_REGION_ADDRESS = "本机访问 / 局域网环境，已回退重庆市";
    private static final String DEFAULT_REGION_ADDRESS = "IP归属地暂时无法获取，已回退重庆市";

    public record LocationInfo(String ipAddress, String city, String regionAddress) {}

    @Value("${ip.location.api:http://ip-api.com/json/}")
    private String ipLocationApi;

    private final ConcurrentHashMap<String, IpLocationCache> ipCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class IpLocationCache {
        private final String city;
        private final String regionAddress;
        private final LocalDateTime timestamp;

        private IpLocationCache(String city, String regionAddress) {
            this.city = city;
            this.regionAddress = regionAddress;
            this.timestamp = LocalDateTime.now();
        }

        private boolean isExpired() {
            return LocalDateTime.now().isAfter(timestamp.plusHours(1));
        }
    }

    public String getLocationByIp(String ip) {
        return getLocationInfoByIp(ip).city();
    }

    public LocationInfo getLocationInfoByIp(String ip) {
        String normalizedIp = normalizeIp(ip);

        if (isLocalIp(normalizedIp) || isPrivateIp(normalizedIp)) {
            return new LocationInfo(normalizedIp, DEFAULT_CITY, LOCAL_REGION_ADDRESS);
        }

        IpLocationCache cache = ipCache.get(normalizedIp);
        if (cache != null && !cache.isExpired()) {
            return new LocationInfo(normalizedIp, cache.city, cache.regionAddress);
        }

        LocationInfo locationInfo = tryResolveConfiguredProvider(normalizedIp);
        if (locationInfo == null) {
            locationInfo = tryResolveIpApiProvider(normalizedIp);
        }
        if (locationInfo == null) {
            locationInfo = new LocationInfo(normalizedIp, DEFAULT_CITY, DEFAULT_REGION_ADDRESS);
        }

        ipCache.put(normalizedIp, new IpLocationCache(locationInfo.city(), locationInfo.regionAddress()));
        return locationInfo;
    }

    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
        }
        return normalizeIp(ip);
    }

    private LocationInfo tryResolveConfiguredProvider(String ip) {
        if (ipLocationApi == null || ipLocationApi.isBlank()) {
            return null;
        }

        try {
            String response = HttpUtil.get(buildProviderUrl(ipLocationApi, ip));
            return parseLocationResponse(ip, response);
        } catch (Exception exception) {
            return null;
        }
    }

    private LocationInfo tryResolveIpApiProvider(String ip) {
        try {
            String response = HttpUtil.get("http://ip-api.com/json/" + ip + "?lang=zh-CN");
            return parseLocationResponse(ip, response);
        } catch (Exception exception) {
            return null;
        }
    }

    private LocationInfo parseLocationResponse(String ip, String response) throws Exception {
        String jsonText = extractJsonPayload(response);
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }

        JsonNode jsonNode = objectMapper.readTree(jsonText);

        LocationInfo ipApiLocation = parseIpApiLocation(ip, jsonNode);
        if (ipApiLocation != null) {
            return ipApiLocation;
        }

        return parseDomesticProviderLocation(ip, jsonNode);
    }

    private LocationInfo parseIpApiLocation(String ip, JsonNode jsonNode) {
        if (!"success".equalsIgnoreCase(readText(jsonNode, "status"))) {
            return null;
        }

        String country = readText(jsonNode, "country");
        String regionName = readText(jsonNode, "regionName");
        String city = readText(jsonNode, "city");
        String district = readText(jsonNode, "district");

        String resultCity = hasText(city) ? city : DEFAULT_CITY;
        String regionAddress = joinAddressParts(country, regionName, city, district);
        if (!hasText(regionAddress)) {
            regionAddress = resultCity;
        }

        return new LocationInfo(ip, resultCity, regionAddress);
    }

    private LocationInfo parseDomesticProviderLocation(String ip, JsonNode jsonNode) {
        String city = readText(jsonNode, "city");
        String province = readText(jsonNode, "pro");
        String region = readText(jsonNode, "region");
        String addr = readText(jsonNode, "addr");

        if (!hasText(city) && !hasText(province) && !hasText(region) && !hasText(addr)) {
            return null;
        }

        String resultCity = hasText(city) ? city : (hasText(province) ? province : DEFAULT_CITY);
        String regionAddress = joinAddressParts(province, city, region, addr);
        if (!hasText(regionAddress)) {
            regionAddress = resultCity;
        }

        return new LocationInfo(ip, resultCity, regionAddress);
    }

    private String buildProviderUrl(String baseUrl, String ip) {
        String normalizedBaseUrl = baseUrl.trim();

        if (normalizedBaseUrl.contains("{ip}")) {
            return normalizedBaseUrl.replace("{ip}", ip);
        }
        if (normalizedBaseUrl.contains("ip-api.com/json")) {
            String prefix = normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl : normalizedBaseUrl + "/";
            return prefix + ip + "?lang=zh-CN";
        }
        if (normalizedBaseUrl.contains("pconline.com.cn")) {
            String prefix = normalizedBaseUrl.endsWith("=") || normalizedBaseUrl.endsWith("&")
                    ? normalizedBaseUrl
                    : normalizedBaseUrl + (normalizedBaseUrl.contains("?") ? "&ip=" : "?ip=");
            String requestUrl = prefix + ip;
            return requestUrl.contains("json=true") ? requestUrl : requestUrl + "&json=true";
        }
        if (normalizedBaseUrl.endsWith("/") || normalizedBaseUrl.endsWith("=") || normalizedBaseUrl.endsWith("&")) {
            return normalizedBaseUrl + ip;
        }
        if (normalizedBaseUrl.contains("?")) {
            return normalizedBaseUrl + ip;
        }
        return normalizedBaseUrl + "/" + ip;
    }

    private String extractJsonPayload(String response) {
        if (response == null) {
            return null;
        }

        String trimmed = response.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return trimmed;
        }

        return trimmed.substring(firstBrace, lastBrace + 1);
    }

    private boolean isUnknown(String ip) {
        return ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip);
    }

    private boolean isLocalIp(String ip) {
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    private boolean isPrivateIp(String ip) {
        if (!hasText(ip)) {
            return true;
        }

        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("169.254.")
                || isPrivate172Subnet(ip)
                || ip.startsWith("fc")
                || ip.startsWith("fd")
                || ip.startsWith("fe80:");
    }

    private boolean isPrivate172Subnet(String ip) {
        if (!ip.startsWith("172.")) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        try {
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "127.0.0.1";
        }

        String normalized = ip.trim();
        int commaIndex = normalized.indexOf(',');
        if (commaIndex >= 0) {
            normalized = normalized.substring(0, commaIndex).trim();
        }

        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }

        return normalized.isEmpty() ? "127.0.0.1" : normalized;
    }

    private String readText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return "";
        }

        String value = fieldNode.asText();
        return value == null ? "" : value.trim();
    }

    private String joinAddressParts(String... values) {
        Set<String> uniqueParts = new LinkedHashSet<>();
        for (String value : values) {
            if (hasText(value)) {
                uniqueParts.add(value.trim());
            }
        }

        return String.join(" ", new ArrayList<>(uniqueParts)).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
