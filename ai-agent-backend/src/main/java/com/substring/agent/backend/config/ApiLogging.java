package com.substring.agent.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// RestClient for external APIs: read timeout plus a log line per call (INFO) and the response body (DEBUG)
public final class ApiLogging {

    private static final Logger log = LoggerFactory.getLogger(ApiLogging.class);
    private static final int MAX_BODY_CHARS = 5000;

    private ApiLogging() {}

    public static RestClient.Builder restClient(Duration readTimeout) {
        JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory();
        http.setReadTimeout(readTimeout);
        return RestClient.builder()
                // Buffering lets the interceptor read the body and the caller still parse it
                .requestFactory(new BufferingClientHttpRequestFactory(http))
                .requestInterceptor(ApiLogging::intercept);
    }

    private static ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String url = mask(request.getURI().toString());
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            byte[] bytes = response.getBody().readAllBytes();
            log.info("API {} {} -> {} in {} ms, {} bytes", request.getMethod(), url,
                    response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000, bytes.length);
            if (log.isDebugEnabled()) log.debug("API response body: {}", truncate(new String(bytes, StandardCharsets.UTF_8), MAX_BODY_CHARS));
            return response;
        } catch (IOException e) {
            log.warn("API {} {} failed after {} ms: {}", request.getMethod(), url, (System.nanoTime() - start) / 1_000_000, e.toString());
            throw e;
        }
    }

    // Keys are sent as query parameters; never write them to the logs
    static String mask(String url) {
        return url.replaceAll("(api_key|access_key)=[^&]*", "$1=***");
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "... (" + (s.length() - max) + " more chars)";
    }
}
