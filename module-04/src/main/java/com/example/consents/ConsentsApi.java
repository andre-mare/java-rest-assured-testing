package com.example.consents;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Customers and Consents API of Module 4, modelled on open banking account access consents (PSD2 AIS): a customer
 * grants a consent with permissions, authorises it, and the consent id then unlocks their accounts.
 *
 * <table>
 *   <caption>Endpoints (all under {@code /api/v1})</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code POST /customers}</td><td>201 + {@code Location}, a {@link Customer}</td></tr>
 *   <tr><td>{@code GET /customers/{id}}</td><td>200 or 404</td></tr>
 *   <tr><td>{@code GET /customers?page=0&size=2}</td><td>200, a page: {@code items, page, size, totalItems, totalPages}</td></tr>
 *   <tr><td>{@code POST /consents}</td><td>201, status {@code AWAITING_AUTHORISATION}, with {@code _links}</td></tr>
 *   <tr><td>{@code GET /consents/{id}}</td><td>200 or 404</td></tr>
 *   <tr><td>{@code POST /consents/{id}/authorisation}</td><td>200, status {@code AUTHORISED}</td></tr>
 *   <tr><td>{@code GET /accounts}, header {@code Consent-ID}</td><td>200, a JSON array of the customer's accounts;
 *       400 without the header, 403 unless the consent is authorised with {@code ReadAccountsBasic}</td></tr>
 *   <tr><td>{@code GET /accounts/{id}/balances}, header {@code Consent-ID}</td><td>200, needs {@code ReadBalances}</td></tr>
 * </table>
 */
public final class ConsentsApi implements AutoCloseable {

    public static final String BASE_PATH = "/api/v1";
    public static final Set<String> PERMISSIONS = Set.of("ReadAccountsBasic", "ReadBalances", "ReadTransactions");

    private final ApiServer server = new ApiServer();
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> consents = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> accountsByCustomer = new LinkedHashMap<>();
    private final AtomicInteger customerIds = new AtomicInteger(1);
    private final AtomicInteger consentIds = new AtomicInteger(1);
    private final AtomicInteger accountIds = new AtomicInteger(1);

    private ConsentsApi() {
        seed("Anna de Vries", "anna@example.nl", "RETAIL");
        seed("Jan Jansen", "jan@example.nl", "RETAIL");
        seed("Bakkerij Bart B.V.", "finance@bakkerijbart.nl", "BUSINESS");
        seed("Emily Clarke", "emily@example.co.uk", "RETAIL");
        seed("Fietsenmaker Fokke", "fokke@example.nl", "BUSINESS");

        String p = BASE_PATH;
        server.post(p + "/customers", this::createCustomer)
              .get(p + "/customers", this::listCustomers)
              .get(p + "/customers/{id}", r -> ApiResponse.ok(customer(r.pathParam("id"))))
              .post(p + "/consents", this::createConsent)
              .get(p + "/consents/{id}", r -> ApiResponse.ok(consent(r.pathParam("id"))))
              .post(p + "/consents/{id}/authorisation", this::authorise)
              .get(p + "/accounts", this::accounts)
              .get(p + "/accounts/{id}/balances", this::balances);
    }

    public static ConsentsApi start() {
        ConsentsApi api = new ConsentsApi();
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

    // ------------------------------------------------------------------------------------------- customers

    private synchronized Customer seed(String name, String email, String segment) {
        Customer customer = new Customer("cust-" + customerIds.getAndIncrement(), name, email, segment, Instant.now());
        customers.put(customer.id(), customer);
        List<Map<String, Object>> accounts = new ArrayList<>();
        accounts.add(account("CURRENT", "Main account"));
        if (segment.equals("RETAIL")) {
            accounts.add(account("SAVINGS", "Rainy day"));
        }
        accountsByCustomer.put(customer.id(), accounts);
        return customer;
    }

    private Map<String, Object> account(String type, String nickname) {
        int n = accountIds.getAndIncrement();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("accountId", "acc-" + (1000 + n));
        account.put("iban", "NL%02dBANK%010d".formatted(10 + n, n));
        account.put("currency", "EUR");
        account.put("type", type);
        account.put("nickname", nickname);
        account.put("productCode", type.equals("SAVINGS") ? "SAV-01" : "CUR-01");   // not in the tests' record, on purpose
        account.put("balance", n % 2 == 0 ? "1250.50" : "15000.00");
        return account;
    }

    private ApiResponse createCustomer(ApiRequest request) {
        Map<String, Object> json = request.jsonObject();
        List<Map<String, String>> errors = new ArrayList<>();
        if (!(json.get("name") instanceof String name) || name.isBlank()) {
            errors.add(Map.of("field", "name", "detail", "must not be blank"));
        }
        if (!(json.get("email") instanceof String email) || !email.contains("@")) {
            errors.add(Map.of("field", "email", "detail", "must be an email address"));
        }
        if (!"RETAIL".equals(json.get("segment")) && !"BUSINESS".equals(json.get("segment"))) {
            errors.add(Map.of("field", "segment", "detail", "must be RETAIL or BUSINESS"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Customer customer = seed((String) json.get("name"), (String) json.get("email"), (String) json.get("segment"));
        return ApiResponse.created(BASE_PATH + "/customers/" + customer.id(), customer);
    }

    private ApiResponse listCustomers(ApiRequest request) {
        int page = intParam(request, "page", 0);
        int size = intParam(request, "size", 10);
        List<Customer> all;
        synchronized (this) {
            all = List.copyOf(customers.values());
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", all.subList(from, to));
        body.put("page", page);
        body.put("size", size);
        body.put("totalItems", all.size());
        body.put("totalPages", (all.size() + size - 1) / size);
        return ApiResponse.ok(body);
    }

    private synchronized Customer customer(String id) {
        Customer customer = customers.get(id);
        if (customer == null) {
            throw ApiException.notFound("Customer " + id + " does not exist");
        }
        return customer;
    }

    // ------------------------------------------------------------------------------------------- consents

    private ApiResponse createConsent(ApiRequest request) {
        Map<String, Object> json = request.jsonObject();
        String customerId = String.valueOf(json.get("customerId"));
        customer(customerId);
        if (!(json.get("permissions") instanceof List<?> permissions) || permissions.isEmpty()
                || !PERMISSIONS.containsAll(permissions)) {
            throw ApiException.validation(List.of(Map.of("field", "permissions",
                    "detail", "must be a non-empty subset of " + PERMISSIONS)));
        }
        String id = "consent-" + consentIds.getAndIncrement();
        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("consentId", id);
        consent.put("customerId", customerId);
        consent.put("permissions", permissions);
        consent.put("status", "AWAITING_AUTHORISATION");
        consent.put("expiresOn", json.getOrDefault("expiresOn", LocalDate.now().plusDays(90).toString()));
        consent.put("_links", Map.of(
                "self", Map.of("href", BASE_PATH + "/consents/" + id),
                "authorise", Map.of("href", BASE_PATH + "/consents/" + id + "/authorisation")));
        synchronized (this) {
            consents.put(id, consent);
        }
        return ApiResponse.created(BASE_PATH + "/consents/" + id, consent);
    }

    private synchronized Map<String, Object> consent(String id) {
        Map<String, Object> consent = consents.get(id);
        if (consent == null) {
            throw ApiException.notFound("Consent " + id + " does not exist");
        }
        return consent;
    }

    private synchronized ApiResponse authorise(ApiRequest request) {
        Map<String, Object> consent = consent(request.pathParam("id"));
        consent.put("status", "AUTHORISED");
        return ApiResponse.ok(consent);
    }

    // ------------------------------------------------------------------------------------------- accounts

    private ApiResponse accounts(ApiRequest request) {
        Map<String, Object> consent = usableConsent(request, "ReadAccountsBasic");
        synchronized (this) {
            return ApiResponse.ok(accountsByCustomer.get(consent.get("customerId")));
        }
    }

    private ApiResponse balances(ApiRequest request) {
        Map<String, Object> consent = usableConsent(request, "ReadBalances");
        Map<String, Object> account;
        synchronized (this) {
            account = accountsByCustomer.get(consent.get("customerId")).stream()
                    .filter(a -> a.get("accountId").equals(request.pathParam("id")))
                    .findFirst()
                    .orElseThrow(() -> ApiException.notFound("Account " + request.pathParam("id") + " is not covered by the consent"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", account.get("accountId"));
        body.put("balances", List.of(Map.of(
                "type", "closingBooked",
                "amount", Map.of("amount", account.get("balance"), "currency", account.get("currency")))));
        return ApiResponse.ok(body);
    }

    private Map<String, Object> usableConsent(ApiRequest request, String permission) {
        String consentId = request.header("Consent-ID");
        if (consentId == null) {
            throw ApiException.badRequest("The header Consent-ID is required");
        }
        Map<String, Object> consent;
        synchronized (this) {
            consent = consents.get(consentId);
        }
        if (consent == null || !"AUTHORISED".equals(consent.get("status"))) {
            throw new ApiException(403, "Forbidden", "Consent " + consentId + " is not authorised");
        }
        if (!((List<?>) consent.get("permissions")).contains(permission)) {
            throw new ApiException(403, "Forbidden", "Consent " + consentId + " lacks the permission " + permission);
        }
        return consent;
    }

    private static int intParam(ApiRequest request, String name, int defaultValue) {
        String value = request.query(name);
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Query parameter " + name + " must be a number");
        }
    }
}
