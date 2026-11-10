package com.example.module_02.section_02;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.startsWith;

import com.example.payments.PaymentsApi;

import io.restassured.specification.RequestSpecification;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 2.2 – query parameters, form parameters and the catch-all {@code param}.
 * <ul>
 *   <li>{@code queryParam} always goes into the URL, after the {@code ?}.</li>
 *   <li>{@code formParam} always goes into the body, as {@code application/x-www-form-urlencoded}.</li>
 *   <li>{@code param} picks for you: the query string for GET, a form body for POST without another body.</li>
 * </ul>
 * The echo endpoint returns {@code query} as a map from name to the list of values, and {@code rawQuery} as sent.
 */
class ParametersTest {

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

    // ------------------------------------------------------------------------------------------- query

    @Test
    void oneQueryParameter() {
        paymentsApi()
            .queryParam("status", "RECEIVED")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("status=RECEIVED"))
            .body("query.status", contains("RECEIVED"));
    }

    @Test
    void severalQueryParametersFromAMap() {
        paymentsApi()
            .queryParams(Map.of("status", "RECEIVED", "currency", "EUR"))
        .when()
            .get("/echo")
        .then()
            .body("query.status", contains("RECEIVED"))
            .body("query.currency", contains("EUR"));
    }

    @Test
    void aMultiValueQueryParameterRepeatsTheName() {
        paymentsApi()
            .queryParam("status", "RECEIVED", "CANCELLED")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("status=RECEIVED&status=CANCELLED"))
            .body("query.status", contains("RECEIVED", "CANCELLED"));
    }

    @Test
    void aQueryParameterWithoutAValue() {
        paymentsApi()
            .queryParam("verbose")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("verbose"));
    }

    @Test
    void javaTimeValuesAreSentAsIso8601() {
        // Since REST Assured 6.1, java.time values are sent with their toString(), not through the JSON mapper.
        paymentsApi()
            .queryParam("from", LocalDate.of(2026, 1, 31))
        .when()
            .get("/echo")
        .then()
            .body("query.from", contains("2026-01-31"));
    }

    @Test
    void queryParametersFilterRealData() {
        paymentsApi()
            .queryParam("currency", "SEK")                              // nobody pays in SEK in this module
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("count", equalTo(0));
    }

    // ------------------------------------------------------------------------------------------- encoding

    @Test
    void valuesAreUrlEncodedByDefault() {
        paymentsApi()
            .queryParam("name", "Anna de Vries & Co")
        .when()
            .get("/echo")
        .then()
            .body("query.name", contains("Anna de Vries & Co"));         // the server decodes it back
    }

    @Test
    void anAlreadyEncodedValueIsEncodedAgain() {
        paymentsApi()
            .queryParam("q", "a%20b")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("q=a%2520b"));                   // % itself became %25
    }

    @Test
    void urlEncodingCanBeTurnedOff() {
        paymentsApi()
            .urlEncodingEnabled(false)                                  // you are responsible for encoding now
            .queryParam("q", "a%20b")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("q=a%20b"))
            .body("query.q", contains("a b"));
    }

    // ------------------------------------------------------------------------------------------- form and param

    @Test
    void formParametersBecomeAUrlEncodedBody() {
        paymentsApi()
            .formParam("name", "Jan Jansen")
            .formParam("iban", "NL69INGB0123456789")
        .when()
            .post("/echo")
        .then()
            .body("contentType", startsWith("application/x-www-form-urlencoded"))
            .body("rawQuery", equalTo(""))                              // nothing in the URL
            // REST Assured encodes a space as %20, not as +, because some servers decode + wrongly
            .body("body", equalTo("name=Jan%20Jansen&iban=NL69INGB0123456789"));
    }

    @Test
    void aFormCreatesABeneficiary() {
        paymentsApi()
            .formParam("name", "Jan & Piet")
            .formParam("iban", "NL69INGB0123456789")
        .when()
            .post("/beneficiaries")
        .then()
            .statusCode(201)
            .body("name", equalTo("Jan & Piet"))                        // encoded on the way, decoded by the server
            .body("$", not(hasKey("nickname")));                        // "$" is the whole body
    }

    @Test
    void paramGoesIntoTheQueryForGet() {
        paymentsApi()
            .param("status", "RECEIVED")
        .when()
            .get("/echo")
        .then()
            .body("rawQuery", equalTo("status=RECEIVED"))
            .body("bodyLength", equalTo(0));
    }

    @Test
    void paramGoesIntoAFormBodyForPost() {
        paymentsApi()
            .param("status", "RECEIVED")
        .when()
            .post("/echo")
        .then()
            .body("rawQuery", equalTo(""))
            .body("body", equalTo("status=RECEIVED"));
    }

    @Test
    void everyValueOfAQueryParameterIsChecked() {
        paymentsApi()
            .queryParam("status", "RECEIVED", "RECEIVED")
        .when()
            .get("/echo")
        .then()
            .body("query.status", everyItem(equalTo("RECEIVED")));
    }
}
