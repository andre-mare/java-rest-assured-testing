package com.example.module_08.section_03;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.example.module_08.support.CorrelationIdFilter;
import com.example.module_08.support.HttpMessageSignatureFilter;
import com.example.signing.SignedPaymentsApi;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 8.3 – signing every request in one place, and proving the server checks the signature.
 * <p>
 * The {@link HttpMessageSignatureFilter} adds {@code Content-Digest}, {@code Signature-Input} and {@code Signature}.
 * The negative tests matter as much as the positive ones: a server that accepts a tampered, stale or wrongly signed
 * request makes the signature worthless.
 */
class RequestSigningTest {

    private static final String PAYMENT = "{\"creditorIban\": \"NL69INGB0123456789\", \"amount\": \"100.00\"}";

    private static SignedPaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = SignedPaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification api() {
        return given().baseUri(api.baseUri()).port(api.port()).filter(new CorrelationIdFilter());
    }

    private static HttpMessageSignatureFilter signer() {
        return new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET);
    }

    @Test
    void aSignedPostIsAccepted() {
        api()
            .filter(signer())
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(201)
            .body("amount", equalTo("100.00"));
    }

    @Test
    void aSignedGetIsAccepted() {
        String id = api().filter(signer()).contentType(ContentType.JSON).body(PAYMENT)
            .when().post("/api/v1/payments")
            .then().statusCode(201).extract().path("paymentId");

        api().filter(signer()).when().get("/api/v1/payments/{id}", id).then().statusCode(200);
    }

    @Test
    void anUnsignedRequestIsRejected() {
        api()
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", startsWith("Signature"));
    }

    @Test
    void aBodyChangedAfterSigningIsRejected() {
        api()
            .filter(signer())
            .filter(new TamperInTransit())                           // runs after the signer
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(401)
            .body("detail", equalTo("Content-Digest doesn't match the body"));
    }

    @Test
    void aSignatureWithTheWrongSecretIsRejected() {
        api()
            .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, "not-the-secret"))
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(401)
            .body("detail", equalTo("The signature is invalid"));
    }

    @Test
    void anOldSignatureIsRejected() {
        Clock tenMinutesAgo = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-10));

        api()
            .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET, tenMinutesAgo))
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(401)
            .body("detail", containsString("too old"));
    }

    /** Changes the amount after the request was signed, as an attacker in the middle would. */
    private static final class TamperInTransit implements OrderedFilter {
        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE;
        }

        @Override
        public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                               FilterContext context) {
            request.body(request.<String>getBody().replace("100.00", "100000.00"));
            return context.next(request, responseSpec);
        }
    }
}
