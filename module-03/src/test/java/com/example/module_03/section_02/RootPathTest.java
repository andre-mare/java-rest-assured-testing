package com.example.module_03.section_02;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.Matchers.equalTo;

import com.example.transactions.TransactionsApi;

import io.restassured.response.ValidatableResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.2 – root paths and path arguments, to stop repeating the start of every path.
 * <p>
 * {@code rootPath(..)} sets a prefix for the {@code body(..)} calls that follow it. {@code appendRootPath},
 * {@code detachRootPath} and {@code noRootPath} move it. {@code withArgs(..)} fills {@code %s} placeholders in a path,
 * so one path can be reused for different values.
 */
class RootPathTest {

    private static TransactionsApi api;

    @BeforeAll
    static void startApi() {
        api = TransactionsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static ValidatableResponse transactions() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
            .when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
            .then();
    }

    @Test
    void aRootPathPrefixesEveryPath() {
        transactions()
            .rootPath("transactions[2]")
            .body("id", equalTo("tx-3"))                                // transactions[2].id
            .body("category", equalTo("RENT"))
            .body("counterparty.name", equalTo("Woonstichting De Key"));
    }

    @Test
    void movingTheRootPath() {
        transactions()
            .rootPath("transactions[0]")
            .body("id", equalTo("tx-1"))
            .appendRootPath("counterparty")                             // now transactions[0].counterparty
            .body("name", equalTo("Acme B.V."))
            .detachRootPath("counterparty")                             // back to transactions[0]
            .body("category", equalTo("SALARY"))
            .noRootPath()                                               // back to the top of the body
            .body("currency", equalTo("EUR"));
    }

    @Test
    void pathArgumentsFillPlaceholders() {
        String categoryOfTransaction = "transactions.find { it.id == '%s' }.category";

        transactions()
            .body(categoryOfTransaction, withArgs("tx-1"), equalTo("SALARY"))
            .body(categoryOfTransaction, withArgs("tx-3"), equalTo("RENT"))
            .body(categoryOfTransaction, withArgs("tx-5"), equalTo("TRANSPORT"));
    }

    @Test
    void aRootPathWithAPlaceholder() {
        transactions()
            .rootPath("transactions.find { it.id == '%s' }")
            .body("counterparty.name", withArgs("tx-2"), equalTo("Albert Heijn"))
            .body("counterparty.name", withArgs("tx-6"), equalTo("Belastingdienst"));
    }
}
