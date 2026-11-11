package com.example.module_09.section_01;

import static com.example.module_09.support.PaymentsClient.newPayment;
import static com.example.module_09.support.PaymentsClient.validPayment;
import static org.hamcrest.Matchers.equalTo;

import com.example.lifecycle.PaymentLifecycleApi;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Module 9.1 – a whole class repeated per currency with {@code @ParameterizedClass} (JUnit 5.13 and later).
 * <p>
 * Every test method runs once for each value, with the value injected into the {@code @Parameter} field. Use it when
 * several tests share one dimension, here the supported currencies.
 */
@ParameterizedClass(name = "currency {0}")
@ValueSource(strings = {"EUR", "GBP", "USD"})
class PerCurrencyTest {

    @Parameter
    String currency;

    private static PaymentLifecycleApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private Map<String, Object> payment() {
        Map<String, Object> payment = validPayment("250.00");
        payment.put("currency", currency);
        return payment;
    }

    @Test
    void aPaymentCanBeInitiated() {
        newPayment(api).body(payment()).when().post("/payments")
            .then().statusCode(201).body("currency", equalTo(currency));
    }

    @Test
    void theAmountIsKeptExactly() {
        newPayment(api).body(payment()).when().post("/payments")
            .then().body("amount", equalTo("250.00"));
    }
}
