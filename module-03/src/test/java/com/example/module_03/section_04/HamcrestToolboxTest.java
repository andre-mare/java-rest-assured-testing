package com.example.module_03.section_04;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.startsWith;

import com.example.transactions.TransactionsApi;

import io.restassured.response.ValidatableResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.4 – the Hamcrest matchers you will use most, grouped by what they check.
 * <p>
 * Choosing the right matcher makes both the test and its failure message clearer: {@code contains} says "exactly these,
 * in this order", {@code containsInAnyOrder} drops the order, {@code hasItems} says "at least these".
 */
class HamcrestToolboxTest {

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
    void collections() {
        transactions()
            .body("transactions.id", hasSize(6))
            .body("transactions.category", hasItem("RENT"))                              // at least this one
            .body("transactions.category", hasItems("RENT", "SALARY"))                   // at least these
            .body("transactions[0..2].id", contains("tx-1", "tx-2", "tx-3"))             // exactly these, in order
            .body("transactions[0..2].id", containsInAnyOrder("tx-3", "tx-1", "tx-2"))   // exactly these, any order
            .body("transactions.id", everyItem(startsWith("tx-")))
            .body("transactions.findAll { it.category == 'GAMBLING' }", empty());
    }

    @Test
    void maps() {
        transactions()
            .body("transactions[0]", hasKey("remittance-info"))
            .body("transactions[0].counterparty", hasEntry("name", "Acme B.V."))
            .body("balance", not(hasKey("available")));
    }

    @Test
    void strings() {
        transactions()
            .body("iban", startsWith("NL"))
            .body("iban", containsString("ABNA"))
            .body("iban", matchesPattern("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"))
            .body("iban", equalToIgnoringCase("nl91abna0417164300"));
    }

    @Test
    void combiningMatchers() {
        transactions()
            .body("balance", allOf(hasKey("opening"), hasKey("closing")))
            .body("currency", anyOf(equalTo("EUR"), equalTo("GBP")))
            .body("transactions.category", everyItem(oneOf("SALARY", "GROCERIES", "RENT", "TRANSPORT", "REFUND")))
            .body("transactions.type", not(hasItem("PENDING")));
    }
}
