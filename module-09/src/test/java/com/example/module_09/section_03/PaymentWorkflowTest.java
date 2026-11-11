package com.example.module_09.section_03;

import static com.example.module_09.support.PaymentsClient.createPayment;
import static com.example.module_09.support.PaymentsClient.newPayment;
import static com.example.module_09.support.PaymentsClient.spec;
import static com.example.module_09.support.PaymentsClient.validPayment;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import com.example.lifecycle.PaymentLifecycleApi;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Module 9.3 – a payment from initiation to execution, as a readable story.
 * <p>
 * Two styles, with different trade-offs:
 * <ul>
 *   <li>{@link StepByStep}: one step per test method, in a fixed order, sharing state through one test instance. The
 *       report reads like the flow, but a failed step makes the next ones fail too, and no step can run alone.</li>
 *   <li>The other nested classes: each test creates the payment it needs and walks it to the state it tests. They
 *       are independent, run in any order, and can run in parallel. Prefer this style.</li>
 * </ul>
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class PaymentWorkflowTest {

    private static PaymentLifecycleApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)                   // one instance, so fields survive between steps
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class StepByStep {

        private String paymentId;

        @Test
        @Order(1)
        void initiate() {
            paymentId = newPayment(api).body(validPayment("120.00"))
                .when().post("/payments")
                .then().statusCode(201).body("status", equalTo("INITIATED"))
                .extract().path("paymentId");
        }

        @Test
        @Order(2)
        void authorise() {
            given(spec(api)).when().post("/payments/{id}/authorisation", paymentId)
                .then().statusCode(200).body("status", equalTo("AUTHORISED"));
        }

        @Test
        @Order(3)
        void execute() {
            given(spec(api)).when().post("/payments/{id}/execution", paymentId)
                .then().statusCode(202).body("status", equalTo("EXECUTING"));
        }

        @Test
        @Order(4)
        void settle() {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                given(spec(api)).when().get("/payments/{id}", paymentId)
                    .then().body("status", equalTo("EXECUTED")));

            given(spec(api)).when().get("/payments/{id}", paymentId)
                .then().body("statusHistory.status", contains("INITIATED", "AUTHORISED", "EXECUTING", "EXECUTED"));
        }
    }

    @Nested
    @Order(2)
    class IndependentTests {

        private String authorisedPayment(String amount) {
            String id = createPayment(api, amount);
            given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(200);
            return id;
        }

        @Test
        void aLargePaymentIsRejectedForInsufficientFunds() {
            String id = authorisedPayment("7500.00");
            given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(202);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                given(spec(api)).when().get("/payments/{id}", id)
                    .then()
                    .body("status", equalTo("REJECTED"))
                    .body("rejectionReason", equalTo("INSUFFICIENT_FUNDS")));
        }

        @Test
        void aPaymentCanBeAuthorisedOnlyOnce() {
            String id = authorisedPayment("10.00");

            given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(409);
        }

        @Test
        void aPaymentMustBeAuthorisedBeforeExecution() {
            String id = createPayment(api, "10.00");

            given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(409);
        }
    }
}
