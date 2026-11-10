package com.example.module_02.section_05;

import static com.example.module_02.support.SamplePayments.validPayment;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.example.payments.PaymentsApi;

import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Module 2.5 – every HTTP method on the payments resource.
 * <p>
 * Each test creates its own payment in {@code @BeforeEach}, so the tests can change or cancel it without affecting
 * each other.
 */
class HttpMethodsTest {

    private static PaymentsApi api;
    private String paymentId;

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
                .header("X-Request-ID", "methods");
    }

    @BeforeEach
    void createPayment() {
        paymentId = paymentsApi()
            .contentType(ContentType.JSON)
            .body(validPayment("20.00"))
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .extract().path("id");
    }

    @Test
    void getReadsIt() {
        paymentsApi().when().get("/payments/{id}", paymentId).then().statusCode(200).body("status", equalTo("RECEIVED"));
    }

    @Test
    void putReplacesIt() {
        Map<String, Object> replacement = validPayment("99.99");
        replacement.put("creditorName", "Emily Clarke");

        paymentsApi()
            .contentType(ContentType.JSON)
            .body(replacement)
        .when()
            .put("/payments/{id}", paymentId)
        .then()
            .statusCode(200)
            .body("amount", equalTo("99.99"))
            .body("creditorName", equalTo("Emily Clarke"));
    }

    @Test
    void patchChangesPartOfIt() {
        paymentsApi()
            .contentType("application/merge-patch+json")            // RFC 7396 JSON Merge Patch
            .body(Map.of("reference", "Rent March"))
        .when()
            .patch("/payments/{id}", paymentId)
        .then()
            .statusCode(200)
            .body("reference", equalTo("Rent March"))
            .body("amount", equalTo("20.00"));                       // untouched
    }

    @Test
    void deleteCancelsIt() {
        paymentsApi().when().delete("/payments/{id}", paymentId).then().statusCode(204).body(emptyString());

        paymentsApi().when().get("/payments/{id}", paymentId).then().body("status", equalTo("CANCELLED"));

        paymentsApi()                                                // a cancelled payment can't be replaced
            .contentType(ContentType.JSON)
            .body(validPayment("1.00"))
        .when()
            .put("/payments/{id}", paymentId)
        .then()
            .statusCode(409);
    }

    @Test
    void headReturnsTheHeadersOnly() {
        paymentsApi()
        .when()
            .head("/payments/{id}", paymentId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(emptyString());
    }

    @Test
    void optionsListsTheAllowedMethods() {
        paymentsApi()
        .when()
            .options("/payments/{id}", paymentId)
        .then()
            .statusCode(204)
            .header("Allow", containsString("PATCH"))
            .header("Allow", containsString("DELETE"));
    }

    @Test
    void queryIsASafeMethodWithABody() {
        // QUERY (an IETF draft, supported since REST Assured 6.1) is like GET, but the search criteria travel in the
        // body instead of the URL, so they can be large and are kept out of access logs.
        paymentsApi()
            .contentType(ContentType.JSON)
            .body(Map.of("currency", "EUR", "minAmount", "20.00"))
        .when()
            .query("/payments")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(1))
            .body("payments.currency", everyItem(equalTo("EUR")));
    }

    @Test
    void anyMethodByName() {
        paymentsApi().when().request(Method.GET, "/payments/{id}", paymentId).then().statusCode(200);

        paymentsApi()                                                // a custom method, such as a cache PURGE
        .when()
            .request("PURGE", "/echo")
        .then()
            .body("method", equalTo("PURGE"));
    }

    @Test
    void aMethodTheResourceDoesntSupport() {
        paymentsApi().when().post("/payments/{id}", paymentId).then().statusCode(405);
    }
}
