package com.example.kindleboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import static com.example.kindleboard.Models.*;

/** 只向固定 Open-Meteo 地址发送天气坐标，失败由调用方保留旧缓存。 */
@Component
public class WeatherClient {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public WeatherClient(ObjectMapper mapper) { this.mapper = mapper; }

    public Weather fetch(Settings settings) throws Exception {
        Location location = settings.weather();
        String query = "https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude()
                + "&longitude=" + location.longitude()
                + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&wind_speed_unit=kmh"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone="
                + URLEncoder.encode(settings.timezone(), StandardCharsets.UTF_8);
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(query))
                .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("天气服务暂不可用");
        JsonNode root = mapper.readTree(response.body());
        JsonNode current = root.path("current"), daily = root.path("daily");
        double temperature = number(current.path("temperature_2m"));
        double humidity = number(current.path("relative_humidity_2m"));
        double code = number(current.path("weather_code"));
        String observedAt = current.path("time").asText("");
        LocalDateTime.parse(observedAt);
        return new Weather(location.cityName(), location.latitude(), location.longitude(), Instant.now(),
                observedAt, temperature, (int) code, number(daily.path("temperature_2m_max").path(0)),
                number(daily.path("temperature_2m_min").path(0)), humidity, false, "天气数据来自 Open-Meteo", number(current.path("wind_speed_10m")));
    }

    /** 缺字段或 null 不能被 asDouble 默认为零，否则会伪造真实天气。 */
    private static double number(JsonNode node) {
        if (!node.isNumber() || !Double.isFinite(node.doubleValue())) throw new IllegalStateException("天气数据不完整");
        return node.doubleValue();
    }

    public static String description(Integer code) {
        if (code == null) return "天气暂不可用";
        return switch (code) {
            case 0 -> "晴";
            case 1, 2 -> "晴间多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "雨";
            case 71, 73, 75, 77, 85, 86 -> "雪";
            case 95, 96, 99 -> "雷雨";
            default -> "天气代码 " + code;
        };
    }
}
