package com.example.module_01.section_04;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import com.example.accounts.AccountsApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.4 – checking the status, the headers and the body of a response.
 * <p>
 * Every check after {@code then()} takes either a plain value or a Hamcrest {@code Matcher}. REST Assured brings
 * Hamcrest with it, so {@code equalTo}, {@code hasItems}, {@code containsString} and friends are always available.
 * Module 3 goes deeper into body paths and matchers.
 */
class FirstAssertionsTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApi() {
        api = AccountsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    /** The parts of the request that every test in this class shares. Module 5 turns this into a reusable spec. */
    private static RequestSpecification accountsApi() {
        return given()
            .baseUri(api.baseUri())
            .port(api.port())
            .basePath(AccountsApi.BASE_PATH);
    }

    // ----------------------------------------------------------------------------------------- status

    @Test
    void statusCodeAndStatusLine() {
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .statusCode(lessThan(300))                      // any matcher works, not only a number
            .statusLine("HTTP/1.1 200 OK")                  // the full first line of the response
            .statusLine(containsString("OK"));
    }

    @Test
    void anUnknownAccountGives404() {
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-9999")
        .then()
            .statusCode(404);
    }

    // ----------------------------------------------------------------------------------------- content type and headers

    @Test
    void contentType() {
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .contentType(ContentType.JSON)                  // ignores parameters such as "; charset=utf-8"
            .contentType("application/json");               // a String must be how the actual value starts
    }

    @Test
    void headers() {
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .header("Cache-Control", "no-store")
            .header("X-Request-Id", notNullValue())
            .header("X-Request-Id", matchesPattern("[0-9a-f-]{36}"));   // a UUID
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        // The JDK server sends "Cache-control" and "X-request-id". HTTP header names are case-insensitive,
        // and so is REST Assured, so any spelling of the name matches.
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .header("cache-control", "no-store")
            .header("CACHE-CONTROL", "no-store");
    }

    @Test
    void aRequestHeaderIsEchoedBack() {
        accountsApi()
            .header("X-Correlation-Id", "test-42")         // a header on the request (more in Module 2)
        .when()
            .get("/accounts")
        .then()
            .header("X-Correlation-Id", "test-42");
    }

    // ----------------------------------------------------------------------------------------- body

    @Test
    void singleValuesInTheBody() {
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .body("id", equalTo("acc-1001"))
            .body("iban", equalTo("NL91ABNA0417164300"))
            .body("status", is("ACTIVE"))                   // is(..) only makes the sentence read better
            .body("openedOn", equalTo("2019-04-01"));       // a LocalDate arrives as an ISO-8601 string
    }

    @Test
    void severalPathsInOneBodyCall() {
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-1003")
        .then()
            .body("owner", equalTo("Jan Jansen"),
                  "status", equalTo("BLOCKED"),
                  "currency", equalTo("EUR"));
    }

    @Test
    void valuesInsideAList() {
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .body("count", equalTo(4))
            .body("accounts", hasSize(4))
            .body("accounts.size()", equalTo(4))                        // GPath can call size() itself
            .body("accounts[0].id", equalTo("acc-1001"))                // index into the list
            .body("accounts[-1].id", equalTo("acc-1004"))               // negative index counts from the end
            .body("accounts.id", hasItems("acc-1002", "acc-1003"))      // "accounts.id" collects every id
            .body("accounts.currency", everyItem(is(notNullValue())));
    }

    @Test
    void numbersAreFloatsByDefault() {
        // JSON has one number type. REST Assured reads a decimal such as 1250.50 as a Float (or a Double when it
        // doesn't fit), so the expected value needs the f suffix. Comparing with 1250.50 (a double) fails.
        // Module 3 shows how to read decimals as BigDecimal instead, which is what money needs.
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .body("balance", equalTo(1250.50f));
    }

    @Test
    void queryParametersFilterTheList() {
        accountsApi()
            .queryParam("currency", "GBP")                  // sent as ?currency=GBP (more in Module 2)
        .when()
            .get("/accounts")
        .then()
            .body("count", equalTo(1))
            .body("accounts[0].owner", equalTo("Emily Clarke"));
    }

    // ----------------------------------------------------------------------------------------- errors

    @Test
    void errorsAreProblemDetails() {
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-9999")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")        // RFC 9457
            .contentType(ContentType.JSON)                  // any "+json" type counts as JSON, so body paths work
            .body("title", equalTo("Not Found"))
            .body("status", equalTo(404))
            .body("detail", containsString("acc-9999"));
    }

    @Test
    void unsupportedMethodsGive405WithAnAllowHeader() {
        accountsApi()
        .when()
            .delete("/accounts/{id}", "acc-1001")
        .then()
            .statusCode(405)
            .header("Allow", "GET");
    }

    // ----------------------------------------------------------------------------------------- time

    @Test
    void responseTime() {
        // Response time is measured on the client, so it includes the network. Use it for generous upper bounds
        // that catch something badly wrong, not for performance testing.
        accountsApi()
        .when()
            .get("/accounts")
        .then()
            .time(lessThan(2L), TimeUnit.SECONDS);
    }
}
