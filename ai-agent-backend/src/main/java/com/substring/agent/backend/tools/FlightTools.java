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
public class FlightTools {

    // SerpApi Google Flights: flight search + booking options (https://serpapi.com/google-flights-api)
    private final RestClient serpApi;
    private final String serpApiKey;

    // AviationStack: live flight status (https://aviationstack.com). Free plan is HTTP only.
    private final RestClient aviationStack;
    private final String aviationStackKey;

    public FlightTools(@Value("${SERPAPI_API_KEY:}") String serpApiKey,
                       @Value("${AVIATIONSTACK_API_KEY:}") String aviationStackKey) {
        this.serpApiKey = serpApiKey;
        this.aviationStackKey = aviationStackKey;
        // Uncached SerpApi lookups can take well over the 10s default read timeout
        this.serpApi = ApiLogging.restClient(Duration.ofSeconds(60)).baseUrl("https://serpapi.com").build();
        this.aviationStack = ApiLogging.restClient(Duration.ofSeconds(60)).baseUrl("http://api.aviationstack.com/v1").build();
    }

    private JsonNode googleFlights(String origin, String destination, String departureDate,
                                   Integer adults, String cabinClass, String bookingToken) {
        // type=2 is one-way; round trips are two one-way searches
        String uri = "/search.json?engine=google_flights&type=2&currency=INR&gl=in&hl=en"
                + "&departure_id={origin}&arrival_id={destination}&outbound_date={date}"
                + "&adults={adults}&travel_class={travelClass}&api_key={key}"
                + (bookingToken == null ? "" : "&booking_token={token}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("origin", origin);
        vars.put("destination", destination);
        vars.put("date", departureDate);
        vars.put("adults", adults == null || adults < 1 ? 1 : adults);
        vars.put("travelClass", travelClass(cabinClass));
        vars.put("key", serpApiKey);
        vars.put("token", bookingToken);
        return serpApi.get()
                .uri(uri, vars)
                .retrieve()
                .body(JsonNode.class);
    }

    // SerpApi travel_class: 1 economy, 2 premium economy, 3 business, 4 first
    static int travelClass(String cabinClass) {
        String c = cabinClass == null ? "" : cabinClass.toLowerCase();
        if (c.contains("premium")) return 2;
        if (c.contains("business")) return 3;
        if (c.contains("first")) return 4;
        return 1;
    }

    // Departure time looks like "2026-11-20 19:00"
    static int departureHour(JsonNode option) {
        String time = option.path("flights").path(0).path("departure_airport").path("time").asString();
        return time.length() >= 13 ? Integer.parseInt(time.substring(11, 13)) : -1;
    }

    //search flight
    @Tool(description = """
            Search one-way flights on Google Flights. Returns the top 5 options with total prices in INR for all passengers,
            a booking token for each, and Google's price insights (whether prices are low, typical or high).
            For a round trip, call this twice: once for the outbound date, then with origin and destination swapped for the return date.
            Use the optional filters for requests like morning (5-11), afternoon (12-16), evening (17-20) or night (21-23) flights,
            nonstop/direct flights, earliest flights or fastest flights.""")
    public String searchFlights(
            @ToolParam(description = "Origin airport IATA code, e.g. DEL") String origin,
            @ToolParam(description = "Destination airport IATA code, e.g. BOM") String destination,
            @ToolParam(description = "Departure date as YYYY-MM-DD") String departureDate,
            @ToolParam(required = false, description = "Number of adult passengers, default 1") Integer adults,
            @ToolParam(required = false, description = "Cabin class: economy (default), premium economy, business or first") String cabinClass,
            @ToolParam(required = false, description = "Sort by: price (default), departure (earliest first) or duration (fastest first)") String sortBy,
            @ToolParam(required = false, description = "Earliest departure hour, 0-23") Integer departAfterHour,
            @ToolParam(required = false, description = "Latest departure hour, 0-23, inclusive") Integer departBeforeHour,
            @ToolParam(required = false, description = "true to show only nonstop flights") Boolean nonstopOnly) {
        if (serpApiKey.isBlank()) return "SERPAPI_API_KEY is not configured";
        try {
            JsonNode res = googleFlights(origin, destination, departureDate, adults, cabinClass, null);
            if (res.has("error")) return "Flight search failed: " + res.path("error").asString();
            List<JsonNode> options = new ArrayList<>();
            res.path("best_flights").forEach(options::add);
            res.path("other_flights").forEach(options::add);
            // Filter and sort locally on the ~35 results Google returns
            options.removeIf(o -> {
                int hour = departureHour(o);
                return (departAfterHour != null && hour < departAfterHour)
                        || (departBeforeHour != null && hour > departBeforeHour)
                        || (Boolean.TRUE.equals(nonstopOnly) && o.path("flights").size() > 1);
            });
            Comparator<JsonNode> order = switch (sortBy == null ? "price" : sortBy.toLowerCase()) {
                case "departure" -> Comparator.comparing(o -> o.path("flights").path(0).path("departure_airport").path("time").asString());
                case "duration" -> Comparator.comparingInt(o -> o.path("total_duration").asInt(Integer.MAX_VALUE));
                default -> Comparator.comparingDouble(o -> o.path("price").asDouble(Double.MAX_VALUE));
            };
            options.sort(order);
            StringBuilder out = new StringBuilder();
            int count = 0;
            // ponytail: top 5 only, page further if users want more
            for (JsonNode option : options) {
                if (count++ == 5) break;
                int stops = option.path("flights").size() - 1;
                out.append("Option ").append(count)
                        .append(" | ").append(option.path("price").asString("?")).append(" INR total")
                        .append(" | ").append(option.path("total_duration").asString()).append(" min")
                        .append(" | ").append(stops == 0 ? "nonstop" : stops + " stop(s)")
                        .append(" | booking token: ").append(option.path("booking_token").asString()).append("\n");
                for (JsonNode seg : option.path("flights")) {
                    out.append("   ").append(seg.path("airline").asString())
                            .append(" ").append(seg.path("flight_number").asString())
                            .append(" ").append(seg.path("departure_airport").path("id").asString())
                            .append(" ").append(seg.path("departure_airport").path("time").asString())
                            .append(" -> ").append(seg.path("arrival_airport").path("id").asString())
                            .append(" ").append(seg.path("arrival_airport").path("time").asString()).append("\n");
                }
            }
            if (count == 0) return "No flights found matching those filters";
            JsonNode insights = res.path("price_insights");
            if (insights.has("price_level")) {
                out.append("Price insights for this route and date: prices are ").append(insights.path("price_level").asString())
                        .append(", lowest ").append(insights.path("lowest_price").asString()).append(" INR")
                        .append(", typical range ").append(insights.path("typical_price_range").path(0).asString())
                        .append("-").append(insights.path("typical_price_range").path(1).asString()).append(" INR\n");
            }
            return out.toString();
        } catch (RestClientException e) {
            return "Flight search failed: " + e.getMessage();
        }
    }

    //flight status
    @Tool(description = "Get the live status of a flight by its IATA flight number, e.g. AI101 or BA117")
    public String getFlightStatus(@ToolParam(description = "IATA flight number, e.g. BA117") String flightNumber) {
        if (aviationStackKey.isBlank()) return "AVIATIONSTACK_API_KEY is not configured";
        try {
            JsonNode res = aviationStack.get()
                    .uri("/flights?access_key={key}&flight_iata={flight}&limit=1", aviationStackKey, flightNumber)
                    .retrieve()
                    .body(JsonNode.class);
            if (res.has("error")) return "Flight status failed: " + res.path("error").path("message").asString();
            JsonNode flight = res.path("data").path(0);
            if (flight.isMissingNode()) return "Flight not found";
            JsonNode dep = flight.path("departure");
            JsonNode arr = flight.path("arrival");
            return flight.path("airline").path("name").asString() + " " + flightNumber
                    + " | status: " + flight.path("flight_status").asString()
                    + "\nDeparture: " + dep.path("airport").asString()
                    + " scheduled " + dep.path("scheduled").asString()
                    + ", estimated " + dep.path("estimated").asString()
                    + ", delay " + dep.path("delay").asString("0") + " min"
                    + ", terminal " + dep.path("terminal").asString("-") + ", gate " + dep.path("gate").asString("-")
                    + "\nArrival: " + arr.path("airport").asString()
                    + " scheduled " + arr.path("scheduled").asString()
                    + ", estimated " + arr.path("estimated").asString()
                    + ", terminal " + arr.path("terminal").asString("-") + ", gate " + arr.path("gate").asString("-");
        } catch (RestClientException e) {
            return "Flight status failed: " + e.getMessage();
        }
    }

    //flight booking
    // No public API lets individuals issue real tickets, so this returns where to book and a link.
    @Tool(description = "Compare the sites selling a flight option from searchFlights (airlines and travel agents), cheapest first, with a booking link for each")
    public String getBookingOptions(
            @ToolParam(description = "Same origin IATA code used in searchFlights") String origin,
            @ToolParam(description = "Same destination IATA code used in searchFlights") String destination,
            @ToolParam(description = "Same departure date used in searchFlights, YYYY-MM-DD") String departureDate,
            @ToolParam(required = false, description = "Same number of adults used in searchFlights") Integer adults,
            @ToolParam(required = false, description = "Same cabin class used in searchFlights") String cabinClass,
            @ToolParam(description = "Booking token of the chosen option from searchFlights") String bookingToken) {
        if (serpApiKey.isBlank()) return "SERPAPI_API_KEY is not configured";
        try {
            JsonNode res = googleFlights(origin, destination, departureDate, adults, cabinClass, bookingToken);
            if (res.has("error")) return "Booking options failed: " + res.path("error").asString();
            List<JsonNode> sellers = new ArrayList<>();
            res.path("booking_options").forEach(o -> sellers.add(o.path("together")));
            sellers.sort(Comparator.comparingDouble(s -> s.path("price").asDouble(Double.MAX_VALUE)));
            StringBuilder out = new StringBuilder();
            for (JsonNode seller : sellers) {
                // Google's redirect to the seller, sent as GET instead of the POST SerpApi describes
                JsonNode request = seller.path("booking_request");
                out.append(seller.path("book_with").asString("?"))
                        .append(" | ").append(seller.path("price").asString("?")).append(" INR total")
                        .append(" | ").append(request.path("url").asString())
                        .append(request.has("post_data") ? "?" + request.path("post_data").asString() : "")
                        .append("\n");
            }
            out.append("All options on Google Flights: ").append(res.path("search_metadata").path("google_flights_url").asString());
            return out.toString();
        } catch (RestClientException e) {
            return "Booking options failed: " + e.getMessage();
        }
    }
}
