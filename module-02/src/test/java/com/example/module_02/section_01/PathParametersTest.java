package com.example.module_02.section_01;

import static com.example.module_02.support.SamplePayments.validPaymentJson;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.payments.PaymentsApi;

import io.restassured.specification.RequestSpecification;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 2.1 – path parameters fill the {@code {placeholders}} of a path.
 * <p>
 * The {@code /echo} endpoints answer with a description of the request as the server received it, including the
 * decoded {@code path} and the {@code rawPath} as it was on the wire. That makes it easy to see what REST Assured sent.
 */
class PathParametersTest {

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
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH);
    }

    @Test
    void namedPathParameter() {
        paymentsApi()
            .pathParam("id", "pay-42")
        .when()
            .get("/echo/{id}")
        .then()
            .body("path", equalTo("/api/v1/echo/pay-42"));
    }

    @Test
    void severalNamedPathParametersFromAMap() {
        paymentsApi()
            .pathParams(Map.of("resource", "payments", "id", "pay-42"))
        .when()
            .get("/echo/{resource}/{id}")
        .then()
            .body("path", equalTo("/api/v1/echo/payments/pay-42"));
    }

    @Test
    void unnamedPathParametersAreFilledInOrder() {
        paymentsApi()
        .when()
            .get("/echo/{resource}/{id}", "payments", "pay-42")     // the names don't matter, the order does
        .then()
            .body("path", equalTo("/api/v1/echo/payments/pay-42"));
    }

    @Test
    void namedAndUnnamedCanBeMixed() {
        paymentsApi()
            .pathParam("resource", "payments")                      // named ones are filled first ...
        .when()
            .get("/echo/{resource}/{id}", "pay-42")                 // ... the rest by position
        .then()
            .body("path", equalTo("/api/v1/echo/payments/pay-42"));
    }

    @Test
    void valuesAreUrlEncoded() {
        paymentsApi()
        .when()
            .get("/echo/{name}", "Anna de Vries")
        .then()
            .body("rawPath", equalTo("/api/v1/echo/Anna%20de%20Vries"))   // on the wire
            .body("path", equalTo("/api/v1/echo/Anna de Vries"));         // after the server decoded it
    }

    @Test
    void aRealResource() {
        String location = paymentsApi()
            .header("X-Request-ID", "path-params-1")
            .contentType("application/json")
            .body(validPaymentJson("10.00"))
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .extract().header("Location");                          // /api/v1/payments/pay-N (Module 4 covers extract)

        String id = location.substring(location.lastIndexOf('/') + 1);

        paymentsApi()
        .when()
            .get("/payments/{id}", id)
        .then()
            .statusCode(200)
            .body("id", equalTo(id));
    }
}
