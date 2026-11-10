package com.example.module_03.section_03;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import com.example.transactions.TransactionsApi;

import io.restassured.common.config.NumberReturnType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.3 – numbers, and why money needs {@link BigDecimal}.
 * <p>
 * JSON has one number type. REST Assured turns a whole number into an {@code Integer} (or {@code Long} when it is too
 * big) and, by default, a decimal into a {@code Float} (or a {@code Double} when it is too big). A float can't hold
 * most decimal amounts exactly, so for money set {@link NumberReturnType#BIG_DECIMAL} and compare with
 * {@code comparesEqualTo}, which ignores the scale: {@code 1000} and {@code 1000.00} compare equal.
 */
class NumbersAndMoneyTest {

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

    private static ValidatableResponse transactions(RequestSpecification request) {
        return request.when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID).then();
    }

    /** Decimals are read as BigDecimal for every request sent with this specification. */
    private static RequestSpecification withBigDecimals() {
        return transactionsApi().config(config().jsonConfig(jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL)));
    }

    @Test
    void wholeNumbersAreIntegers() {
        transactions(transactionsApi()).body("transactions.size()", equalTo(6));
    }

    @Test
    void decimalsAreFloatsByDefault() {
        transactions(transactionsApi())
            .body("transactions[1].amount", instanceOf(Float.class))
            .body("transactions[1].amount", equalTo(-42.35f))
            .body("transactions[1].amount", not(equalTo(-42.35)));      // a Double never equals a Float
    }

    @Test
    void decimalsAsBigDecimal() {
        transactions(withBigDecimals())
            .body("transactions[1].amount", instanceOf(BigDecimal.class))
            .body("transactions[1].amount", equalTo(new BigDecimal("-42.35")))
            .body("balance.closing", equalTo(new BigDecimal("2407.65")));
    }

    @Test
    void equalToIsStrictAboutScaleButComparesEqualToIsNot() {
        transactions(withBigDecimals())
            .body("balance.opening", not(equalTo(new BigDecimal("1000"))))   // 1000.00 has scale 2, 1000 has scale 0
            .body("balance.opening", comparesEqualTo(new BigDecimal("1000")));
    }

    @Test
    void sumsAreExactWithBigDecimal() {
        transactions(withBigDecimals())
            .body("transactions.findAll { it.type == 'DEBIT' }.amount.sum()", comparesEqualTo(new BigDecimal("-1242.35")))
            .body("transactions.findAll { it.type == 'CREDIT' }.amount.sum()", comparesEqualTo(new BigDecimal("2650")));
    }

    @Test
    void theNetMovementIsExact() {
        // 2500.00 - 42.35 - 1100.00 - 60.00 - 40.00 + 150.00: with floats this sum would be off by a tiny fraction
        transactions(withBigDecimals())
            .body("transactions.amount.sum()", comparesEqualTo(new BigDecimal("1407.65")));
    }

    @Test
    void closeToForValuesThatAreApproximateOnPurpose() {
        // For a measurement rather than money, read doubles and allow a tolerance.
        transactions(transactionsApi().config(config().jsonConfig(jsonConfig().numberReturnType(NumberReturnType.DOUBLE))))
            .body("transactions[1].amount", closeTo(-42.35, 0.001));
    }
}
