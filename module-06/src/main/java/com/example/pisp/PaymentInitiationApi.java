package com.example.pisp;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * A payment initiation API (PSD2 PISP style) with a documented contract, so Module 6 can test responses against
 * JSON Schemas, an XML Schema and an OpenAPI description.
 *
 * <table>
 *   <caption>Endpoints (all under {@code /pisp/v1})</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code POST /domestic-payments}, JSON</td><td>201 payment, or 400/415/422 problem</td></tr>
 *   <tr><td>{@code GET /domestic-payments/{id}}</td><td>200 payment, or 404 problem</td></tr>
 *   <tr><td>{@code GET /domestic-payments/{id}?format=v0}</td><td>200, the payment in an old shape that breaks the
 *       contract, to show what schema validation catches</td></tr>
 *   <tr><td>{@code POST /payment-files}, {@code application/xml} ISO 20022 pain.001</td><td>202, a pain.002 status
 *       report: {@code ACCP}, or {@code RJCT} when the declared number of transactions is wrong</td></tr>
 * </table>
 */
public final class PaymentInitiationApi implements AutoCloseable {

    public static final String BASE_PATH = "/pisp/v1";

    private static final String IBAN = "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}";

    private final ApiServer server = new ApiServer();
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1);

    private PaymentInitiationApi() {
        String p = BASE_PATH;
        server.post(p + "/domestic-payments", this::initiate)
              .get(p + "/domestic-payments/{id}", this::read)
              .post(p + "/payment-files", this::paymentFile);
    }

    public static PaymentInitiationApi start() {
        PaymentInitiationApi api = new PaymentInitiationApi();
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

    // ------------------------------------------------------------------------------------------- JSON payments

    @SuppressWarnings("unchecked")
    private ApiResponse initiate(ApiRequest request) {
        Map<String, Object> json = request.jsonObject();
        List<Map<String, String>> errors = new ArrayList<>();
        requireText(errors, json, "instructionId", ".{1,35}");
        Map<String, Object> debtor = object(errors, json, "debtor");
        Map<String, Object> creditor = object(errors, json, "creditor");
        Map<String, Object> amount = object(errors, json, "amount");
        if (debtor != null) {
            requireText(errors, debtor, "iban", IBAN, "debtor.iban");
        }
        if (creditor != null) {
            requireText(errors, creditor, "iban", IBAN, "creditor.iban");
            requireText(errors, creditor, "name", ".{1,70}", "creditor.name");
        }
        if (amount != null) {
            requireText(errors, amount, "value", "[0-9]{1,13}\\.[0-9]{2}", "amount.value");
            requireText(errors, amount, "currency", "[A-Z]{3}", "amount.currency");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        String id = "pmt-" + ids.getAndIncrement();
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", id);
        payment.put("instructionId", json.get("instructionId"));
        payment.put("status", "RCVD");                              // ISO 20022: received
        payment.put("debtor", debtor);
        payment.put("creditor", creditor);
        payment.put("amount", amount);
        if (json.get("remittanceInformation") != null) {
            payment.put("remittanceInformation", json.get("remittanceInformation"));
        }
        payment.put("requestedExecutionDate",
                json.getOrDefault("requestedExecutionDate", LocalDate.now().toString()));
        payment.put("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        payment.put("_links", Map.of("self", Map.of("href", BASE_PATH + "/domestic-payments/" + id)));
        payments.put(id, payment);
        return ApiResponse.created(BASE_PATH + "/domestic-payments/" + id, payment);
    }

    @SuppressWarnings("unchecked")
    private ApiResponse read(ApiRequest request) {
        Map<String, Object> payment = payments.get(request.pathParam("id"));
        if (payment == null) {
            throw ApiException.notFound("Payment " + request.pathParam("id") + " does not exist");
        }
        if ("v0".equals(request.query("format"))) {
            // The shape before the contract was agreed: a number for the amount, a free-text status, no links.
            Map<String, Object> amount = (Map<String, Object>) payment.get("amount");
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("paymentId", payment.get("paymentId"));
            old.put("status", "PENDING");
            old.put("amount", Double.parseDouble((String) amount.get("value")));
            old.put("currency", amount.get("currency"));
            return ApiResponse.ok(old);
        }
        return ApiResponse.ok(payment);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(List<Map<String, String>> errors, Map<String, Object> json, String field) {
        Object value = json.get(field);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        errors.add(Map.of("field", field, "detail", "is required and must be an object"));
        return null;
    }

    private static void requireText(List<Map<String, String>> errors, Map<String, Object> json, String field, String regex) {
        requireText(errors, json, field, regex, field);
    }

    private static void requireText(List<Map<String, String>> errors, Map<String, Object> json, String field,
                                    String regex, String reportedName) {
        Object value = json.get(field);
        if (!(value instanceof String text) || !text.matches(regex)) {
            errors.add(Map.of("field", reportedName, "detail", "is required and must match " + regex));
        }
    }

    // ------------------------------------------------------------------------------------------- XML payment files

    private ApiResponse paymentFile(ApiRequest request) throws Exception {
        if (!request.contentType().contains("xml")) {
            throw new ApiException(415, "Unsupported Media Type", "Expected an ISO 20022 pain.001 XML document");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // no XXE
        Document document;
        try {
            document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(request.body()));
        } catch (Exception e) {
            throw ApiException.badRequest("The body is not well-formed XML");
        }
        String messageId = text(document, "MsgId");
        int declared = Integer.parseInt(text(document, "NbOfTxs"));
        int actual = document.getElementsByTagNameNS("*", "CdtTrfTxInf").getLength();
        boolean accepted = declared == actual;

        String report = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.002.001.10">
                  <CstmrPmtStsRpt>
                    <GrpHdr>
                      <MsgId>STS-%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                    </GrpHdr>
                    <OrgnlGrpInfAndSts>
                      <OrgnlMsgId>%s</OrgnlMsgId>
                      <OrgnlMsgNmId>pain.001.001.09</OrgnlMsgNmId>
                      <OrgnlNbOfTxs>%d</OrgnlNbOfTxs>
                      <GrpSts>%s</GrpSts>%s
                    </OrgnlGrpInfAndSts>
                  </CstmrPmtStsRpt>
                </Document>
                """.formatted(messageId, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", ""),
                messageId, declared, accepted ? "ACCP" : "RJCT",
                accepted ? "" : "\n          <StsRsnInf><AddtlInf>NbOfTxs is %d but the file has %d transactions</AddtlInf></StsRsnInf>"
                        .formatted(declared, actual));
        return ApiResponse.text(202, "application/xml; charset=utf-8", report);
    }

    private static String text(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            throw ApiException.badRequest("The element " + localName + " is missing");
        }
        return nodes.item(0).getTextContent().trim();
    }
}
