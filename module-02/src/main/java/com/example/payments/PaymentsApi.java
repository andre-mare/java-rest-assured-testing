package com.example.payments;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * The Payments API of Module 2. It accepts every kind of request data, so the tests can practise building requests.
 *
 * <table>
 *   <caption>Endpoints (all under {@code /api/v1})</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code POST /payments}, JSON, header {@code X-Request-ID}</td><td>201 + {@code Location}, or 400/415/422</td></tr>
 *   <tr><td>{@code GET /payments?status=..&currency=..}</td><td>200 {@code {"count":..,"payments":[..]}};
 *       {@code status} may be repeated</td></tr>
 *   <tr><td>{@code QUERY /payments}, JSON filter body</td><td>200, same shape as GET</td></tr>
 *   <tr><td>{@code GET /payments/{id}}</td><td>200 or 404</td></tr>
 *   <tr><td>{@code PUT /payments/{id}}, JSON</td><td>200 (replaces), or 409 when cancelled</td></tr>
 *   <tr><td>{@code PATCH /payments/{id}}, {@code application/merge-patch+json}</td><td>200, changes {@code reference}</td></tr>
 *   <tr><td>{@code DELETE /payments/{id}}</td><td>204, the payment is cancelled</td></tr>
 *   <tr><td>{@code POST /payments/bulk}, multipart: {@code batchName} + CSV {@code file}</td><td>201</td></tr>
 *   <tr><td>{@code POST /beneficiaries}, form url-encoded</td><td>201</td></tr>
 *   <tr><td>any method on {@code /echo}, {@code /echo/{a}} and {@code /echo/{a}/{b}}</td><td>200, a JSON description of the
 *       request as the server received it</td></tr>
 * </table>
 * {@code HEAD} works for every GET route, and {@code OPTIONS} for every path.
 */
public final class PaymentsApi implements AutoCloseable {

    public static final String BASE_PATH = "/api/v1";

    private static final String IBAN = "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}";
    private static final String AMOUNT = "[0-9]{1,9}\\.[0-9]{2}";

    private final Map<String, Payment> payments = new LinkedHashMap<>();
    private final AtomicInteger paymentIds = new AtomicInteger(1);
    private final AtomicInteger beneficiaryIds = new AtomicInteger(1);
    private final ApiServer server = new ApiServer();

    private PaymentsApi() {
        String p = BASE_PATH;
        server.post(p + "/payments", this::createPayment)
              .get(p + "/payments", this::listPayments)
              .route("QUERY", p + "/payments", this::queryPayments)
              .get(p + "/payments/{id}", r -> ApiResponse.ok(find(r.pathParam("id"))))
              .put(p + "/payments/{id}", this::replacePayment)
              .patch(p + "/payments/{id}", this::patchPayment)
              .delete(p + "/payments/{id}", this::cancelPayment)
              .post(p + "/payments/bulk", this::bulkUpload)
              .post(p + "/beneficiaries", this::createBeneficiary);
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "QUERY", "PURGE")) {
            server.route(method, p + "/echo", this::echo);
            server.route(method, p + "/echo/{a}", this::echo);
            server.route(method, p + "/echo/{a}/{b}", this::echo);
        }
        server.after((request, response) -> {
            String requestId = request.header("X-Request-ID");
            if (requestId != null) {
                response.header("X-Request-ID", requestId);
            }
        });
    }

    public static PaymentsApi start() {
        PaymentsApi api = new PaymentsApi();
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

    // ------------------------------------------------------------------------------------------- payments

    private ApiResponse createPayment(ApiRequest request) {
        if (request.header("X-Request-ID") == null) {
            throw ApiException.badRequest("The header X-Request-ID is required");
        }
        Map<String, Object> json = request.jsonObject();
        validate(json);
        String id = "pay-" + paymentIds.getAndIncrement();
        Payment payment = new Payment(id, PaymentStatus.RECEIVED, text(json, "debtorIban"), text(json, "creditorIban"),
                text(json, "creditorName"), text(json, "amount"), text(json, "currency"), text(json, "reference"),
                Instant.now());
        synchronized (payments) {
            payments.put(id, payment);
        }
        return ApiResponse.created(BASE_PATH + "/payments/" + id, payment);
    }

    private ApiResponse replacePayment(ApiRequest request) {
        Payment existing = find(request.pathParam("id"));
        if (existing.status() == PaymentStatus.CANCELLED) {
            throw ApiException.conflict("Payment " + existing.id() + " is cancelled and can't be changed");
        }
        Map<String, Object> json = request.jsonObject();
        validate(json);
        Payment replaced = new Payment(existing.id(), existing.status(), text(json, "debtorIban"),
                text(json, "creditorIban"), text(json, "creditorName"), text(json, "amount"), text(json, "currency"),
                text(json, "reference"), existing.createdAt());
        synchronized (payments) {
            payments.put(existing.id(), replaced);
        }
        return ApiResponse.ok(replaced);
    }

    private ApiResponse patchPayment(ApiRequest request) {
        Payment existing = find(request.pathParam("id"));
        if (!request.contentType().toLowerCase(Locale.ROOT).startsWith("application/merge-patch+json")) {
            throw new ApiException(415, "Unsupported Media Type", "PATCH needs application/merge-patch+json (RFC 7396)");
        }
        Map<String, Object> json = request.jsonObject();
        for (String field : json.keySet()) {
            if (!field.equals("reference")) {
                throw ApiException.validation(List.of(Map.of("field", field, "detail", "can't be changed with PATCH")));
            }
        }
        Payment patched = existing.withReference(text(json, "reference"));
        synchronized (payments) {
            payments.put(existing.id(), patched);
        }
        return ApiResponse.ok(patched);
    }

    private ApiResponse cancelPayment(ApiRequest request) {
        Payment existing = find(request.pathParam("id"));
        synchronized (payments) {
            payments.put(existing.id(), existing.withStatus(PaymentStatus.CANCELLED));
        }
        return ApiResponse.noContent();
    }

    private ApiResponse listPayments(ApiRequest request) {
        Predicate<Payment> filter = payment -> true;
        List<String> statuses = request.queryParams().getOrDefault("status", List.of());
        if (!statuses.isEmpty()) {
            filter = filter.and(payment -> statuses.contains(payment.status().name()));
        }
        String currency = request.query("currency");
        if (currency != null) {
            filter = filter.and(payment -> payment.currency().equalsIgnoreCase(currency));
        }
        return ApiResponse.ok(page(filter));
    }

    /** QUERY (a safe method with a body): {@code {"currency": "EUR", "minAmount": "10.00"}}, both optional. */
    private ApiResponse queryPayments(ApiRequest request) {
        Map<String, Object> json = request.jsonObject();
        Predicate<Payment> filter = payment -> true;
        String currency = text(json, "currency");
        if (currency != null) {
            filter = filter.and(payment -> payment.currency().equalsIgnoreCase(currency));
        }
        String minAmount = text(json, "minAmount");
        if (minAmount != null) {
            BigDecimal min = new BigDecimal(minAmount);
            filter = filter.and(payment -> new BigDecimal(payment.amount()).compareTo(min) >= 0);
        }
        return ApiResponse.ok(page(filter));
    }

    private Map<String, Object> page(Predicate<Payment> filter) {
        List<Payment> found;
        synchronized (payments) {
            found = payments.values().stream().filter(filter).toList();
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("count", found.size());
        page.put("payments", found);
        return page;
    }

    /** Multipart: a text part {@code batchName} and a CSV part {@code file} with lines
     *  {@code debtorIban,creditorIban,creditorName,amount,currency}. */
    private ApiResponse bulkUpload(ApiRequest request) {
        List<MultipartParser.Part> parts = MultipartParser.parse(request.contentType(), request.body());
        MultipartParser.Part batchName = parts.stream().filter(p -> "batchName".equals(p.name())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("The part 'batchName' is missing"));
        MultipartParser.Part file = parts.stream().filter(p -> "file".equals(p.name())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("The part 'file' is missing"));
        List<String> ids = new ArrayList<>();
        for (String line : file.text().split("\\R")) {
            if (line.isBlank() || line.startsWith("debtorIban")) {
                continue;                                       // skip empty lines and a header row
            }
            String[] f = line.split(",");
            if (f.length != 5) {
                throw ApiException.badRequest("Each CSV line needs 5 fields: " + line);
            }
            String id = "pay-" + paymentIds.getAndIncrement();
            synchronized (payments) {
                payments.put(id, new Payment(id, PaymentStatus.RECEIVED, f[0].trim(), f[1].trim(), f[2].trim(),
                        f[3].trim(), f[4].trim(), batchName.text(), Instant.now()));
            }
            ids.add(id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchName", batchName.text());
        result.put("fileName", file.fileName());
        result.put("fileContentType", file.contentType());
        result.put("count", ids.size());
        result.put("paymentIds", ids);
        return ApiResponse.json(201, result);
    }

    // ------------------------------------------------------------------------------------------- beneficiaries

    private ApiResponse createBeneficiary(ApiRequest request) {
        if (!request.contentType().toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            throw new ApiException(415, "Unsupported Media Type", "Expected a form (application/x-www-form-urlencoded)");
        }
        String name = request.formParam("name");
        String iban = request.formParam("iban");
        List<Map<String, String>> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(Map.of("field", "name", "detail", "must not be blank"));
        }
        if (iban == null || !iban.matches(IBAN)) {
            errors.add(Map.of("field", "iban", "detail", "must be an IBAN"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        String id = "ben-" + beneficiaryIds.getAndIncrement();
        return ApiResponse.created(BASE_PATH + "/beneficiaries/" + id,
                new Beneficiary(id, name, iban, request.formParam("nickname")));
    }

    // ------------------------------------------------------------------------------------------- echo

    /** Describes the request exactly as it arrived, so tests can see what REST Assured sent. */
    private ApiResponse echo(ApiRequest request) {
        Map<String, List<String>> headers = new TreeMap<>();
        request.headers().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values));
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("method", request.method());
        echo.put("path", request.path());
        echo.put("rawPath", request.exchange().getRequestURI().getRawPath());
        echo.put("rawQuery", request.rawQuery() == null ? "" : request.rawQuery());
        echo.put("query", request.queryParams());
        echo.put("headers", headers);
        echo.put("cookies", request.cookies());
        echo.put("contentType", request.contentType());
        echo.put("bodyLength", request.body().length);
        echo.put("body", request.bodyAsString());
        return ApiResponse.ok(echo);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private Payment find(String id) {
        synchronized (payments) {
            Payment payment = payments.get(id);
            if (payment == null) {
                throw ApiException.notFound("Payment " + id + " does not exist");
            }
            return payment;
        }
    }

    private static void validate(Map<String, Object> json) {
        List<Map<String, String>> errors = new ArrayList<>();
        check(errors, json, "debtorIban", IBAN, "must be an IBAN");
        check(errors, json, "creditorIban", IBAN, "must be an IBAN");
        check(errors, json, "creditorName", ".*\\S.*", "must not be blank");
        check(errors, json, "amount", AMOUNT, "must be a positive amount with two decimals, such as \"25.00\"");
        check(errors, json, "currency", "[A-Z]{3}", "must be an ISO 4217 code such as EUR");
        if (errors.isEmpty() && new BigDecimal(text(json, "amount")).signum() <= 0) {
            errors.add(Map.of("field", "amount", "detail", "must be more than zero"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private static void check(List<Map<String, String>> errors, Map<String, Object> json, String field,
                              String regex, String detail) {
        String value = text(json, field);
        if (value == null || !value.matches(regex)) {
            errors.add(Map.of("field", field, "detail", detail));
        }
    }

    private static String text(Map<String, Object> json, String field) {
        Object value = json.get(field);
        return value == null ? null : value.toString();
    }
}
