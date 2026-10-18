package com.substring.agent.backend.tools;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.substring.agent.backend.config.ApiLogging;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

@Component
public class WeatherTools {

    // Open-Meteo: free, no API key (https://open-meteo.com). Forecasts go 16 days ahead.
    private static final int FORECAST_DAYS = 16;
    private final RestClient openMeteo;

    public WeatherTools() {
        this.openMeteo = ApiLogging.restClient(Duration.ofSeconds(30)).build();
    }

    // WMO weather codes used by Open-Meteo
    private static final Map<Integer, String> CONDITIONS = Map.ofEntries(
            Map.entry(0, "clear sky"), Map.entry(1, "mainly clear"), Map.entry(2, "partly cloudy"), Map.entry(3, "overcast"),
            Map.entry(45, "fog"), Map.entry(48, "fog"),
            Map.entry(51, "light drizzle"), Map.entry(53, "drizzle"), Map.entry(55, "heavy drizzle"),
            Map.entry(61, "light rain"), Map.entry(63, "rain"), Map.entry(65, "heavy rain"),
            Map.entry(71, "light snow"), Map.entry(73, "snow"), Map.entry(75, "heavy snow"),
            Map.entry(80, "light showers"), Map.entry(81, "showers"), Map.entry(82, "heavy showers"),
            Map.entry(95, "thunderstorm"), Map.entry(96, "thunderstorm with hail"), Map.entry(99, "thunderstorm with hail"));

    // getForecast
    @Tool(description = """
            Get the daily weather for a place and date range: conditions, min/max temperature and rain.
            Dates up to 16 days ahead get a real forecast; later dates get last year's weather on the same dates as a guide.""")
    public String getWeather(
            @ToolParam(description = "Place name, for display only, e.g. Goa") String placeName,
            @ToolParam(description = "Latitude of the place, e.g. 15.49 for Goa") double latitude,
            @ToolParam(description = "Longitude of the place, e.g. 73.83 for Goa") double longitude,
            @ToolParam(description = "First date as YYYY-MM-DD") String startDate,
            @ToolParam(required = false, description = "Last date as YYYY-MM-DD, defaults to startDate") String endDate) {
        LocalDate start, end;
        try {
            start = LocalDate.parse(startDate);
            end = endDate == null || endDate.isBlank() ? start : LocalDate.parse(endDate);
        } catch (DateTimeParseException e) {
            return "Dates must be YYYY-MM-DD";
        }
        LocalDate today = LocalDate.now();
        if (start.isBefore(today)) return "Start date is in the past";
        if (end.isBefore(start)) return "End date is before start date";

        // ponytail: whole range falls back to last year if any day is beyond the forecast window
        boolean forecast = !end.isAfter(today.plusDays(FORECAST_DAYS - 1));
        String url = forecast
                ? "https://api.open-meteo.com/v1/forecast?daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max"
                : "https://archive-api.open-meteo.com/v1/archive?daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum";
        LocalDate from = forecast ? start : start.minusYears(1);
        LocalDate to = forecast ? end : end.minusYears(1);
        try {
            JsonNode daily = openMeteo.get()
                    .uri(url + "&timezone=auto&latitude={lat}&longitude={lon}&start_date={from}&end_date={to}",
                            latitude, longitude, from, to)
                    .retrieve()
                    .body(JsonNode.class)
                    .path("daily");
            StringBuilder out = new StringBuilder(forecast
                    ? "Forecast for " + placeName + ":\n"
                    : "No forecast yet (only " + FORECAST_DAYS + " days ahead). Weather in " + placeName + " on the same dates last year:\n");
            for (int i = 0; i < daily.path("time").size(); i++) {
                out.append(daily.path("time").path(i).asString())
                        .append(": ").append(CONDITIONS.getOrDefault(daily.path("weather_code").path(i).asInt(), "unknown"))
                        .append(", ").append(daily.path("temperature_2m_min").path(i).asString())
                        .append("-").append(daily.path("temperature_2m_max").path(i).asString()).append("°C")
                        .append(", rain ").append(daily.path("precipitation_sum").path(i).asString()).append(" mm");
                if (forecast) out.append(", rain chance ").append(daily.path("precipitation_probability_max").path(i).asString()).append("%");
                out.append("\n");
            }
            return out.toString();
        } catch (RestClientException e) {
            return "Weather lookup failed: " + e.getMessage();
        }
    }
}
