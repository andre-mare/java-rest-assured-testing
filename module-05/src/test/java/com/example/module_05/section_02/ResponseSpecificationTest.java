package com.example.module_05.section_02;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.openbanking.OpenBankingApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 5.2 – a {@link ResponseSpecification} holds expectations that many responses share.
 * <p>
 * Here: every successful response is JSON and echoes the {@code x-fapi-interaction-id} that the request sent, and every
 * error is an RFC 9457 problem. A response specification is checked as a whole, so all its failed expectations are
 * reported together, unlike a chain of separate {@code then()} calls (Module 1, section 6).
 */
class ResponseSpecificationTest {

    private static OpenBankingApi api;
    private static RequestSpecification openBanking;
    private static ResponseSpecification success;
    private static ResponseSpecification problem;

    @BeforeAll
    static void startApiAndBuildSpecifications() {
        api = OpenBankingApi.start();

        openBanking = new RequestSpecBuilder()
                .setBaseUri(api.baseUri()).setPort(api.port()).setBasePath(OpenBankingApi.BASE_PATH)
                .addHeader("Authorization", "Bearer test-token")
                .build();

        success = new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectContentType(ContentType.JSON)
                .expectHeader("x-fapi-interaction-id", matchesPattern("[0-9a-f-]{36}"))
                // A response-aware header expectation (REST Assured 6.1+): the length must match the actual body
                .expectHeader("Content-Length", response -> equalTo(String.valueOf(response.asByteArray().length)))
                .expectBody("Links.Self", notNullValue())
                .build();

        problem = new ResponseSpecBuilder()
                .expectContentType("application/problem+json")
                .expectBody("title", notNullValue())
                .expectBody("status", notNullValue())
                .build();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification request() {
        return given(openBanking).header("x-fapi-interaction-id", UUID.randomUUID().toString());
    }

    @Test
    void thenSpec() {
        request()
        .when()
            .get("/accounts")
        .then()
            .spec(success)
            .body("Data.Account", hasSize(3));                       // plus what is special about this response
    }

    @Test
    void bothSpecificationsInGiven() {
        // given(request, response) returns a RequestSender: the request is complete, so anything per-request (here the
        // interaction id) must already be in the request specification.
        RequestSpecification withInteractionId = new RequestSpecBuilder()
            .addRequestSpecification(openBanking)
            .addHeader("x-fapi-interaction-id", UUID.randomUUID().toString())
            .build();

        given(withInteractionId, success)
            .get("/accounts/{id}/balances", "31820")                 // a RequestSender has no when(): send directly
        .then()
            .body("Data.Balance[0].Amount.Amount", equalTo("15000.00"));
    }

    @Test
    void errorsShareAShape() {
        request()
        .when()
            .get("/accounts/{id}", "99999")
        .then()
            .statusCode(404)
            .spec(problem);
    }

    @Test
    void aSpecificationReportsAllItsFailuresAtOnce() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            request()
            .when()
                .get("/accounts/{id}", "99999")                     // a 404 problem, not a success
            .then()
                .spec(success));

        String message = error.getMessage();
        // A problem+json body still counts as JSON and still echoes the interaction id, so two expectations fail:
        assertTrue(message.startsWith("2 expectations failed."), message);   // the status and Links.Self
        assertTrue(message.contains("Expected status code <200> but was <404>"), message);
        assertTrue(message.contains("Links.Self"), message);
    }
}
