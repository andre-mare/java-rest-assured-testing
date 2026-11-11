package com.example.module_08.section_05;

import static io.restassured.RestAssured.given;
import static io.restassured.config.FailureConfig.failureConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.signing.SignedPaymentsApi;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.listener.ResponseValidationFailureListener;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 8.5 – parsers for responses with the wrong content type, and listeners that react to failed validations.
 * <p>
 * REST Assured picks a parser for body paths from the response's content type. {@code /api/v1/legacy/fx-rates}
 * sends JSON as {@code text/plain}, so by default a path such as {@code rates.GBP} can't be read. Telling REST
 * Assured to parse {@code text/plain} as JSON fixes that, per response or in a response specification.
 */
class ParsersAndListenersTest {

    private static SignedPaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = SignedPaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification legacy() {
        return given().baseUri(api.baseUri()).port(api.port());
    }

    @Test
    void withoutHelpTheBodyIsJustText() {
        legacy()
        .when()
            .get("/api/v1/legacy/fx-rates")
        .then()
            .contentType("text/plain")
            .body(equalTo("{\"base\":\"EUR\",\"rates\":{\"GBP\":0.85,\"USD\":1.09,\"SEK\":11.42}}"));
    }

    @Test
    void parseTextAsJsonForOneResponse() {
        legacy()
        .when()
            .get("/api/v1/legacy/fx-rates")
        .then()
            .parser("text/plain", Parser.JSON)
            .body("base", equalTo("EUR"))
            .body("rates.USD", equalTo(1.09f));
    }

    @Test
    void orInAResponseSpecification() {
        // A parser registered in a specification applies to the specification's own expectations. Merging it with
        // spec(..) doesn't carry the parser over, so a body path chained after spec(..) would still see text/plain.
        // Put the body expectations in the specification, or use parser(..) on the response.
        ResponseSpecification legacyJson = new ResponseSpecBuilder()
            .registerParser("text/plain", Parser.JSON)
            .expectStatusCode(200)
            .expectBody("rates.SEK", equalTo(11.42f))
            .build();

        legacy()
        .when()
            .get("/api/v1/legacy/fx-rates")
        .then()
            .spec(legacyJson);
    }

    @Test
    void aFailureListenerSeesTheResponseOfAFailedTest() {
        // Attach evidence to a failure: report the response to a test report, a ticket or a screenshot-like file.
        List<String> evidence = new ArrayList<>();
        ResponseValidationFailureListener collectEvidence =
                (requestSpec, responseSpec, response) -> evidence.add(response.statusCode() + " " + response.asString());

        assertThrows(AssertionError.class, () ->
            legacy()
                .config(config().failureConfig(failureConfig().failureListeners(collectEvidence)))
            .when()
                .get("/api/v1/legacy/fx-rates")
            .then()
                .statusCode(500));                                   // fails: the status is 200

        assertEquals(1, evidence.size());
        assertEquals("200 {\"base\":\"EUR\",\"rates\":{\"GBP\":0.85,\"USD\":1.09,\"SEK\":11.42}}", evidence.getFirst());
    }
}
