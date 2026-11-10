package com.example.module_01.section_06;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import com.example.accounts.AccountsApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Module 1.6 – every test here fails on purpose, so you can read REST Assured's failure output.
 * <p>
 * The tag {@code expected-failure-tests} is excluded in the parent POM, so {@code mvn test} skips this class.
 * Run it explicitly:
 * <pre>mvn test -pl module-01 -Dtest=DeliberatelyFailingTest -DexcludedGroups=none</pre>
 * The build ends with {@code BUILD FAILURE} and five failed tests. That is the expected result.
 */
@Tag("expected-failure-tests")
class DeliberatelyFailingTest {

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
    void wrongStatusCode() {
        accountsApi().when().get("/accounts/{id}", "acc-9999").then().statusCode(200);
    }

    @Test
    void wrongBodyValue() {
        accountsApi().when().get("/accounts/{id}", "acc-1004").then().body("currency", equalTo("EUR"));
    }

    @Test
    void missingItemsInAList() {
        accountsApi().when().get("/accounts").then().body("accounts.id", hasItems("acc-1001", "acc-2001"));
    }

    @Test
    void wrongContentType() {
        accountsApi().when().get("/accounts").then().contentType(ContentType.XML);
    }

    @Test
    void severalFailuresInOneBodyCall() {
        accountsApi()
            .log().ifValidationFails()                          // the request is printed with the failure
        .when()
            .get("/accounts/{id}", "acc-1003")
        .then()
            .log().ifValidationFails()
            .statusCode(200)                                    // holds
            .body("status", equalTo("ACTIVE"),                  // fails: the account is BLOCKED
                  "balance", equalTo(-42.10),                   // fails: a double, but the value is read as a Float
                  "owner", equalTo("Jan Jansen"));              // holds
    }
}
