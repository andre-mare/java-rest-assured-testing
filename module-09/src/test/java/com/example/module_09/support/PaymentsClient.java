package com.example.module_09.support;

import static io.restassured.RestAssured.given;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.RestAssuredConfig.config;

import com.example.lifecycle.PaymentLifecycleApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Shared set-up for Module 9: the request specification and valid payment bodies. */
public final class PaymentsClient {

    private PaymentsClient() {
    }

    public static RequestSpecification spec(PaymentLifecycleApi api) {
        return new RequestSpecBuilder()
            .setBaseUri(api.baseUri())
            .setPort(api.port())
            .setBasePath(PaymentLifecycleApi.BASE_PATH)
            .addHeader("Authorization", "Bearer " + PaymentLifecycleApi.TOKEN)
            .setContentType(ContentType.JSON)
            // Data-driven suites send many requests: print them only for a failing test.
            .setConfig(config().logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL)))
            .build();
    }

    /** A request with a fresh idempotency key, ready to POST a payment. */
    public static RequestSpecification newPayment(PaymentLifecycleApi api) {
        return given(spec(api)).header("Idempotency-Key", UUID.randomUUID().toString());
    }

    /** A valid payment; change entries to make it invalid. */
    public static Map<String, Object> validPayment(String amount) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("debtorIban", "NL91ABNA0417164300");
        payment.put("creditorIban", "NL69INGB0123456789");
        payment.put("creditorName", "Jan Jansen");
        payment.put("amount", amount);
        payment.put("currency", "EUR");
        return payment;
    }

    /** Creates a payment and returns its id. */
    public static String createPayment(PaymentLifecycleApi api, String amount) {
        return newPayment(api).body(validPayment(amount))
            .when().post("/payments")
            .then().statusCode(201)
            .extract().path("paymentId");
    }
}
