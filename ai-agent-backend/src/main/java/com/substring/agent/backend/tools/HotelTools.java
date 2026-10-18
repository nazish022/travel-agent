package com.substring.agent.backend.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.substring.agent.backend.config.ApiLogging;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

@Component
public class HotelTools {

    // SerpApi Google Hotels: hotel search + prices per booking site (https://serpapi.com/google-hotels-api)
    private final RestClient serpApi;
    private final String serpApiKey;

    public HotelTools(@Value("${SERPAPI_API_KEY:}") String serpApiKey) {
        this.serpApiKey = serpApiKey;
        // Uncached SerpApi lookups can take well over the 10s default read timeout
        this.serpApi = ApiLogging.restClient(Duration.ofSeconds(60)).baseUrl("https://serpapi.com").build();
    }

    private JsonNode googleHotels(String city, String checkIn, String checkOut, Integer adults, Map<String, Object> extra) {
        StringBuilder uri = new StringBuilder("/search.json?engine=google_hotels&currency=INR&gl=in&hl=en"
                + "&q={q}&check_in_date={checkIn}&check_out_date={checkOut}&adults={adults}&api_key={key}");
        Map<String, Object> vars = new HashMap<>(extra);
        extra.keySet().forEach(k -> uri.append("&").append(k).append("={").append(k).append("}"));
        vars.put("q", "hotels in " + city);
        vars.put("checkIn", checkIn);
        vars.put("checkOut", checkOut);
        vars.put("adults", adults == null || adults < 1 ? 2 : adults);
        vars.put("key", serpApiKey);
        return serpApi.get().uri(uri.toString(), vars).retrieve().body(JsonNode.class);
    }

    // search hotel
    @Tool(description = """
            Search hotels on Google Hotels for a city and dates. Returns the top 5 with price per night and total stay price in INR,
            rating, star class, coordinates and a property token for comparing booking sites.""")
    public String searchHotels(
            @ToolParam(description = "City or area, e.g. Goa or North Goa") String city,
            @ToolParam(description = "Check-in date as YYYY-MM-DD") String checkInDate,
            @ToolParam(description = "Check-out date as YYYY-MM-DD") String checkOutDate,
            @ToolParam(required = false, description = "Number of adults, default 2") Integer adults,
            @ToolParam(required = false, description = "Maximum price per night in INR") Integer maxPricePerNight,
            @ToolParam(required = false, description = "Sort by: price (default), rating or reviews") String sortBy,
            @ToolParam(required = false, description = "Minimum guest rating: 3.5, 4 or 4.5") Double minRating) {
        if (serpApiKey.isBlank()) return "SERPAPI_API_KEY is not configured";
        // SerpApi codes: sort_by 3 lowest price, 8 highest rating, 13 most reviewed; rating 7 = 3.5+, 8 = 4+, 9 = 4.5+
        Map<String, Object> extra = new HashMap<>();
        extra.put("sort_by", switch (sortBy == null ? "price" : sortBy.toLowerCase()) {
            case "rating" -> 8;
            case "reviews" -> 13;
            default -> 3;
        });
        if (maxPricePerNight != null) extra.put("max_price", maxPricePerNight);
        if (minRating != null) extra.put("rating", minRating >= 4.5 ? 9 : minRating >= 4 ? 8 : 7);
        try {
            JsonNode res = googleHotels(city, checkInDate, checkOutDate, adults, extra);
            if (res.has("error")) return "Hotel search failed: " + res.path("error").asString();
            StringBuilder out = new StringBuilder();
            int count = 0;
            // ponytail: first page, top 5 only
            for (JsonNode hotel : res.path("properties")) {
                if (count++ == 5) break;
                out.append("Hotel ").append(count).append(": ").append(hotel.path("name").asString())
                        .append(" | ").append(hotel.path("rate_per_night").path("lowest").asString("?")).append(" per night")
                        .append(" | ").append(hotel.path("total_rate").path("lowest").asString("?")).append(" total stay")
                        .append(" | rating ").append(hotel.path("overall_rating").asString("-"))
                        .append(" (").append(hotel.path("reviews").asString("0")).append(" reviews)")
                        .append(" | ").append(hotel.path("hotel_class").asString("unrated"))
                        .append(" | lat ").append(hotel.path("gps_coordinates").path("latitude").asString())
                        .append(", lon ").append(hotel.path("gps_coordinates").path("longitude").asString())
                        .append(" | property token: ").append(hotel.path("property_token").asString()).append("\n");
            }
            return count == 0 ? "No hotels found matching those filters" : out.toString();
        } catch (RestClientException e) {
            return "Hotel search failed: " + e.getMessage();
        }
    }

    // hotel booking
    // No booking API for individuals, so this returns where to book and links.
    @Tool(description = "Compare the booking sites (Agoda, Booking.com, hotel website...) for a hotel from searchHotels, cheapest first, with a booking link for each")
    public String getHotelBookingOptions(
            @ToolParam(description = "Same city used in searchHotels") String city,
            @ToolParam(description = "Same check-in date used in searchHotels") String checkInDate,
            @ToolParam(description = "Same check-out date used in searchHotels") String checkOutDate,
            @ToolParam(required = false, description = "Same number of adults used in searchHotels") Integer adults,
            @ToolParam(description = "Property token of the chosen hotel from searchHotels") String propertyToken) {
        if (serpApiKey.isBlank()) return "SERPAPI_API_KEY is not configured";
        try {
            JsonNode res = googleHotels(city, checkInDate, checkOutDate, adults, Map.of("property_token", propertyToken));
            if (res.has("error")) return "Hotel booking options failed: " + res.path("error").asString();
            List<JsonNode> sites = new ArrayList<>();
            res.path("prices").forEach(sites::add);
            sites.sort(Comparator.comparingDouble(s -> s.path("rate_per_night").path("extracted_lowest").asDouble(Double.MAX_VALUE)));
            StringBuilder out = new StringBuilder(res.path("name").asString()).append("\n");
            for (JsonNode site : sites) {
                out.append(site.path("source").asString("?"))
                        .append(" | ").append(site.path("rate_per_night").path("lowest").asString("?")).append(" per night")
                        .append(" | ").append(site.path("link").asString()).append("\n");
            }
            if (sites.isEmpty()) out.append("No booking sites listed. ");
            if (res.has("link")) out.append("Hotel website: ").append(res.path("link").asString());
            return out.toString();
        } catch (RestClientException e) {
            return "Hotel booking options failed: " + e.getMessage();
        }
    }
}
