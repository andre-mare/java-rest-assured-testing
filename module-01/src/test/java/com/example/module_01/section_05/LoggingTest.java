package com.example.module_01.section_05;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.accounts.AccountsApi;

import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.5 – seeing what was sent and what came back.
 * <p>
 * {@code log()} prints to standard out, which Surefire shows in the console and in the test report. Run this class
 * on its own and read the output next to the code:
 * <pre>mvn test -pl module-01 -Dtest=LoggingTest</pre>
 * Logging every request makes a large suite unreadable, so most real suites log only when a test fails, with
 * {@code ifValidationFails()}. Be careful with logs in shared build output: they include headers such as
 * {@code Authorization}. Module 5 shows how to hide them.
 */
class LoggingTest {

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
    void logTheWholeRequestAndResponse() {
        accountsApi()
            .log().all()                                // method, URI, parameters, headers, cookies and body
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .log().all()                                // status line, headers, cookies and body
            .statusCode(200);
    }

    @Test
    void logOnlyParts() {
        accountsApi()
            .queryParam("status", "ACTIVE")
            .log().method()
            .log().uri()                                // the final URL, with the query string
        .when()
            .get("/accounts")
        .then()
            .log().status()
            .log().headers()
            .log().body()                               // JSON is pretty-printed by default
            .statusCode(200);
    }

    @Test
    void logOnlyWhenSomethingFails() {
        // Nothing is printed for this test, because every expectation holds. Break one (expect "acc-1002") and
        // run it again: the request and the response are printed together with the failure.
        accountsApi()
            .log().ifValidationFails()
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .body("id", equalTo("acc-1001"));
    }

    @Test
    void logTheResponseOnlyForErrorStatusCodes() {
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-9999")
        .then()
            .log().ifError()                            // only for status codes of 400 and above
            .statusCode(404);
    }

    @Test
    void logTheResponseOnlyForOneStatusCode() {
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .log().ifStatusCodeIsEqualTo(500)           // not printed: the status is 200
            .statusCode(200);
    }
}
