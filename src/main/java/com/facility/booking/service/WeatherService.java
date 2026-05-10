package com.facility.booking.service;

import com.facility.booking.entity.CityCode;
import com.facility.booking.entity.Weather;
import com.facility.booking.repository.CityCodeRepository;
import com.facility.booking.repository.WeatherQuoteRepository;
import com.facility.booking.util.WeatherTypeUtils;
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
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Service
public class WeatherService {

    private static final String DEFAULT_CITY = "合川区";
    private static final String DEFAULT_CITY_CODE = "101040300";
    private static final DateTimeFormatter UPDATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Random RANDOM = new Random();
    private static final Map<String, String> BUILTIN_CITY_CODES = Map.ofEntries(
            Map.entry("合川", "101040300"),
            Map.entry("合川区", "101040300"),
            Map.entry("北京", "101010100"),
            Map.entry("北京市", "101010100"),
            Map.entry("上海", "101020100"),
            Map.entry("上海市", "101020100"),
            Map.entry("广州", "101280101"),
            Map.entry("广州市", "101280101"),
            Map.entry("深圳", "101280601"),
            Map.entry("深圳市", "101280601"),
            Map.entry("杭州", "101210101"),
            Map.entry("杭州市", "101210101")
    );

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
            locationInfo = new IpLocationService.LocationInfo(clientIp, DEFAULT_CITY, "IP归属地暂时无法获取，已回退合川区");
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
            String normalizedWeatherType = WeatherTypeUtils.normalizeWeatherType(rawWeatherType);

            Weather weather = new Weather();
            weather.setCity(extractDisplayCity(root, city));
            weather.setWeatherType(hasText(rawWeatherType) ? rawWeatherType : normalizedWeatherType);
            weather.setTemperature(extractTemperature(dataNode));
            weather.setWeatherIcon(weatherIconService.getWeatherIconPath(hasText(rawWeatherType) ? rawWeatherType : normalizedWeatherType));
            weather.setMoodQuote(resolveMoodQuote(rawWeatherType, normalizedWeatherType));
            weather.setUpdateTime(extractUpdateTime(root));
            return weather;
        } catch (Exception exception) {
            return null;
        }
    }

    private Optional<String> resolveCityCode(String city) {
        for (String candidate : buildCityCandidates(city)) {
            String builtinCode = BUILTIN_CITY_CODES.get(candidate);
            if (hasText(builtinCode)) {
                return Optional.of(builtinCode);
            }
        }

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
            candidates.add(removeSuffix(DEFAULT_CITY, "市"));
            return candidates;
        }

        candidates.add(city);
        candidates.add(normalized);
        candidates.add(removeSuffix(normalized, "市"));
        candidates.add(removeSuffix(normalized, "地区"));
        candidates.add(removeSuffix(normalized, "盟"));
        candidates.add(removeSuffix(normalized, "自治州"));
        candidates.add(removeSuffix(normalized, "特别行政区"));
        candidates.add(removeSuffix(normalized, "自治区"));
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

        return WeatherTypeUtils.UNKNOWN;
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
                String numeric = high.replaceAll("[^0-9.-]", "");
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
                    "天气晴朗，适合把今天的学习和预约安排得更从容。",
                    "阳光在线，今天也适合高效推进手头事项。"
            );
            case "多云", "阴" -> List.of(
                    "天气平稳，按计划处理今天的事情会更顺手。",
                    "云层稍厚，但节奏不用乱，照常推进就好。"
            );
            case "小雨", "中雨", "大雨", "暴雨", "阵雨", "雷阵雨", "雷雨", "冻雨" -> List.of(
                    "雨天出行记得预留时间，预约结束后也注意路上安全。",
                    "天气偏湿，今天更适合把安排做细一点。"
            );
            case "小雪", "中雪", "大雪", "暴雪", "阵雪", "雨夹雪" -> List.of(
                    "天气较冷，外出前记得保暖，行程也尽量留出缓冲。",
                    "雪天路滑，按部就班比赶时间更重要。"
            );
            case "雾", "雾霾", "扬沙", "浮尘", "沙尘暴", "强沙尘暴" -> List.of(
                    "能见度一般，今天外出和预约都建议更早一点出发。",
                    "当前天气对出行有影响，安排路线时多留一点余量。"
            );
            case "大风", "台风" -> List.of(
                    "风力较大，外出时注意安全，尽量减少不必要停留。",
                    "天气变化明显，今天的安排更适合预留机动时间。"
            );
            default -> List.of(
                    "天气信息已同步，按节奏推进今天的学习和预约即可。",
                    "当前天气已更新，合理安排时间会更高效。"
            );
        };
    }

    private Weather buildStableFallbackWeather(String city) {
        Weather weather = new Weather();
        weather.setCity(hasText(city) ? city : DEFAULT_CITY);
        weather.setWeatherType(WeatherTypeUtils.UNKNOWN);
        weather.setTemperature("--");
        weather.setWeatherIcon(weatherIconService.getWeatherIconPath(WeatherTypeUtils.UNKNOWN));
        weather.setMoodQuote("天气服务暂时不可用，请稍后刷新重试。");
        weather.setUpdateTime(LocalDateTime.now().format(UPDATE_TIME_FORMATTER));
        return weather;
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
