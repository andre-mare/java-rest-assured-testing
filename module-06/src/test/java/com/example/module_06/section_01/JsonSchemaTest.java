package com.example.module_06.section_01;

import static com.example.module_06.support.SamplePayments.domesticPayment;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pisp.PaymentInitiationApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 6.1 – checking the shape of a response with JSON Schema.
 * <p>
 * A value check such as {@code body("status", equalTo("RCVD"))} tests one field. A schema tests all of them: which
 * fields exist, their types and formats, and (with {@code additionalProperties: false}) that nothing unexpected was
 * added. The schemas in {@code src/test/resources/schemas} declare draft 2020-12 with {@code $schema}; since REST
 * Assured 6.1 such schemas are validated with the networknt validator, so features such as {@code $defs},
 * {@code const} and {@code if}/{@code then}/{@code else} work.
 */
class JsonSchemaTest {

    private static final String PAYMENT_SCHEMA = "schemas/payment.schema.json";

    private static PaymentInitiationApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentInitiationApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification pisp() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentInitiationApi.BASE_PATH);
    }

    private static String createPayment(String instructionId) {
        return pisp().contentType(ContentType.JSON).body(domesticPayment(instructionId, "25.00"))
            .when().post("/domestic-payments")
            .then().statusCode(201)
            .extract().path("paymentId");
    }

    @Test
    void aCreatedPaymentMatchesTheSchema() {
        pisp()
            .contentType(ContentType.JSON)
            .body(domesticPayment("INV-1", "25.00"))
        .when()
            .post("/domestic-payments")
        .then()
            .statusCode(201)
            .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
    }

    @Test
    void aReadPaymentMatchesTheSameSchema() {
        String id = createPayment("INV-2");

        pisp()
        .when()
            .get("/domestic-payments/{id}", id)
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
    }

    @Test
    void theSchemaCatchesAResponseThatBreaksTheContract() {
        // ?format=v0 returns an old shape: a number for the amount, a status outside the enum, missing fields.
        // Every value test that only reads paymentId would still pass. The schema doesn't.
        String id = createPayment("INV-3");

        AssertionError error = assertThrows(AssertionError.class, () ->
            pisp()
                .queryParam("format", "v0")
            .when()
                .get("/domestic-payments/{id}", id)
            .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA)));

        assertThat(error.getMessage(), containsString("_links"));       // the report names what is wrong
    }

    @Test
    void anInlineSchemaForAQuickCheck() {
        String id = createPayment("INV-4");

        pisp()
        .when()
            .get("/domestic-payments/{id}", id)
        .then()
            // The schema matcher reads the whole body as text, so it can't be combined with a path.
            .body(matchesJsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["amount"],
                  "properties": {
                    "amount": {
                      "properties": {
                        "value": { "type": "string", "pattern": "^[0-9]+\\\\.[0-9]{2}$" },
                        "currency": { "const": "EUR" }
                      }
                    }
                  }
                }
                """));
    }

    @Test
    void validatingJsonWithoutAnHttpCall() {
        // The matcher is a plain Hamcrest matcher on a String, so it can check test data, stored examples or
        // messages from a queue, too.
        String example = """
            {"paymentId": "pmt-1", "instructionId": "X", "status": "UNKNOWN",
             "debtor": {"iban": "NL91ABNA0417164300"}, "creditor": {"iban": "NL69INGB0123456789", "name": "Jan"},
             "amount": {"value": "1.00", "currency": "EUR"}, "createdAt": "2026-03-01T10:00:00Z",
             "_links": {"self": {"href": "/pisp/v1/domestic-payments/pmt-1"}}}
            """;

        assertThat(example, not(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA)));         // UNKNOWN isn't a status
        assertThat(example.replace("UNKNOWN", "RCVD"), matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
    }
}
