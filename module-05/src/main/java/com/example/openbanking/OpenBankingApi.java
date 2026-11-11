package com.example.openbanking;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An account information API in the style of the UK Open Banking (OBIE) standard: versioned base path, capitalised
 * {@code Data}/{@code Links}/{@code Meta} envelopes, a FAPI interaction id on every exchange and a bearer token.
 * <p>
 * Rules for every request under {@link #BASE_PATH}:
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} with a token from {@link #VALID_TOKENS}, otherwise 401 with
 *       {@code WWW-Authenticate: Bearer}</li>
 *   <li>an {@code x-fapi-interaction-id} header, otherwise 400; it is echoed in the response</li>
 *   <li>an {@code Accept} header that allows JSON, otherwise 406</li>
 * </ul>
 * Endpoints: {@code GET /accounts}, {@code GET /accounts/{id}}, {@code GET /accounts/{id}/balances}.
 * <p>
 * Under {@code /test-support} (no rules) are endpoints that exist only to exercise client configuration:
 * {@code GET /slow?millis=..}, {@code GET /redirect}, {@code POST /redirect}, {@code /landing} (any method, says which
 * method arrived) and {@code GET /greeting}, which sends UTF-8 text without declaring a charset.
 */
public final class OpenBankingApi implements AutoCloseable {

    public static final String BASE_PATH = "/open-banking/v4.0/aisp";
    public static final List<String> VALID_TOKENS = List.of("test-token", "second-token");

    private static final List<Map<String, Object>> ACCOUNTS = List.of(
            account("22289", "NL91ABNA0417164300", "Personal", "CurrentAccount", "Main account", "1250.50"),
            account("31820", "NL69INGB0123456789", "Personal", "Savings", "Holiday fund", "15000.00"),
            account("40114", "NL20INGB0001234567", "Business", "CurrentAccount", "Bakery", "-42.10"));

    private final ApiServer server = new ApiServer();

    private OpenBankingApi() {
        server.before(this::checkRules)
              .after((request, response) -> {
                  String interactionId = request.header("x-fapi-interaction-id");
                  if (interactionId != null) {
                      response.header("x-fapi-interaction-id", interactionId);
                  }
              });

        String p = BASE_PATH;
        server.get(p + "/accounts", r -> ApiResponse.ok(envelope("Account",
                        ACCOUNTS.stream().map(OpenBankingApi::withoutBalance).toList(), p + "/accounts")))
              .get(p + "/accounts/{id}", r -> ApiResponse.ok(envelope("Account",
                        List.of(withoutBalance(find(r.pathParam("id")))), p + "/accounts/" + r.pathParam("id"))))
              .get(p + "/accounts/{id}/balances", this::balances);

        server.get("/test-support/slow", this::slow)
              .get("/test-support/redirect", r -> ApiResponse.status(302).header("Location", "/test-support/landing"))
              .post("/test-support/redirect", r -> ApiResponse.status(302).header("Location", "/test-support/landing"))
              .get("/test-support/landing", r -> ApiResponse.ok(Map.of("method", r.method())))
              .post("/test-support/landing", r -> ApiResponse.ok(Map.of("method", r.method())))
              .get("/test-support/greeting", r -> ApiResponse.text(200, "text/plain", "Grüße uit Zürich"));
    }

    public static OpenBankingApi start() {
        OpenBankingApi api = new OpenBankingApi();
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

    private Optional<ApiResponse> checkRules(ApiRequest request) {
        if (!request.path().startsWith(BASE_PATH)) {
            return Optional.empty();
        }
        String authorization = request.header("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || !VALID_TOKENS.contains(authorization.substring("Bearer ".length()))) {
            throw new ApiException(401, "Unauthorized", "A valid bearer token is required")
                    .withHeader("WWW-Authenticate", "Bearer realm=\"aisp\"");
        }
        if (request.header("x-fapi-interaction-id") == null) {
            throw ApiException.badRequest("The header x-fapi-interaction-id is required");
        }
        String accept = request.header("Accept");
        if (accept != null && !accept.contains("*/*") && !accept.toLowerCase(Locale.ROOT).contains("json")) {
            throw new ApiException(406, "Not Acceptable", "This API only produces application/json");
        }
        return Optional.empty();
    }

    private ApiResponse balances(ApiRequest request) {
        Map<String, Object> account = find(request.pathParam("id"));
        String amount = (String) account.get("_balance");
        Map<String, Object> balance = Map.of(
                "AccountId", account.get("AccountId"),
                "Amount", Map.of("Amount", amount.replace("-", ""), "Currency", "EUR"),
                "CreditDebitIndicator", amount.startsWith("-") ? "Debit" : "Credit",
                "Type", "InterimAvailable");
        return ApiResponse.ok(envelope("Balance", List.of(balance), request.path()));
    }

    private ApiResponse slow(ApiRequest request) throws InterruptedException {
        long millis = Long.parseLong(Optional.ofNullable(request.query("millis")).orElse("1000"));
        Thread.sleep(millis);
        return ApiResponse.ok(Map.of("sleptMillis", millis));
    }

    private static Map<String, Object> find(String id) {
        return ACCOUNTS.stream().filter(a -> a.get("AccountId").equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Account " + id + " does not exist"));
    }

    private static Map<String, Object> envelope(String kind, List<?> items, String self) {
        return Map.of(
                "Data", Map.of(kind, items),
                "Links", Map.of("Self", self),
                "Meta", Map.of("TotalPages", 1));
    }

    private static Map<String, Object> account(String id, String iban, String type, String subType, String nickname,
                                               String balance) {
        return Map.of(
                "AccountId", id,
                "Currency", "EUR",
                "AccountType", type,
                "AccountSubType", subType,
                "Nickname", nickname,
                "Account", List.of(Map.of("SchemeName", "UK.OBIE.IBAN", "Identification", iban)),
                "_balance", balance);
    }

    private static Map<String, Object> withoutBalance(Map<String, Object> account) {
        return account.entrySet().stream().filter(e -> !e.getKey().startsWith("_"))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
