package com.example.module_03.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.example.transactions.TransactionsApi;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.1 – GPath, the path language of {@code body(..)}, on a list of transactions.
 * <p>
 * GPath comes from Groovy. A path such as {@code transactions.id} walks into every element of a list, and closures in
 * braces filter or transform it: {@code it} is the current element. The six transactions of {@code acc-1001}:
 * <pre>
 *   tx-1  2026-03-01  CREDIT   2500.00  Acme B.V.             SALARY
 *   tx-2  2026-03-02  DEBIT     -42.35  Albert Heijn          GROCERIES
 *   tx-3  2026-03-05  DEBIT   -1100.00  Woonstichting De Key  RENT
 *   tx-4  2026-03-09  DEBIT     -60.00  Albert Heijn          GROCERIES
 *   tx-5  2026-03-15  DEBIT     -40.00  NS Reizigers          TRANSPORT
 *   tx-6  2026-03-20  CREDIT    150.00  Belastingdienst       REFUND
 * </pre>
 */
class JsonGPathTest {

    private static TransactionsApi api;

    @BeforeAll
    static void startApi() {
        api = TransactionsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification transactionsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH);
    }

    private static ValidatableResponse transactions() {
        return transactionsApi().when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID).then();
    }

    @Test
    void findReturnsTheFirstMatch() {
        transactions().body("transactions.find { it.category == 'RENT' }.counterparty.name",
                equalTo("Woonstichting De Key"));
    }

    @Test
    void findAllReturnsEveryMatch() {
        transactions().body("transactions.findAll { it.type == 'DEBIT' }.id",
                contains("tx-2", "tx-3", "tx-4", "tx-5"));
    }

    @Test
    void conditionsCanLookIntoNestedObjects() {
        transactions().body("transactions.findAll { it.counterparty.name == 'Albert Heijn' }.size()", equalTo(2));
    }

    @Test
    void collectTransformsEveryElement() {
        transactions().body("transactions.collect { it.counterparty.name }.unique().size()", equalTo(5));
    }

    @Test
    void anyAndEvery() {
        transactions()
            .body("transactions.any { it.category == 'RENT' }", is(true))
            .body("transactions.every { it.id.startsWith('tx-') }", is(true));
    }

    @Test
    void maxAndMinCompareByAClosure() {
        transactions()
            .body("transactions.max { it.amount }.id", equalTo("tx-1"))
            .body("transactions.min { it.amount }.counterparty.name", equalTo("Woonstichting De Key"));
    }

    @Test
    void indexesAndRanges() {
        transactions()
            .body("transactions[0].id", equalTo("tx-1"))
            .body("transactions[-1].id", equalTo("tx-6"))               // from the end
            .body("transactions[0..1].id", contains("tx-1", "tx-2"))    // a range, both ends included
            .body("transactions[-2..-1].id", contains("tx-5", "tx-6"));
    }

    @Test
    void listsOfListsCanBeFlattened() {
        transactions()
            .body("transactions.tags", hasSize(6))                      // one list of tags per transaction
            .body("transactions.tags.flatten().unique()", hasItems("income", "food", "housing", "tax"));
    }

    @Test
    void keysWithADash() {
        transactions()
            .body("transactions[0].remittance-info", equalTo("Salary March"))
            .body("transactions[0].'remittance-info'", equalTo("Salary March"));   // quoting works too
    }

    @Test
    void theServerCanFilterToo() {
        transactionsApi()
            .queryParam("type", "credit")
        .when()
            .get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then()
            .body("transactions.type", everyItem(equalTo("CREDIT")))
            .body("transactions.id", contains("tx-1", "tx-6"));
    }

    @Test
    void aJsonArrayAtTheRoot() {
        // GET /categories returns [ {"code":"SALARY",..}, .. ]: the body itself is the list.
        transactionsApi()
        .when()
            .get("/categories")
        .then()
            .body("$", hasSize(5))                                      // "$" is the root
            .body("size()", equalTo(5))
            .body("code", hasItems("SALARY", "RENT"))                   // a path on the root list walks into every element
            .body("find { it.code == 'RENT' }.label", equalTo("Rent"));
    }
}
