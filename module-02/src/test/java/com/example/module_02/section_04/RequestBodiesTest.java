package com.example.module_02.section_04;

import static com.example.module_02.support.SamplePayments.CREDITOR_IBAN;
import static com.example.module_02.support.SamplePayments.DEBTOR_IBAN;
import static com.example.module_02.support.SamplePayments.validPayment;
import static com.example.module_02.support.SamplePayments.validPaymentJson;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;

import com.example.payments.PaymentsApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 2.4 – the ways to give a request a body.
 * <p>
 * For a {@code String}, {@code byte[]}, {@code File} or {@code InputStream}, REST Assured sends the content as it is.
 * For any other object, such as a {@code Map}, a {@code List} or a record, it uses an object mapper, chosen by the
 * content type: JSON for {@code application/json}, XML for {@code application/xml}. Jackson 3 is on the classpath here,
 * so JSON bodies are written with it.
 */
class RequestBodiesTest {

    /** A request body as a Java record. Jackson 3 writes its components as JSON fields. */
    record PaymentRequest(String debtorIban, String creditorIban, String creditorName, String amount, String currency) {
    }

    private static PaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification paymentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH)
                .header("X-Request-ID", "bodies");
    }

    @Test
    void aJsonString() {
        paymentsApi()
            .contentType(ContentType.JSON)
            .body(validPaymentJson("12.50"))
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("amount", equalTo("12.50"));
    }

    @Test
    void aMapIsSerializedToJson() {
        paymentsApi()
            .contentType(ContentType.JSON)
            .body(validPayment("13.00"))
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("reference", equalTo("Invoice 2026-001"));
    }

    @Test
    void aRecordIsSerializedToJson() {
        paymentsApi()
            .contentType(ContentType.JSON)
            .body(new PaymentRequest(DEBTOR_IBAN, CREDITOR_IBAN, "Emily Clarke", "14.00", "EUR"))
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("creditorName", equalTo("Emily Clarke"));
    }

    @Test
    void aListIsSerializedToAJsonArray() {
        paymentsApi()
            .contentType(ContentType.JSON)
            .body(List.of(Map.of("id", "a"), Map.of("id", "b")))
        .when()
            .post("/echo")
        .then()
            .body("body", startsWith("["))
            .body("body", containsString("\"id\":\"a\""));
    }

    @Test
    void anInvalidBodyGivesFieldErrors() {
        Map<String, Object> payment = validPayment("0.5");          // one decimal, so invalid
        payment.remove("creditorName");

        paymentsApi()
            .contentType(ContentType.JSON)
            .body(payment)
        .when()
            .post("/payments")
        .then()
            .statusCode(422)
            .body("errors.field", hasItems("amount", "creditorName"));
    }

    @Test
    void theWrongContentTypeIsRejected() {
        paymentsApi()
            .contentType(ContentType.TEXT)
            .body(validPaymentJson("15.00"))
        .when()
            .post("/payments")
        .then()
            .statusCode(415);
    }

    @Test
    void bytesAreSentAsTheyAre() {
        byte[] content = "plain bytes".getBytes(StandardCharsets.UTF_8);

        paymentsApi()
            .contentType("application/octet-stream")
            .body(content)
        .when()
            .post("/echo")
        .then()
            .body("bodyLength", equalTo(content.length))
            .body("contentType", startsWith("application/octet-stream"));
    }

    @Test
    void aFileIsSentAsItsContent(@TempDir Path folder) throws IOException {
        Path file = Files.writeString(folder.resolve("payment.json"), validPaymentJson("16.00"));

        paymentsApi()
            .contentType(ContentType.JSON)
            .body(file.toFile())
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("amount", equalTo("16.00"));
    }
}
