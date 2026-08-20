package com.love.archive.xpay.support;

import com.love.archive.common.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** 基于 JDK HttpClient 的易支付 form POST 实现。 */
public final class JdkXpayHttpClient implements XpayHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;

    public JdkXpayHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpTextResponse postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "marriage-archive-platform")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpTextResponse(response.statusCode(), response.body(), toHeaders(response));
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_UNAVAILABLE", "支付渠道暂时不可用");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "PAYMENT_CHANNEL_UNAVAILABLE", "支付渠道暂时不可用");
        }
    }

    private static Map<String, String> toHeaders(HttpResponse<?> response) {
        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((key, values) ->
                headers.put(key.toLowerCase(), values.isEmpty() ? "" : values.get(0)));
        return headers;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
