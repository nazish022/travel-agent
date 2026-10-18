package com.substring.agent.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiLoggingTest {

    @Test
    void masksApiKeysInUrls() {
        assertEquals("https://serpapi.com/search.json?engine=google_flights&api_key=***&booking_token=abc",
                ApiLogging.mask("https://serpapi.com/search.json?engine=google_flights&api_key=SECRET123&booking_token=abc"));
        assertEquals("http://api.aviationstack.com/v1/flights?access_key=***&flight_iata=AI101",
                ApiLogging.mask("http://api.aviationstack.com/v1/flights?access_key=SECRET&flight_iata=AI101"));
        assertEquals("https://serpapi.com/search.json?q=x&api_key=***",
                ApiLogging.mask("https://serpapi.com/search.json?q=x&api_key=SECRET"));
    }

    @Test
    void truncatesLongText() {
        assertEquals("abc", ApiLogging.truncate("abc", 5));
        assertEquals("abcde... (2 more chars)", ApiLogging.truncate("abcdefg", 5));
    }
}
