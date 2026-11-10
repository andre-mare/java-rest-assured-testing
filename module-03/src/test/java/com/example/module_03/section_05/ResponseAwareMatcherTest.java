package com.example.module_03.section_05;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.endsWithPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

import com.example.transactions.TransactionsApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.5 – using one part of the response to check another.
 * <p>
 * When the server chooses a value, such as the id of a new resource, the test can't know it in advance. A
 * {@code ResponseAwareMatcher} is a function from the response to a matcher, so the expectation can use the actual id.
 * This class adds transactions, so it starts its own API to keep the data of other classes unchanged.
 */
class ResponseAwareMatcherTest {

    private static TransactionsApi api;

    @BeforeAll
    static void startApi() {
        api = TransactionsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification newTransaction() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
            .contentType(ContentType.JSON)
            .body(Map.of("amount", -3.5, "counterpartyName", "Bakkerij Bart"));
    }

    @Test
    void theLocationHeaderEndsWithTheNewId() {
        newTransaction()
        .when()
            .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then()
            .statusCode(201)
            .header("Location", response -> endsWith("/transactions/" + response.path("id")));
    }

    @Test
    void readyMadeResponseAwareMatchers() {
        newTransaction()
        .when()
            .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then()
            .header("Location", endsWithPath("id"));                     // the same check, from RestAssuredMatchers
    }

    @Test
    void aBodyValueCheckedAgainstAnotherBodyValue() {
        newTransaction()
        .when()
            .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then()
            .body("type", response -> equalTo(response.<Float>path("amount") < 0 ? "DEBIT" : "CREDIT"));
    }

    @Test
    void theWholeBody() {
        newTransaction()
        .when()
            .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then()
            .body(containsString("Bakkerij Bart"));                      // a matcher without a path sees the body text
    }
}
