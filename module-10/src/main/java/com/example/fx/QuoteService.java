package com.example.fx;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;

/**
 * Module 10's API under test: a quote service that converts an amount with the exchange rate of an external FX
 * provider. In production the provider is a third party; in the tests WireMock plays it.
 *
 * <h2>Our API</h2>
 * {@code POST /api/v1/quotes} with {@code {"sourceCurrency": "EUR", "targetCurrency": "GBP", "sourceAmount": "100.00"}}
 * answers 201 with the {@code rate} and the {@code targetAmount}, rounded half-even to two decimals.
 *
 * <h2>The call to the provider</h2>
 * {@code GET {provider}/v2/rates?base=EUR&symbols=GBP} with {@code Authorization: Bearer fx-api-key},
 * {@code X-Client-Id: payments-service} and {@code Accept: application/json}. Expected answer:
 * {@code {"base": "EUR", "rates": {"GBP": 0.8523}}}.
 *
 * <h2>When the provider misbehaves</h2>
 * <table>
 *   <caption>Error mapping</caption>
 *   <tr><th>Provider</th><th>Our answer</th></tr>
 *   <tr><td>503</td><td>one retry; if it fails again, 502</td></tr>
 *   <tr><td>other 5xx</td><td>502</td></tr>
 *   <tr><td>429 with {@code Retry-After}</td><td>503 with the same {@code Retry-After}</td></tr>
 *   <tr><td>404 (unknown currency)</td><td>422</td></tr>
 *   <tr><td>no answer within one second</td><td>504</td></tr>
 *   <tr><td>connection failure, malformed JSON or no rate</td><td>502</td></tr>
 * </table>
 * Invalid input is rejected with 422 before the provider is called.
 */
public final class QuoteService implements AutoCloseable {

    public static final String API_KEY = "fx-api-key";
    public static final String CLIENT_ID = "payments-service";
    public static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(1);

    private final ApiServer server = new ApiServer();
    private final String providerUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(PROVIDER_TIMEOUT).build();

    private QuoteService(String providerUrl) {
        this.providerUrl = providerUrl;
        server.post("/api/v1/quotes", this::quote);
    }

    /** Starts the service, calling the FX provider at {@code providerUrl}, such as {@code http://localhost:8089}. */
    public static QuoteService start(String providerUrl) {
        QuoteService service = new QuoteService(providerUrl);
        service.server.start();
        return service;
    }

    public int port() {
        return server.port();
    }

    public String baseUri() {
        return server.baseUri();
    }

    @Override
    public void close() {
        server.close();
        http.close();
    }

    private ApiResponse quote(ApiRequest request) throws InterruptedException {
        Map<String, Object> json = request.jsonObject();
        String source = String.valueOf(json.get("sourceCurrency"));
        String target = String.valueOf(json.get("targetCurrency"));
        String amountText = String.valueOf(json.get("sourceAmount"));
        if (!source.matches("[A-Z]{3}") || !target.matches("[A-Z]{3}") || source.equals(target)
                || !amountText.matches("[0-9]+\\.[0-9]{2}") || new BigDecimal(amountText).signum() <= 0) {
            throw ApiException.validation(java.util.List.of(Map.of("field", "request",
                    "detail", "needs two different ISO 4217 currencies and a positive amount with two decimals")));
        }

        BigDecimal rate = fetchRate(source, target);
        BigDecimal amount = new BigDecimal(amountText);

        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("quoteId", "q-" + UUID.randomUUID().toString().substring(0, 8));
        quote.put("sourceCurrency", source);
        quote.put("targetCurrency", target);
        quote.put("sourceAmount", amount.toPlainString());
        quote.put("rate", rate.toPlainString());
        quote.put("targetAmount", amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        quote.put("expiresAt", Instant.now().plusSeconds(30).toString());
        return ApiResponse.json(201, quote);
    }

    private BigDecimal fetchRate(String source, String target) throws InterruptedException {
        HttpResponse<String> response = callProvider(source, target);
        if (response.statusCode() == 503) {
            response = callProvider(source, target);                 // one retry for a temporary outage
        }
        int status = response.statusCode();
        if (status == 429) {
            ApiException busy = new ApiException(503, "Service Unavailable", "The FX provider is rate limiting us");
            response.headers().firstValue("Retry-After").ifPresent(value -> busy.withHeader("Retry-After", value));
            throw busy;
        }
        if (status == 404) {
            throw ApiException.validation(java.util.List.of(Map.of("field", "currency",
                    "detail", "the FX provider doesn't support " + source + "/" + target)));
        }
        if (status != 200) {
            throw new ApiException(502, "Bad Gateway", "The FX provider failed with status " + status);
        }
        try {
            Map<?, ?> body = server.mapper().readValue(response.body(), Map.class);
            Object rate = body.get("rates") instanceof Map<?, ?> rates ? rates.get(target) : null;
            if (!(rate instanceof Number number)) {
                throw new ApiException(502, "Bad Gateway", "The FX provider sent no rate for " + target);
            }
            return new BigDecimal(number.toString());
        } catch (JacksonException e) {
            throw new ApiException(502, "Bad Gateway", "The FX provider sent an invalid response");
        }
    }

    private HttpResponse<String> callProvider(String source, String target) throws InterruptedException {
        URI uri = URI.create(providerUrl + "/v2/rates?base=" + encode(source) + "&symbols=" + encode(target));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(PROVIDER_TIMEOUT)
                .header("Authorization", "Bearer " + API_KEY)
                .header("X-Client-Id", CLIENT_ID)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ApiException(504, "Gateway Timeout", "The FX provider didn't answer within " + PROVIDER_TIMEOUT);
        } catch (IOException e) {
            throw new ApiException(502, "Bad Gateway", "The FX provider is unreachable: " + e.getClass().getSimpleName());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
