package com.example.transactions;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Transactions API of Module 3: one account with a month of transactions, as JSON, as an ISO 20022 style XML bank
 * statement and as an HTML page, so the tests can practise checking every kind of response.
 *
 * <table>
 *   <caption>Endpoints (all under {@code /api/v1})</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code GET /accounts/{id}/transactions?type=DEBIT|CREDIT}</td><td>200 JSON, 404 for an unknown account</td></tr>
 *   <tr><td>{@code POST /accounts/{id}/transactions}</td><td>201 with {@code Location} ending in the new id</td></tr>
 *   <tr><td>{@code GET /accounts/{id}/statement}</td><td>200 {@code application/xml}, a simplified camt.053</td></tr>
 *   <tr><td>{@code GET /accounts/{id}/statement.html}</td><td>200 {@code text/html}</td></tr>
 *   <tr><td>{@code GET /categories}</td><td>200, a JSON array at the root</td></tr>
 *   <tr><td>{@code POST /sessions}</td><td>201 with two {@code Set-Cookie} headers</td></tr>
 * </table>
 * Amounts are JSON numbers with two decimals; debits are negative.
 */
public final class TransactionsApi implements AutoCloseable {

    public static final String BASE_PATH = "/api/v1";
    public static final String ACCOUNT_ID = "acc-1001";
    public static final String IBAN = "NL91ABNA0417164300";

    private static final BigDecimal OPENING_BALANCE = new BigDecimal("1000.00");

    private final ApiServer server = new ApiServer();
    private final List<Map<String, Object>> transactions = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger(1);

    private TransactionsApi() {
        add("2026-03-01", "2500.00", "Acme B.V.", "NL02ABNA0123456789", "SALARY", List.of("income"), "Salary March");
        add("2026-03-02", "-42.35", "Albert Heijn", "NL44RABO0123456789", "GROCERIES", List.of("food"), "Pinbetaling AH 1021");
        add("2026-03-05", "-1100.00", "Woonstichting De Key", "NL27INGB0000123456", "RENT", List.of("housing", "fixed"), "Huur maart");
        add("2026-03-09", "-60.00", "Albert Heijn", "NL44RABO0123456789", "GROCERIES", List.of("food"), "Pinbetaling AH 1021");
        add("2026-03-15", "-40.00", "NS Reizigers", "NL61INGB0000654321", "TRANSPORT", List.of("travel"), "OV-chipkaart");
        add("2026-03-20", "150.00", "Belastingdienst", "NL86INGB0002445588", "REFUND", List.of("income", "tax"), "Teruggaaf 2025");

        String p = BASE_PATH;
        server.get(p + "/accounts/{id}/transactions", this::listTransactions)
              .post(p + "/accounts/{id}/transactions", this::addTransaction)
              .get(p + "/accounts/{id}/statement", this::xmlStatement)
              .get(p + "/accounts/{id}/statement.html", this::htmlStatement)
              .get(p + "/categories", r -> ApiResponse.ok(List.of(
                      Map.of("code", "SALARY", "label", "Salary"),
                      Map.of("code", "GROCERIES", "label", "Groceries"),
                      Map.of("code", "RENT", "label", "Rent"),
                      Map.of("code", "TRANSPORT", "label", "Transport"),
                      Map.of("code", "REFUND", "label", "Refund"))))
              .post(p + "/sessions", r -> ApiResponse.json(201, Map.of("status", "signed in"))
                      .header("Set-Cookie", "SESSION=" + UUID.randomUUID()
                              + "; Path=/api; Max-Age=900; Secure; HttpOnly; SameSite=Strict")
                      .header("Set-Cookie", "lang=nl; Path=/"));
    }

    public static TransactionsApi start() {
        TransactionsApi api = new TransactionsApi();
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

    // ------------------------------------------------------------------------------------------- JSON

    private void add(String date, String amount, String name, String iban, String category, List<String> tags,
                     String remittance) {
        BigDecimal value = new BigDecimal(amount);
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("id", "tx-" + ids.getAndIncrement());
        transaction.put("bookingDate", LocalDate.parse(date));
        transaction.put("type", value.signum() < 0 ? "DEBIT" : "CREDIT");
        transaction.put("amount", value);
        transaction.put("counterparty", Map.of("name", name, "iban", iban));
        transaction.put("category", category);
        transaction.put("tags", tags);
        transaction.put("remittance-info", remittance);          // a key with a dash, on purpose
        transactions.add(transaction);
    }

    private ApiResponse listTransactions(ApiRequest request) {
        requireAccount(request);
        String type = request.query("type");
        List<Map<String, Object>> selected;
        synchronized (transactions) {
            selected = transactions.stream()
                    .filter(t -> type == null || t.get("type").equals(type.toUpperCase()))
                    .toList();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", ACCOUNT_ID);
        body.put("iban", IBAN);
        body.put("currency", "EUR");
        body.put("balance", Map.of("opening", OPENING_BALANCE, "closing", closingBalance()));
        body.put("transactions", selected);
        return ApiResponse.ok(body);
    }

    private ApiResponse addTransaction(ApiRequest request) {
        requireAccount(request);
        Map<String, Object> json = request.jsonObject();
        Object amount = json.get("amount");
        Object name = json.get("counterpartyName");
        if (amount == null || name == null) {
            throw ApiException.validation(List.of(Map.of("field", "amount/counterpartyName", "detail", "both are required")));
        }
        Map<String, Object> created;
        synchronized (transactions) {
            add(LocalDate.now().toString(), new BigDecimal(amount.toString()).setScale(2).toPlainString(),
                    name.toString(), "NL00TEST0000000000", "OTHER", List.of(), "Added by a test");
            created = transactions.getLast();
        }
        return ApiResponse.created(BASE_PATH + "/accounts/" + ACCOUNT_ID + "/transactions/" + created.get("id"), created);
    }

    private BigDecimal closingBalance() {
        synchronized (transactions) {
            return transactions.stream().map(t -> (BigDecimal) t.get("amount")).reduce(OPENING_BALANCE, BigDecimal::add);
        }
    }

    private static void requireAccount(ApiRequest request) {
        if (!ACCOUNT_ID.equals(request.pathParam("id"))) {
            throw ApiException.notFound("Account " + request.pathParam("id") + " does not exist");
        }
    }

    // ------------------------------------------------------------------------------------------- XML and HTML

    /**
     * A simplified ISO 20022 camt.053 (bank-to-customer statement). The structure and element names follow the
     * standard; many mandatory elements are left out to keep it readable.
     */
    private ApiResponse xmlStatement(ApiRequest request) {
        requireAccount(request);
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08" xmlns:ext="urn:example:bank:extensions">
                  <BkToCstmrStmt>
                    <GrpHdr>
                      <MsgId>STMT-2026-03</MsgId>
                      <CreDtTm>2026-04-01T06:00:00</CreDtTm>
                    </GrpHdr>
                    <Stmt>
                      <Id>%s-2026-03</Id>
                      <Acct>
                        <Id><IBAN>%s</IBAN></Id>
                        <Ccy>EUR</Ccy>
                      </Acct>
                      <Bal><Tp>OPBD</Tp><Amt Ccy="EUR">%s</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
                      <Bal><Tp>CLBD</Tp><Amt Ccy="EUR">%s</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
                """.formatted(ACCOUNT_ID, IBAN, OPENING_BALANCE.toPlainString(), closingBalance().toPlainString()));
        synchronized (transactions) {
            for (Map<String, Object> t : transactions) {
                BigDecimal amount = (BigDecimal) t.get("amount");
                @SuppressWarnings("unchecked")
                Map<String, String> counterparty = (Map<String, String>) t.get("counterparty");
                xml.append("""
                              <Ntry>
                                <NtryRef>%s</NtryRef>
                                <Amt Ccy="EUR">%s</Amt>
                                <CdtDbtInd>%s</CdtDbtInd>
                                <BookgDt><Dt>%s</Dt></BookgDt>
                                <NtryDtls>
                                  <RltdPties><Nm>%s</Nm></RltdPties>
                                  <RmtInf><Ustrd>%s</Ustrd></RmtInf>
                                </NtryDtls>
                                <ext:Category>%s</ext:Category>
                              </Ntry>
                        """.formatted(t.get("id"), amount.abs().toPlainString(), amount.signum() < 0 ? "DBIT" : "CRDT",
                        t.get("bookingDate"), escape(counterparty.get("name")), escape((String) t.get("remittance-info")),
                        t.get("category")));
            }
        }
        xml.append("""
                    </Stmt>
                  </BkToCstmrStmt>
                </Document>
                """);
        return ApiResponse.text(200, "application/xml; charset=utf-8", xml.toString());
    }

    private ApiResponse htmlStatement(ApiRequest request) {
        requireAccount(request);
        StringBuilder rows = new StringBuilder();
        synchronized (transactions) {
            for (Map<String, Object> t : transactions) {
                @SuppressWarnings("unchecked")
                Map<String, String> counterparty = (Map<String, String>) t.get("counterparty");
                rows.append("<tr><td>").append(t.get("bookingDate")).append("</td><td>")
                    .append(escape(counterparty.get("name"))).append("</td><td>")
                    .append(((BigDecimal) t.get("amount")).toPlainString()).append("</td></tr>\n");
            }
        }
        String html = """
                <html>
                  <head><title>Statement %s</title></head>
                  <body>
                    <h1>March 2026</h1>
                    <table id="transactions">
                %s    </table>
                  </body>
                </html>
                """.formatted(IBAN, rows);
        return ApiResponse.text(200, "text/html; charset=utf-8", html);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
