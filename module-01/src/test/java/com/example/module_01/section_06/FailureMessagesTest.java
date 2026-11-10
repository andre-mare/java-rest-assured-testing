package com.example.module_01.section_06;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.accounts.AccountsApi;

import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.6 – what a failed expectation looks like, captured so the build stays green.
 * <p>
 * A failed expectation throws a plain {@link AssertionError}, so JUnit reports the test as failed like any other.
 * Two things are worth knowing:
 * <ul>
 *   <li>The message starts with how many expectations failed, then one paragraph per failure.</li>
 *   <li>After {@code then()}, each call such as {@code statusCode(..)} or {@code body(..)} is checked as soon as it
 *       is made, so a chain stops at its first failing call. The expectations inside <em>one</em>
 *       {@code body(path, matcher, path, matcher, ..)} call are checked together, so one run reports all of their
 *       mismatches. Module 5 shows the same for a whole response specification.</li>
 * </ul>
 * These tests catch the error with {@code assertThrows} and check its message. To see the same failures as real
 * red tests, run {@link DeliberatelyFailingTest}.
 */
class FailureMessagesTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApi() {
        api = AccountsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification accountsApi() {
        return given()
            .baseUri(api.baseUri())
            .port(api.port())
            .basePath(AccountsApi.BASE_PATH);
    }

    @Test
    void aWrongStatusCodeNamesExpectedAndActual() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            accountsApi()
            .when()
                .get("/accounts/{id}", "acc-1001")
            .then()
                .statusCode(404));

        String message = error.getMessage();
        assertTrue(message.startsWith("1 expectation failed."), message);
        assertTrue(message.contains("Expected status code <404> but was <200>"), message);
    }

    @Test
    void aWrongBodyValueNamesThePath() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            accountsApi()
            .when()
                .get("/accounts")
            .then()
                .body("count", equalTo(5)));

        String message = error.getMessage();
        assertTrue(message.contains("JSON path count doesn't match."), message);
        assertTrue(message.contains("Expected: <5>"), message);
        assertTrue(message.contains("Actual: <4>"), message);
    }

    @Test
    void aThenChainStopsAtTheFirstFailingCall() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            accountsApi()
            .when()
                .get("/accounts/{id}", "acc-1001")
            .then()
                .statusCode(200)                            // checked now: holds
                .header("Cache-Control", "max-age=60")      // checked now: fails and throws
                .body("owner", equalTo("Jan Jansen")));     // never reached

        String message = error.getMessage();
        assertTrue(message.startsWith("1 expectation failed."), message);
        assertTrue(message.contains("Expected header \"Cache-Control\""), message);
        assertFalse(message.contains("owner"), message);
    }

    @Test
    void allPathsOfOneBodyCallAreReportedTogether() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            accountsApi()
            .when()
                .get("/accounts/{id}", "acc-1001")
            .then()
                .body("owner", equalTo("Jan Jansen"),       // fails
                      "currency", equalTo("EUR"),           // holds
                      "type", equalTo("SAVINGS")));         // fails

        String message = error.getMessage();
        assertTrue(message.startsWith("2 expectations failed."), message);
        assertTrue(message.contains("JSON path owner doesn't match."), message);
        assertTrue(message.contains("JSON path type doesn't match."), message);
    }
}
