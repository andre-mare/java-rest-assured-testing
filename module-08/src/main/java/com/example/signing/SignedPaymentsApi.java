package com.example.signing;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 8's API: payments that must be signed, in the spirit of the request signing that FAPI and several open
 * banking standards require. See {@link HttpSignatures} for the headers.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Request</th><th>Rules</th></tr>
 *   <tr><td>{@code POST /api/v1/payments}, JSON</td><td>{@code X-Correlation-Id}; signed, covering
 *       {@code @method @path content-digest x-correlation-id}; a correct {@code Content-Digest}</td></tr>
 *   <tr><td>{@code GET /api/v1/payments/{id}}</td><td>{@code X-Correlation-Id}; signed, covering
 *       {@code @method @path x-correlation-id}</td></tr>
 *   <tr><td>{@code GET /api/v1/partner/accounts}</td><td>{@code X-API-Key: } {@value #PARTNER_API_KEY}</td></tr>
 *   <tr><td>{@code GET /api/v1/legacy/fx-rates}</td><td>none; answers JSON with the wrong content type
 *       {@code text/plain} and doesn't echo {@code X-Correlation-Id}, like some old systems do</td></tr>
 * </table>
 * Shared signing keys: {@code tpp-key-1} = {@value #TPP_SECRET}. A signature older than five minutes is rejected.
 * Signature problems give 401 with {@code WWW-Authenticate: Signature}; a missing correlation id gives 400.
 */
public final class SignedPaymentsApi implements AutoCloseable {

    public static final String TPP_KEY_ID = "tpp-key-1";
    public static final String TPP_SECRET = "tpp-shared-secret-change-me";
    public static final String PARTNER_API_KEY = "partner-key-123";

    private static final Map<String, String> SECRETS = Map.of(TPP_KEY_ID, TPP_SECRET);
    private static final Pattern SIGNATURE_INPUT = Pattern.compile(
            "sig1=\\(([^)]*)\\);created=(\\d+);keyid=\"([^\"]+)\"");
    private static final Pattern SIGNATURE = Pattern.compile("sig1=:([A-Za-z0-9+/=]+):");

    private final ApiServer server = new ApiServer();
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1);

    private SignedPaymentsApi() {
        server.before(this::requireCorrelationId)
              .after((request, response) -> {
                  String correlationId = request.header("X-Correlation-Id");
                  if (correlationId != null && !request.path().startsWith("/api/v1/legacy")) {   // legacy echoes nothing
                      response.header("X-Correlation-Id", correlationId);
                  }
              });
        server.post("/api/v1/payments", request -> {
                  verifySignature(request, List.of("@method", "@path", "content-digest", "x-correlation-id"));
                  return createPayment(request);
              })
              .get("/api/v1/payments/{id}", request -> {
                  verifySignature(request, List.of("@method", "@path", "x-correlation-id"));
                  Map<String, Object> payment = payments.get(request.pathParam("id"));
                  if (payment == null) {
                      throw ApiException.notFound("Payment " + request.pathParam("id") + " does not exist");
                  }
                  return ApiResponse.ok(payment).header("X-RateLimit-Remaining", "99");
              })
              .get("/api/v1/partner/accounts", request -> {
                  if (!PARTNER_API_KEY.equals(request.header("X-API-Key"))) {
                      throw new ApiException(401, "Unauthorized", "A valid X-API-Key is required");
                  }
                  return ApiResponse.ok(List.of(Map.of("id", "acc-1001", "iban", "NL91ABNA0417164300")));
              })
              .get("/api/v1/legacy/fx-rates", request -> ApiResponse.text(200, "text/plain",
                      "{\"base\":\"EUR\",\"rates\":{\"GBP\":0.85,\"USD\":1.09,\"SEK\":11.42}}"));
    }

    public static SignedPaymentsApi start() {
        SignedPaymentsApi api = new SignedPaymentsApi();
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

    private Optional<ApiResponse> requireCorrelationId(ApiRequest request) {
        if (!request.path().startsWith("/api/v1/legacy") && request.header("X-Correlation-Id") == null) {
            throw ApiException.badRequest("The header X-Correlation-Id is required");
        }
        return Optional.empty();
    }

    private ApiResponse createPayment(ApiRequest request) {
        Map<String, Object> json = request.jsonObject();
        String id = "pmt-" + ids.getAndIncrement();
        Map<String, Object> payment = new LinkedHashMap<>(json);
        payment.put("paymentId", id);
        payment.put("status", "RCVD");
        payment.put("createdAt", Instant.now().toString());
        payments.put(id, payment);
        return ApiResponse.created("/api/v1/payments/" + id, payment);
    }

    private void verifySignature(ApiRequest request, List<String> requiredComponents) {
        String input = request.header("Signature-Input");
        String signature = request.header("Signature");
        if (input == null || signature == null) {
            throw unauthorized("The request is not signed");
        }
        Matcher in = SIGNATURE_INPUT.matcher(input);
        Matcher sig = SIGNATURE.matcher(signature);
        if (!in.matches() || !sig.matches()) {
            throw unauthorized("Signature-Input or Signature is malformed");
        }
        List<String> components = Arrays.stream(in.group(1).split(" "))
                .map(c -> c.replace("\"", "")).filter(c -> !c.isEmpty()).toList();
        if (!components.containsAll(requiredComponents)) {
            throw unauthorized("The signature must cover " + requiredComponents);
        }
        long created = Long.parseLong(in.group(2));
        if (Math.abs(Instant.now().getEpochSecond() - created) > 300) {
            throw unauthorized("The signature is too old");
        }
        String secret = SECRETS.get(in.group(3));
        if (secret == null) {
            throw unauthorized("Unknown keyid " + in.group(3));
        }
        if (components.contains("content-digest")) {
            String digest = request.header("Content-Digest");
            if (digest == null || !digest.equals(HttpSignatures.contentDigest(request.body()))) {
                throw unauthorized("Content-Digest doesn't match the body");
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String component : components) {
            values.put(component, switch (component) {
                case "@method" -> request.method().toUpperCase(Locale.ROOT);
                case "@path" -> request.exchange().getRequestURI().getRawPath();
                default -> Optional.ofNullable(request.header(component)).orElse("");
            });
        }
        String params = HttpSignatures.signatureParams(components, created, in.group(3));
        String expected = HttpSignatures.hmac(HttpSignatures.signatureBase(components, values, params), secret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                sig.group(1).getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized("The signature is invalid");
        }
    }

    private static ApiException unauthorized(String reason) {
        return new ApiException(401, "Unauthorized", reason)
                .withHeader("WWW-Authenticate", "Signature error=\"" + reason + "\"");
    }
}
