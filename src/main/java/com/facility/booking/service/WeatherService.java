package com.facility.booking.service;

import com.facility.booking.entity.CityCode;
import com.facility.booking.entity.Weather;
import com.facility.booking.entity.WeatherQuote;
import com.facility.booking.repository.CityCodeRepository;
import com.facility.booking.repository.WeatherQuoteRepository;
import com.facility.booking.service.WeatherIconService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class WeatherService {

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

    // 默认语录，当数据库中没有对应天气类型的语录时使用
    private static final Map<String, WeatherQuote> DEFAULT_QUOTES = new HashMap<>();

    static {
        DEFAULT_QUOTES.put("晴", new WeatherQuote("晴", "☀️", Arrays.asList(
                "愿你拥有如阳光般明媚的心情，温暖而充满力量。无论遇到什么，都要保持微笑和善良。",
                "阳光是最好的滤镜，愿你的每一天都闪闪发光，充满希望和正能量。",
                "晴空万里，愿你的心情也如这天气一般明朗，前路一片光明。",
                "今天的阳光格外温暖，愿你也被这个世界温柔以待，收获满满的幸福。",
                "阳光正好，微风不燥，愿你享受这美好的时光，活出精彩的人生。"
        )));

        DEFAULT_QUOTES.put("多云", new WeatherQuote("多云", "⛅", Arrays.asList(
                "生活就像多云的天气，有时晴朗，有时阴沉，但总会有阳光穿透云层。",
                "云朵在天空中自由飘荡，愿你的心情也如此轻松自在，无拘无束。",
                "多云的天气也别有一番风味，愿你能在平凡的日子里发现不平凡的美好。",
                "云层遮不住阳光，困难挡不住前进的脚步，愿你勇敢面对一切。",
                "天上的云朵千变万化，愿你的人生也丰富多彩，充满无限可能。"
        )));

        DEFAULT_QUOTES.put("阴", new WeatherQuote("阴", "☁️", Arrays.asList(
                "阴天也有阴天的美，愿你在安静的日子里沉淀自己，积蓄力量。",
                "即使天空阴沉，内心的阳光依然可以照亮前路，愿你保持乐观的心态。",
                "阴天是休息的好时机，愿你放慢脚步，享受片刻的宁静与平和。",
                "乌云终会散去，阳光终会到来，愿你耐心等待美好的明天。",
                "阴天适合思考，愿你在这安静的时刻，找到内心的答案和方向。"
        )));

        DEFAULT_QUOTES.put("小雨", new WeatherQuote("小雨", "🌧️", Arrays.asList(
                "细雨绵绵，愿你的心情如雨后的空气般清新，洗去一切烦恼。",
                "小雨滋润万物，愿你的生活也如春雨般充满生机与希望。",
                "雨滴敲打窗户，是大自然的乐章，愿你在这美妙的旋律中找到内心的平静。",
                "小雨过后，空气格外清新，愿你的心情也如雨后彩虹般绚丽多彩。",
                "雨中的世界别有一番诗意，愿你用温柔的眼光看待这个世界。"
        )));

        DEFAULT_QUOTES.put("中雨", new WeatherQuote("中雨", "🌧️", Arrays.asList(
                "中雨洗涤大地，愿你的心灵也得到净化，重拾清澈与纯真。",
                "雨声淅淅沥沥，是自然的催眠曲，愿你在这声音中找到安宁。",
                "中雨过后，万物复苏，愿你的生活也如雨后春笋般茁壮成长。",
                "雨中漫步，感受大自然的馈赠，愿你珍惜每一个当下。",
                "雨水滋润着大地，愿你的梦想也如种子般在雨中生根发芽。"
        )));

        DEFAULT_QUOTES.put("大雨", new WeatherQuote("大雨", "⛈️", Arrays.asList(
                "大雨过后，天空更加清澈，愿你的心境也如雨后天空般明朗。",
                "狂风暴雨终会过去，阳光终会重现，愿你勇敢面对生活中的风雨。",
                "大雨考验着万物的坚韧，愿你也如雨后的小草般顽强不屈。",
                "雨后的彩虹最美，愿你在经历风雨后，收获更加美好的未来。",
                "大雨洗去了尘埃，愿你的心灵也得到净化，重新出发。"
        )));

        DEFAULT_QUOTES.put("暴雨", new WeatherQuote("暴雨", "⛈️", Arrays.asList(
                "暴雨过后，天空更加湛蓝，愿你的未来也如雨后天空般光明。",
                "狂风暴雨无法阻挡前进的步伐，愿你坚定信念，勇往直前。",
                "暴雨考验着万物的意志，愿你如雨后的花朵般绽放光彩。",
                "雨过天晴，彩虹出现，愿你在经历困难后，迎来更加美好的明天。",
                "暴雨洗去了污垢，愿你的心灵也得到净化，重新开始。"
        )));

        DEFAULT_QUOTES.put("雷阵雨", new WeatherQuote("雷阵雨", "⛈️", Arrays.asList(
                "雷阵雨来得快去得也快，愿你的烦恼也如阵雨般快速消散。",
                "雷声过后是平静，愿你经历风雨后，收获内心的宁静与力量。",
                "雷阵雨虽然猛烈，但时间短暂，愿你的困难也如阵雨般快速解决。",
                "雨后的空气格外清新，愿你的心情也如雨后天空般明朗。",
                "雷阵雨是大自然的洗礼，愿你在经历后变得更加坚强。"
        )));

        DEFAULT_QUOTES.put("小雪", new WeatherQuote("小雪", "🌨️", Arrays.asList(
                "雪花纷飞，愿你的世界也如雪景般纯净美好，充满诗意。",
                "小雪轻柔地飘落，愿你的心情也如此温柔，充满爱与温暖。",
                "雪花是冬天的精灵，愿你的生活也如雪花般美丽动人。",
                "雪后的世界银装素裹，愿你的未来也如雪景般纯洁美好。",
                "小雪虽小，却能装点整个世界，愿你的努力也能收获满满的成果。"
        )));

        DEFAULT_QUOTES.put("中雪", new WeatherQuote("中雪", "❄️", Arrays.asList(
                "中雪纷飞，愿你的心情也如雪花般轻盈自在，无拘无束。",
                "雪后的世界分外妖娆，愿你的生活也如雪景般绚丽多彩。",
                "雪花是冬天的礼物，愿你的每一天都充满惊喜和美好。",
                "雪后的空气格外清新，愿你的心情也如雪后天空般明朗。",
                "中雪虽大，却能让世界变得更加美丽，愿你的努力也能创造奇迹。"
        )));

        DEFAULT_QUOTES.put("大雪", new WeatherQuote("大雪", "❄️", Arrays.asList(
                "大雪纷飞，愿你的世界也如雪景般壮丽，充满无限可能。",
                "雪后的世界银装素裹，愿你的未来也如雪景般纯洁美好。",
                "雪花是冬天的诗篇，愿你的生活也如诗歌般优美动人。",
                "雪后的空气格外清新，愿你的心情也如雪后天空般明朗。",
                "大雪虽大，却能让世界变得更加美丽，愿你的努力也能创造奇迹。"
        )));

        DEFAULT_QUOTES.put("暴雪", new WeatherQuote("暴雪", "❄️", Arrays.asList(
                "暴雪过后，世界焕然一新，愿你的生活也如雪后般充满希望。",
                "狂风暴雪无法阻挡春天的到来，愿你坚定信念，迎接美好的明天。",
                "暴雪考验着万物的坚韧，愿你如雪后的梅花般傲然绽放。",
                "雪后的世界格外美丽，愿你的未来也如雪景般光明灿烂。",
                "暴雪洗去了尘埃，愿你的心灵也得到净化，重新出发。"
        )));

        DEFAULT_QUOTES.put("雾", new WeatherQuote("雾", "🌫️", Arrays.asList(
                "雾气弥漫，愿你的内心依然明亮，找到前进的方向。",
                "雾中的世界朦胧而神秘，愿你在迷茫中找到自己的路。",
                "雾终会散去，阳光终会到来，愿你耐心等待美好的明天。",
                "雾中的风景别有一番韵味，愿你在平凡中发现不平凡的美好。",
                "雾是短暂的，阳光是永恒的，愿你保持乐观，迎接光明。"
        )));

        DEFAULT_QUOTES.put("霾", new WeatherQuote("霾", "😷", Arrays.asList(
                "雾霾天气要注意防护，愿你的身体健康，心情愉悦。",
                "雾霾终会散去，蓝天终会重现，愿你保持乐观的心态。",
                "即使天空灰蒙蒙，内心的阳光依然可以照亮前路。",
                "雾霾天气适合室内活动，愿你享受安静的时光，充实自己。",
                "雾霾过后，空气更加清新，愿你的心情也如雨后天空般明朗。"
        )));

        DEFAULT_QUOTES.put("大风", new WeatherQuote("大风", "💨", Arrays.asList(
                "大风虽然猛烈，但无法阻挡前进的步伐，愿你勇敢面对。",
                "风吹过后，天空更加清澈，愿你的心境也如风后天空般明朗。",
                "风是自由的象征，愿你的心灵也如风般自由自在。",
                "大风考验着万物的坚韧，愿你如风中的小草般顽强不屈。",
                "风过之后，一切归于平静，愿你经历风雨后，收获内心的宁静。"
        )));

        DEFAULT_QUOTES.put("沙尘暴", new WeatherQuote("沙尘暴", "🌪️", Arrays.asList(
                "沙尘暴过后，天空更加清澈，愿你的心境也如风暴后般明朗。",
                "狂风沙尘无法阻挡春天的到来，愿你坚定信念，迎接美好的明天。",
                "沙尘暴考验着万物的坚韧，愿你如沙漠中的仙人掌般顽强。",
                "风暴过后，一切归于平静，愿你经历困难后，收获内心的力量。",
                "沙尘暴洗去了尘埃，愿你的心灵也得到净化，重新出发。"
        )));

        DEFAULT_QUOTES.put("雨夹雪", new WeatherQuote("雨夹雪", "🌨️", Arrays.asList(
                "雨夹雪是冬天的独特风景，愿你的生活也如此丰富多彩。",
                "雨雪交织，愿你的心情也如这天气般充满变化和惊喜。",
                "雨夹雪过后，世界焕然一新，愿你的生活也如雪后般充满希望。",
                "雨雪是自然的馈赠，愿你珍惜每一个当下，享受生活的美好。",
                "雨夹雪虽然复杂，但依然美丽，愿你的生活也如此多姿多彩。"
        )));

        DEFAULT_QUOTES.put("雷阵雨伴有冰雹", new WeatherQuote("雷阵雨伴有冰雹", "⛈️", Arrays.asList(
                "雷雨冰雹虽然猛烈，但终会过去，愿你勇敢面对生活中的挑战。",
                "风暴过后，彩虹出现，愿你在经历困难后，迎来更加美好的明天。",
                "冰雹过后，万物复苏，愿你的生活也如雨后春笋般茁壮成长。",
                "雷雨冰雹是大自然的洗礼，愿你在经历后变得更加坚强。",
                "风暴过后，天空更加湛蓝，愿你的未来也如雨后天空般光明。"
        )));

        DEFAULT_QUOTES.put("冻雨", new WeatherQuote("冻雨", "🌨️", Arrays.asList(
                "冻雨虽然寒冷，但无法冻结内心的温暖，愿你保持善良和爱心。",
                "冻雨过后，世界银装素裹，愿你的未来也如雪景般纯洁美好。",
                "冻雨考验着万物的坚韧，愿你如冬天的梅花般傲然绽放。",
                "冻雨是冬天的独特风景，愿你的生活也如此丰富多彩。",
                "冻雨过后，空气格外清新，愿你的心情也如雪后天空般明朗。"
        )));

        DEFAULT_QUOTES.put("阵雪", new WeatherQuote("阵雪", "🌨️", Arrays.asList(
                "阵雪来得快去得也快，愿你的烦恼也如阵雪般快速消散。",
                "雪花纷飞，愿你的世界也如雪景般纯净美好，充满诗意。",
                "阵雪虽然短暂，但依然美丽，愿你的生活也如此多姿多彩。",
                "雪后的世界银装素裹，愿你的未来也如雪景般纯洁美好。",
                "阵雪是冬天的礼物，愿你的每一天都充满惊喜和美好。"
        )));

        DEFAULT_QUOTES.put("阵雨", new WeatherQuote("阵雨", "🌦️", Arrays.asList(
                "阵雨来得快去得也快，愿你的烦恼也如阵雨般快速消散。",
                "阵雨过后，空气格外清新，愿你的心情也如雨后天空般明朗。",
                "阵雨虽然短暂，但依然美丽，愿你的生活也如此多姿多彩。",
                "雨后的彩虹最美，愿你在经历困难后，收获更加美好的未来。",
                "阵雨是大自然的馈赠，愿你珍惜每一个当下，享受生活的美好。"
        )));

        DEFAULT_QUOTES.put("扬沙", new WeatherQuote("扬沙", "🌪️", Arrays.asList(
                "扬沙天气要注意防护，愿你的身体健康，心情愉悦。",
                "扬沙过后，天空更加清澈，愿你的心境也如风后天空般明朗。",
                "即使天空灰蒙蒙，内心的阳光依然可以照亮前路。",
                "扬沙天气适合室内活动，愿你享受安静的时光，充实自己。",
                "扬沙过后，空气更加清新，愿你的心情也如雨后天空般明朗。"
        )));

        DEFAULT_QUOTES.put("浮尘", new WeatherQuote("浮尘", "🌫️", Arrays.asList(
                "浮尘天气要注意防护，愿你的身体健康，心情愉悦。",
                "浮尘过后，天空更加清澈，愿你的心境也如风后天空般明朗。",
                "即使天空灰蒙蒙，内心的阳光依然可以照亮前路。",
                "浮尘天气适合室内活动，愿你享受安静的时光，充实自己。",
                "浮尘过后，空气更加清新，愿你的心情也如雨后天空般明朗。"
        )));

        DEFAULT_QUOTES.put("强沙尘暴", new WeatherQuote("强沙尘暴", "🌪️", Arrays.asList(
                "强沙尘暴过后，天空更加清澈，愿你的心境也如风暴后般明朗。",
                "狂风沙尘无法阻挡春天的到来，愿你坚定信念，迎接美好的明天。",
                "强沙尘暴考验着万物的坚韧，愿你如沙漠中的仙人掌般顽强。",
                "风暴过后，一切归于平静，愿你经历困难后，收获内心的力量。",
                "强沙尘暴洗去了尘埃，愿你的心灵也得到净化，重新出发。"
        )));
    }

    public Weather getWeatherByCity(String city) {
        try {
            // 首先尝试调用外部天气API
            Weather externalWeather = getWeatherFromExternalAPI(city);
            if (externalWeather != null) {
                return externalWeather;
            }
        } catch (Exception e) {
            System.err.println("外部天气API调用失败，使用模拟数据: " + e.getMessage());
        }
        
        // 如果外部API调用失败，使用模拟数据
        return getMockWeatherData(city);
    }
    
    /**
     * 根据IP地址获取天气信息
     */
    public Weather getWeatherByIp(HttpServletRequest request) {
        try {
            // 获取客户端IP
            String clientIp = ipLocationService.getClientIp(request);
            
            // 根据IP获取城市
            String city = ipLocationService.getLocationByIp(clientIp);
            
            // 获取该城市的天气信息
            return getWeatherByCity(city);
        } catch (Exception e) {
            System.err.println("根据IP获取天气失败，使用默认城市: " + e.getMessage());
            // 失败时返回默认城市天气
            return getWeatherByCity("北京");
        }
    }
    
    private Weather getWeatherFromExternalAPI(String city) {
        // 从数据库查询城市代码
        String cityCode = getCityCodeFromDatabase(city);
        String apiUrl = weatherApiBaseUrl + cityCode;
        
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if ("200".equals(String.valueOf(responseBody.get("status")))) {
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    
                    Weather weather = new Weather();
                    weather.setCity(city);
                    
                    // 解析天气信息
                    String forecast = String.valueOf(data.get("forecast"));
                    String weatherType = parseWeatherType(forecast);
                    weather.setWeatherType(weatherType);
                    weather.setWeatherIcon(getWeatherIcon(weatherType));
                    
                    // 解析温度
                    String temperature = parseTemperature(data);
                    weather.setTemperature(temperature);
                    
                    // 设置心情语录
                    String moodQuote = getRandomQuote(DEFAULT_QUOTES.getOrDefault(weatherType, DEFAULT_QUOTES.get("晴")).getQuotes());
                    weather.setMoodQuote(moodQuote);
                    
                    weather.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    
                    return weather;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("天气API调用异常: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    private Weather getMockWeatherData(String city) {
        Weather weather = new Weather();

        String weatherType = getRandomWeatherType();
        WeatherQuote quote = DEFAULT_QUOTES.getOrDefault(weatherType, DEFAULT_QUOTES.get("晴"));

        String temperature = getRandomTemperature(weatherType);
        String moodQuote = getRandomQuote(quote.getQuotes());

        weather.setWeatherType(quote.getWeatherType());
        weather.setWeatherIcon(quote.getWeatherIcon()); // 使用表情符号作为天气图标
        weather.setTemperature(temperature);
        weather.setMoodQuote(moodQuote);
        weather.setCity(city);
        weather.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return weather;
    }
    
    /**
     * 从数据库查询城市代码
     */
    private String getCityCodeFromDatabase(String city) {
        try {
            // 首先精确匹配
            Optional<CityCode> cityCodeOpt = cityCodeRepository.findByName(city);
            if (cityCodeOpt.isPresent()) {
                return cityCodeOpt.get().getCode();
            }
            
            // 如果精确匹配失败，尝试模糊匹配
            cityCodeOpt = cityCodeRepository.findByNameContaining(city);
            if (cityCodeOpt.isPresent()) {
                return cityCodeOpt.get().getCode();
            }
            
            // 如果都找不到，使用默认的北京代码
            System.err.println("未找到城市代码，使用默认北京代码: " + city);
            return "101010100";
            
        } catch (Exception e) {
            System.err.println("查询城市代码失败，使用默认北京代码: " + e.getMessage());
            return "101010100";
        }
    }
    
    private String parseWeatherType(String forecast) {
        if (forecast.contains("晴")) return "晴";
        if (forecast.contains("多云")) return "多云";
        if (forecast.contains("阴")) return "阴";
        if (forecast.contains("小雨")) return "小雨";
        if (forecast.contains("中雨")) return "中雨";
        if (forecast.contains("大雨")) return "大雨";
        if (forecast.contains("暴雨")) return "暴雨";
        if (forecast.contains("小雪")) return "小雪";
        if (forecast.contains("中雪")) return "中雪";
        if (forecast.contains("大雪")) return "大雪";
        if (forecast.contains("暴雪")) return "暴雪";
        if (forecast.contains("雾")) return "雾";
        if (forecast.contains("霾")) return "霾";
        if (forecast.contains("雷阵雨")) return "雷阵雨";
        if (forecast.contains("阵雨")) return "阵雨";
        if (forecast.contains("阵雪")) return "阵雪";
        return "晴";
    }
    
    private String parseTemperature(Map<String, Object> data) {
        try {
            String highTemp = String.valueOf(data.get("wendu"));
            return highTemp + "℃";
        } catch (Exception e) {
            return getRandomTemperature("晴");
        }
    }
    
    private String getWeatherIcon(String weatherType) {
        // 使用WeatherIconService获取图标路径
        return weatherIconService.getWeatherIconPath(weatherType);
    }
    
    /**
     * 根据天气类型获取对应的图标文件名
     */
    private String getWeatherIconFileName(String weatherType) {
        // 图标文件名映射表（基于您提供的图标列表）
        switch (weatherType) {
            case "晴": return "晴.ico";
            case "多云": return "多云.ico";
            case "阴": return "阴.ico";
            case "小雨": return "小雨.ico";
            case "中雨": return "中雨.ico";
            case "大雨": return "大雨.ico";
            case "暴雨": return "暴雨.ico";
            case "雷阵雨": return "雷阵雨.ico";
            case "雷阵雨伴有冰雹": return "冰雹.ico"; // 使用冰雹图标
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
            case "强沙尘暴": return "沙尘暴.ico"; // 复用沙尘暴图标
            case "大风": return "大风.ico";       // 注意：可能需要创建大风图标
            case "台风": return "台风.ico";
            case "冰雹": return "冰雹.ico";
            case "霜": return "霜.ico";
            case "雷雨": return "雷雨.ico";
            case "大暴雨": return "大暴雨.ico";
            case "多云_夜晚": return "多云_夜晚.ico";
            case "晴_夜晚": return "晴_夜晚.ico";
            case "未知": return "未知.ico";
            default: return "晴.ico"; // 默认图标
        }
    }

    private String getRandomWeatherType() {
        List<String> weatherTypes = new ArrayList<>(DEFAULT_QUOTES.keySet());
        Random random = new Random();
        return weatherTypes.get(random.nextInt(weatherTypes.size()));
    }

    private String getRandomTemperature(String weatherType) {
        Random random = new Random();
        int temp;

        if (weatherType.contains("雪") || weatherType.contains("冻")) {
            temp = random.nextInt(10) - 15;
        } else if (weatherType.contains("雨") || weatherType.contains("雷")) {
            temp = random.nextInt(15) + 5;
        } else if (weatherType.contains("晴")) {
            temp = random.nextInt(15) + 15;
        } else {
            temp = random.nextInt(20) - 5;
        }

        return temp + "℃";
    }

    private String getRandomQuote(List<String> quotes) {
        Random random = new Random();
        return quotes.get(random.nextInt(quotes.size()));
    }

    private static class WeatherQuote {
        private String weatherType;
        private String weatherIcon;
        private List<String> quotes;

        public WeatherQuote(String weatherType, String weatherIcon, List<String> quotes) {
            this.weatherType = weatherType;
            this.weatherIcon = weatherIcon;
            this.quotes = quotes;
        }

        public String getWeatherType() {
            return weatherType;
        }

        public String getWeatherIcon() {
            return weatherIcon;
        }

        public List<String> getQuotes() {
            return quotes;
        }
    }
}