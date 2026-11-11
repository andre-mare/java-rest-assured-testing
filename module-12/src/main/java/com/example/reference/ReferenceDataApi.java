package com.example.reference;

import com.example.http.ApiException;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A read-only reference data API with a deliberate delay on every request, for Module 12's parallel execution
 * section: with a fixed cost per request, running tests in parallel visibly shortens the run.
 * <ul>
 *   <li>{@code GET /api/v1/currencies} and {@code GET /api/v1/currencies/{code}}</li>
 *   <li>{@code GET /api/v1/holidays/{country}}: TARGET2 and Dutch bank holidays of 2026</li>
 * </ul>
 */
public final class ReferenceDataApi implements AutoCloseable {

    public static final Duration DELAY = Duration.ofMillis(200);

    private static final Map<String, Map<String, Object>> CURRENCIES = Map.of(
            "EUR", Map.of("code", "EUR", "name", "Euro", "minorUnits", 2),
            "GBP", Map.of("code", "GBP", "name", "Pound sterling", "minorUnits", 2),
            "JPY", Map.of("code", "JPY", "name", "Yen", "minorUnits", 0),
            "KWD", Map.of("code", "KWD", "name", "Kuwaiti dinar", "minorUnits", 3));

    private static final Map<String, List<String>> HOLIDAYS = Map.of(
            "EU", List.of("2026-01-01", "2026-04-03", "2026-04-06", "2026-05-01", "2026-12-25", "2026-12-26"),
            "NL", List.of("2026-01-01", "2026-04-03", "2026-04-06", "2026-04-27", "2026-05-05", "2026-05-14",
                    "2026-05-25", "2026-12-25", "2026-12-26"));

    private final ApiServer server = new ApiServer();

    private ReferenceDataApi() {
        server.before(request -> {
                  Thread.sleep(DELAY.toMillis());                     // a slow back end
                  return java.util.Optional.empty();
              })
              .get("/api/v1/currencies", r -> ApiResponse.ok(CURRENCIES.values().stream()
                      .sorted(java.util.Comparator.comparing(c -> (String) c.get("code"))).toList()))
              .get("/api/v1/currencies/{code}", r -> ApiResponse.ok(lookup(CURRENCIES, r.pathParam("code"))))
              .get("/api/v1/holidays/{country}", r -> ApiResponse.ok(Map.of(
                      "country", r.pathParam("country"), "dates", lookup(HOLIDAYS, r.pathParam("country")))));
    }

    public static ReferenceDataApi start() {
        ReferenceDataApi api = new ReferenceDataApi();
        api.server.start();
        return api;
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
    }

    private static <T> T lookup(Map<String, T> values, String key) {
        T value = values.get(key);
        if (value == null) {
            throw ApiException.notFound(key + " is unknown");
        }
        return value;
    }
}
