package com.facility.booking.service;

import com.facility.booking.entity.CityCode;
import com.facility.booking.entity.Weather;
import com.facility.booking.repository.CityCodeRepository;
import com.facility.booking.repository.WeatherQuoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class WeatherService {

    private static final String DEFAULT_CITY = "北京";
    private static final String DEFAULT_CITY_CODE = "101010100";
    private static final DateTimeFormatter UPDATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Random RANDOM = new Random();

    @Value("${weather.api.base-url}")
    private String weatherApiBaseUrl;

    @Autowired
    private CityCodeRepository cityCodeRepository;

    @Autowired
    private IpLocationService ipLocationService;

    @Autowired
    private WeatherQuoteRepository weatherQuoteRepository;

    @Autowired
    private WeatherIconService weatherIconService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Weather getWeatherByCity(String city) {
        String resolvedCity = normalizeCityName(city);

        Weather weather = fetchWeatherFromApi(resolvedCity, resolveCityCode(resolvedCity).orElse(null));
        if (weather != null) {
            return weather;
        }

        if (!DEFAULT_CITY.equals(resolvedCity)) {
            Weather fallbackWeather = fetchWeatherFromApi(DEFAULT_CITY, DEFAULT_CITY_CODE);
            if (fallbackWeather != null) {
                return fallbackWeather;
            }
        }

        return buildStableFallbackWeather(resolvedCity);
    }

    public Weather getWeatherByIp(HttpServletRequest request) {
        String clientIp = ipLocationService.getClientIp(request);
        IpLocationService.LocationInfo locationInfo;

        try {
            locationInfo = ipLocationService.getLocationInfoByIp(clientIp);
        } catch (Exception exception) {
            locationInfo = new IpLocationService.LocationInfo(clientIp, DEFAULT_CITY, "IP 归属地暂时无法获取");
        }

        String resolvedCity = normalizeCityName(locationInfo.city());
        Weather weather = getWeatherByCity(resolvedCity);
        weather.setIpAddress(locationInfo.ipAddress());
        weather.setRegionAddress(locationInfo.regionAddress());

        if (!hasText(weather.getCity())) {
            weather.setCity(resolvedCity);
        }

        return weather;
    }

    private Weather fetchWeatherFromApi(String city, String cityCode) {
        if (!hasText(cityCode)) {
            return null;
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(weatherApiBaseUrl + cityCode, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!"200".equals(root.path("status").asText())) {
                return null;
            }

            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                return null;
            }

            String rawWeatherType = extractWeatherType(dataNode);
            String normalizedWeatherType = normalizeWeatherType(rawWeatherType);

            Weather weather = new Weather();
            weather.setCity(extractDisplayCity(root, city));
            weather.setWeatherType(hasText(rawWeatherType) ? rawWeatherType : normalizedWeatherType);
            weather.setTemperature(extractTemperature(dataNode));
            weather.setWeatherIcon(weatherIconService.getWeatherIconPath(rawWeatherType));
            weather.setMoodQuote(resolveMoodQuote(rawWeatherType, normalizedWeatherType));
            weather.setUpdateTime(extractUpdateTime(root));
            return weather;
        } catch (Exception exception) {
            return null;
        }
    }

    private Optional<String> resolveCityCode(String city) {
        for (String candidate : buildCityCandidates(city)) {
            Optional<CityCode> exactMatch = cityCodeRepository.findByName(candidate);
            if (exactMatch.isPresent()) {
                return Optional.of(exactMatch.get().getCode());
            }
        }

        for (String candidate : buildCityCandidates(city)) {
            Optional<CityCode> fuzzyMatch = cityCodeRepository.findByNameContaining(candidate);
            if (fuzzyMatch.isPresent()) {
                return Optional.of(fuzzyMatch.get().getCode());
            }
        }

        return Optional.empty();
    }

    private Set<String> buildCityCandidates(String city) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeCityName(city);
        if (!hasText(normalized)) {
            candidates.add(DEFAULT_CITY);
            return candidates;
        }

        candidates.add(normalized);
        candidates.add(removeSuffix(normalized, "市"));
        candidates.add(removeSuffix(normalized, "地区"));
        candidates.add(removeSuffix(normalized, "盟"));
        candidates.add(removeSuffix(normalized, "自治州"));
        candidates.add(removeSuffix(normalized, "特别行政区"));
        candidates.add(removeSuffix(normalized, "自治县"));
        candidates.add(removeSuffix(normalized, "县"));
        candidates.add(removeSuffix(normalized, "区"));
        candidates.removeIf(candidate -> !hasText(candidate));
        return candidates;
    }

    private String normalizeCityName(String city) {
        if (!hasText(city)) {
            return DEFAULT_CITY;
        }

        String normalized = city.trim().replace(" ", "");
        normalized = normalized.replace("省", "");
        normalized = normalized.replace("壮族自治区", "");
        normalized = normalized.replace("回族自治区", "");
        normalized = normalized.replace("维吾尔自治区", "");
        normalized = normalized.replace("自治区", "");

        if (normalized.endsWith("特别行政区")) {
            normalized = normalized.substring(0, normalized.length() - "特别行政区".length());
        } else if (normalized.endsWith("自治州")) {
            normalized = normalized.substring(0, normalized.length() - "自治州".length());
        } else if (normalized.endsWith("地区")) {
            normalized = normalized.substring(0, normalized.length() - "地区".length());
        } else if (normalized.endsWith("盟")) {
            normalized = normalized.substring(0, normalized.length() - "盟".length());
        } else if (normalized.endsWith("市")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return hasText(normalized) ? normalized : DEFAULT_CITY;
    }

    private String extractDisplayCity(JsonNode root, String fallbackCity) {
        String cityFromApi = root.path("cityInfo").path("city").asText("");
        if (!hasText(cityFromApi)) {
            return fallbackCity;
        }
        return cityFromApi.trim();
    }

    private String extractWeatherType(JsonNode dataNode) {
        JsonNode forecastNode = dataNode.path("forecast");
        if (forecastNode.isArray() && !forecastNode.isEmpty()) {
            String type = forecastNode.get(0).path("type").asText("");
            if (hasText(type)) {
                return type.trim();
            }
        }

        return "";
    }

    private String extractTemperature(JsonNode dataNode) {
        String currentTemperature = dataNode.path("wendu").asText("");
        if (hasText(currentTemperature)) {
            return currentTemperature.trim() + "℃";
        }

        JsonNode forecastNode = dataNode.path("forecast");
        if (forecastNode.isArray() && !forecastNode.isEmpty()) {
            String high = forecastNode.get(0).path("high").asText("");
            if (hasText(high)) {
                String numeric = high.replaceAll("[^0-9-]", "");
                if (hasText(numeric)) {
                    return numeric + "℃";
                }
            }
        }

        return "--";
    }

    private String extractUpdateTime(JsonNode root) {
        String apiUpdateTime = root.path("cityInfo").path("updateTime").asText("");
        if (hasText(apiUpdateTime)) {
            if (apiUpdateTime.matches("\\d{2}:\\d{2}")) {
                return LocalDate.now() + " " + apiUpdateTime;
            }
            return apiUpdateTime.trim();
        }

        return LocalDateTime.now().format(UPDATE_TIME_FORMATTER);
    }

    private String resolveMoodQuote(String rawWeatherType, String normalizedWeatherType) {
        if (hasText(rawWeatherType)) {
            Optional<com.facility.booking.entity.WeatherQuote> exactQuote =
                    weatherQuoteRepository.findRandomByWeatherType(rawWeatherType.trim());
            if (exactQuote.isPresent() && hasText(exactQuote.get().getContent())) {
                return exactQuote.get().getContent().trim();
            }
        }

        Optional<com.facility.booking.entity.WeatherQuote> normalizedQuote =
                weatherQuoteRepository.findRandomByWeatherType(normalizedWeatherType);
        if (normalizedQuote.isPresent() && hasText(normalizedQuote.get().getContent())) {
            return normalizedQuote.get().getContent().trim();
        }

        List<String> fallbackQuotes = getFallbackQuotes(normalizedWeatherType);
        return fallbackQuotes.get(RANDOM.nextInt(fallbackQuotes.size()));
    }

    private List<String> getFallbackQuotes(String normalizedWeatherType) {
        return switch (normalizedWeatherType) {
            case "晴" -> List.of(
                    "天气晴朗，适合安排今天的学习和预约计划。",
                    "阳光不错，保持节奏，今天的事情会推进得更顺。"
            );
            case "多云", "阴" -> List.of(
                    "云层较多，适合稳稳推进今天的安排。",
                    "天气偏柔和，按计划处理事项会更高效。"
            );
            case "小雨", "中雨", "大雨", "暴雨", "阵雨", "雷阵雨", "雷雨", "冻雨" -> List.of(
                    "出门记得带伞，预约结束后也注意路上安全。",
                    "雨天更适合把安排做细一点，行程会更从容。"
            );
            case "小雪", "中雪", "大雪", "暴雪", "阵雪", "雨夹雪" -> List.of(
                    "天气较冷，外出前注意保暖和出行时间。",
                    "雪天路滑，预约和返程都尽量预留缓冲时间。"
            );
            case "雾", "雾霾", "扬沙", "浮尘", "沙尘暴", "强沙尘暴" -> List.of(
                    "能见度一般，尽量提前出发，避免赶时间。",
                    "当前天气对出行有影响，安排路线时多留一点余量。"
            );
            case "大风", "台风" -> List.of(
                    "风力较大，外出注意安全，尽量减少不必要停留。",
                    "天气变化明显，今天的安排更适合留出机动时间。"
            );
            default -> List.of(
                    "天气信息已更新，按节奏推进今天的学习和预约即可。",
                    "当前天气已同步，合理安排时间会更高效。"
            );
        };
    }

    private Weather buildStableFallbackWeather(String city) {
        Weather weather = new Weather();
        weather.setCity(hasText(city) ? city : DEFAULT_CITY);
        weather.setWeatherType("未知");
        weather.setTemperature("--");
        weather.setWeatherIcon(weatherIconService.getWeatherIconPath("未知"));
        weather.setMoodQuote("天气服务暂时不可用，请稍后刷新重试。");
        weather.setUpdateTime(LocalDateTime.now().format(UPDATE_TIME_FORMATTER));
        return weather;
    }

    private String normalizeWeatherType(String weatherType) {
        if (!hasText(weatherType)) {
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

    private String removeSuffix(String value, String suffix) {
        if (!hasText(value) || !value.endsWith(suffix)) {
            return value;
        }
        return value.substring(0, value.length() - suffix.length());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
