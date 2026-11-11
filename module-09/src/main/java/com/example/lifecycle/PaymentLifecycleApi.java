package com.example.lifecycle;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module 9's API: the full life of a payment, with the HTTP semantics that real payment APIs need.
 * Every request needs {@code Authorization: Bearer test-token}.
 *
 * <table>
 *   <caption>Endpoints (all under {@code /api/v1})</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code POST /payments} with {@code Idempotency-Key}</td><td>201 {@code INITIATED}; the same key and body
 *       again gives the same payment with {@code Idempotent-Replayed: true}; the same key with another body gives
 *       422. Field errors give 422 with {@code errors}</td></tr>
 *   <tr><td>{@code GET /payments/{id}}</td><td>200 with an {@code ETag}; 304 for a matching
 *       {@code If-None-Match}</td></tr>
 *   <tr><td>{@code PATCH /payments/{id}}, merge patch of {@code reference}/{@code executionDate}</td><td>needs
 *       {@code If-Match}: 428 without it, 412 when stale, 409 once authorised</td></tr>
 *   <tr><td>{@code POST /payments/{id}/authorisation}</td><td>200 {@code AUTHORISED}, or 409</td></tr>
 *   <tr><td>{@code POST /payments/{id}/execution}</td><td>202 {@code EXECUTING} with {@code Location} and
 *       {@code Retry-After}; about half a second later {@code EXECUTED}, or {@code REJECTED} with reason
 *       {@code INSUFFICIENT_FUNDS} for amounts above 5000.00</td></tr>
 *   <tr><td>{@code GET /payments?limit=..&cursor=..}</td><td>200 {@code items}, oldest first, with a
 *       {@code Link: <..>; rel="next"} header while there are more</td></tr>
 * </table>
 * Five payments exist at start-up.
 */
public final class PaymentLifecycleApi implements AutoCloseable {

    public static final String BASE_PATH = "/api/v1";
    public static final String TOKEN = "test-token";
    public static final Set<String> CURRENCIES = Set.of("EUR", "GBP", "USD");
    public static final BigDecimal SINGLE_PAYMENT_LIMIT = new BigDecimal("100000.00");

    private static final BigDecimal FUNDS = new BigDecimal("5000.00");

    private final ApiServer server = new ApiServer();
    private final Map<String, Payment> payments = new LinkedHashMap<>();
    private final Map<String, Idempotent> idempotencyKeys = new LinkedHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ScheduledExecutorService bank = Executors.newSingleThreadScheduledExecutor();

    /** A payment and its version, which changes on every update and becomes the ETag. */
    private static final class Payment {
        final String id;
        final Map<String, Object> request;
        String status = "INITIATED";
        String reference;
        String executionDate;
        String rejectionReason;
        int version = 1;
        final List<Map<String, String>> history = new ArrayList<>();

        Payment(String id, Map<String, Object> request) {
            this.id = id;
            this.request = request;
            this.reference = (String) request.get("reference");
            this.executionDate = (String) request.getOrDefault("executionDate", LocalDate.now().toString());
            transition("INITIATED");
        }

        void transition(String newStatus) {
            status = newStatus;
            version++;
            history.add(Map.of("status", newStatus, "at", Instant.now().toString()));
        }

        String etag() {
            return "\"" + id + "-v" + version + "\"";
        }

        Map<String, Object> view() {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("paymentId", id);
            view.put("status", status);
            view.putAll(request);
            view.put("reference", reference);
            view.put("executionDate", executionDate);
            if (rejectionReason != null) {
                view.put("rejectionReason", rejectionReason);
            }
            view.put("statusHistory", List.copyOf(history));
            return view;
        }
    }

    private record Idempotent(Map<String, Object> request, String paymentId) {
    }

    private PaymentLifecycleApi() {
        server.before(this::requireToken);
        String p = BASE_PATH;
        server.post(p + "/payments", this::initiate)
              .get(p + "/payments", this::list)
              .get(p + "/payments/{id}", this::read)
              .patch(p + "/payments/{id}", this::update)
              .post(p + "/payments/{id}/authorisation", this::authorise)
              .post(p + "/payments/{id}/execution", this::execute);

        for (String name : List.of("Ada", "Bram", "Cleo", "Daan", "Eva")) {
            Map<String, Object> seed = new LinkedHashMap<>();
            seed.put("debtorIban", "NL91ABNA0417164300");
            seed.put("creditorIban", "NL69INGB0123456789");
            seed.put("creditorName", name);
            seed.put("amount", "10.00");
            seed.put("currency", "EUR");
            String id = "pay-" + ids.getAndIncrement();
            payments.put(id, new Payment(id, seed));
        }
    }

    public static PaymentLifecycleApi start() {
        PaymentLifecycleApi api = new PaymentLifecycleApi();
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
        bank.shutdownNow();
        server.close();
    }

    // ------------------------------------------------------------------------------------------- handlers

    private Optional<ApiResponse> requireToken(ApiRequest request) {
        if (!("Bearer " + TOKEN).equals(request.header("Authorization"))) {
            throw new ApiException(401, "Unauthorized", "A valid bearer token is required")
                    .withHeader("WWW-Authenticate", "Bearer realm=\"payments\"");
        }
        return Optional.empty();
    }

    private ApiResponse initiate(ApiRequest request) {
        String key = request.header("Idempotency-Key");
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("The header Idempotency-Key is required");
        }
        Map<String, Object> json = request.jsonObject();
        synchronized (this) {
            Idempotent earlier = idempotencyKeys.get(key);
            if (earlier != null) {
                if (!earlier.request().equals(json)) {
                    throw new ApiException(422, "Unprocessable Content",
                            "Idempotency-Key " + key + " was already used for a different request");
                }
                Payment payment = payments.get(earlier.paymentId());
                return ApiResponse.created(location(payment), payment.view())
                        .header("Idempotent-Replayed", "true").header("ETag", payment.etag());
            }
            validate(json);
            String id = "pay-" + ids.getAndIncrement();
            Payment payment = new Payment(id, json);
            payments.put(id, payment);
            idempotencyKeys.put(key, new Idempotent(json, id));
            return ApiResponse.created(location(payment), payment.view()).header("ETag", payment.etag());
        }
    }

    private synchronized ApiResponse read(ApiRequest request) {
        Payment payment = find(request.pathParam("id"));
        if (payment.etag().equals(request.header("If-None-Match"))) {
            return ApiResponse.status(304).header("ETag", payment.etag());
        }
        return ApiResponse.ok(payment.view()).header("ETag", payment.etag());
    }

    private synchronized ApiResponse update(ApiRequest request) {
        Payment payment = find(request.pathParam("id"));
        String ifMatch = request.header("If-Match");
        if (ifMatch == null) {
            throw new ApiException(428, "Precondition Required", "Send If-Match with the ETag you last read");
        }
        if (!ifMatch.equals(payment.etag())) {
            throw new ApiException(412, "Precondition Failed", "The payment changed since you read it");
        }
        if (!payment.status.equals("INITIATED")) {
            throw ApiException.conflict("A payment can only be changed while INITIATED, it is " + payment.status);
        }
        Map<String, Object> patch = request.jsonObject();
        if (patch.containsKey("reference")) {
            payment.reference = (String) patch.get("reference");
        }
        if (patch.containsKey("executionDate")) {
            payment.executionDate = (String) patch.get("executionDate");
        }
        payment.version++;
        return ApiResponse.ok(payment.view()).header("ETag", payment.etag());
    }

    private synchronized ApiResponse authorise(ApiRequest request) {
        Payment payment = find(request.pathParam("id"));
        if (!payment.status.equals("INITIATED")) {
            throw ApiException.conflict("Only an INITIATED payment can be authorised, it is " + payment.status);
        }
        payment.transition("AUTHORISED");
        return ApiResponse.ok(payment.view()).header("ETag", payment.etag());
    }

    private synchronized ApiResponse execute(ApiRequest request) {
        Payment payment = find(request.pathParam("id"));
        if (!payment.status.equals("AUTHORISED")) {
            throw ApiException.conflict("Only an AUTHORISED payment can be executed, it is " + payment.status);
        }
        payment.transition("EXECUTING");
        bank.schedule(() -> settle(payment), 500, TimeUnit.MILLISECONDS);
        return ApiResponse.json(202, payment.view())
                .header("Location", location(payment))
                .header("Retry-After", "1");
    }

    private synchronized void settle(Payment payment) {
        if (new BigDecimal((String) payment.request.get("amount")).compareTo(FUNDS) > 0) {
            payment.rejectionReason = "INSUFFICIENT_FUNDS";
            payment.transition("REJECTED");
        } else {
            payment.transition("EXECUTED");
        }
    }

    private synchronized ApiResponse list(ApiRequest request) {
        int limit = Integer.parseInt(Objects.requireNonNullElse(request.query("limit"), "10"));
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("limit must be between 1 and 100");
        }
        List<String> order = new ArrayList<>(payments.keySet());
        String cursor = request.query("cursor");
        int from = cursor == null ? 0 : order.indexOf(cursor);
        if (from < 0) {
            throw ApiException.badRequest("Unknown cursor " + cursor);
        }
        int to = Math.min(from + limit, order.size());
        List<Map<String, Object>> items = order.subList(from, to).stream().map(id -> payments.get(id).view()).toList();
        ApiResponse response = ApiResponse.ok(Map.of("items", items));
        if (to < order.size()) {
            response.header("Link", "<" + BASE_PATH + "/payments?limit=" + limit + "&cursor=" + order.get(to) + ">; rel=\"next\"");
        }
        return response;
    }

    // ------------------------------------------------------------------------------------------- helpers

    private Payment find(String id) {
        Payment payment = payments.get(id);
        if (payment == null) {
            throw ApiException.notFound("Payment " + id + " does not exist");
        }
        return payment;
    }

    private static String location(Payment payment) {
        return BASE_PATH + "/payments/" + payment.id;
    }

    private static void validate(Map<String, Object> json) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (String field : List.of("debtorIban", "creditorIban")) {
            if (!(json.get(field) instanceof String iban) || !isValidIban(iban)) {
                errors.add(error(field, "must be a valid IBAN"));
            }
        }
        if (!(json.get("creditorName") instanceof String name) || name.isBlank() || name.length() > 70) {
            errors.add(error("creditorName", "must be 1 to 70 characters"));
        }
        if (!(json.get("amount") instanceof String amount) || !amount.matches("-?[0-9]+(\\.[0-9]+)?")) {
            errors.add(error("amount", "must be a decimal number as a string"));
        } else {
            BigDecimal value = new BigDecimal(amount);
            if (value.signum() <= 0) {
                errors.add(error("amount", "must be more than zero"));
            } else if (value.scale() > 2) {
                errors.add(error("amount", "must have at most two decimals"));
            } else if (value.compareTo(SINGLE_PAYMENT_LIMIT) > 0) {
                errors.add(error("amount", "exceeds the single payment limit of " + SINGLE_PAYMENT_LIMIT));
            }
        }
        if (!(json.get("currency") instanceof String currency) || !CURRENCIES.contains(currency)) {
            errors.add(error("currency", "is not supported"));
        }
        if (json.get("executionDate") instanceof String date) {
            try {
                if (LocalDate.parse(date).isBefore(LocalDate.now())) {
                    errors.add(error("executionDate", "must not be in the past"));
                }
            } catch (RuntimeException e) {
                errors.add(error("executionDate", "must be a date such as 2026-12-31"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private static Map<String, String> error(String field, String detail) {
        return Map.of("field", field, "detail", detail);
    }

    /** ISO 13616: move the first four characters to the end, letters to numbers (A=10), and the result mod 97 is 1. */
    static boolean isValidIban(String iban) {
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
