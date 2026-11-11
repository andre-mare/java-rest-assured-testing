package com.example.module_09.section_05;

import static com.example.module_09.support.PaymentsClient.createPayment;
import static com.example.module_09.support.PaymentsClient.spec;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.lifecycle.PaymentLifecycleApi;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 9.5 – waiting for asynchronous work without {@code Thread.sleep}.
 * <p>
 * {@code POST /execution} answers 202 Accepted: the work has started, not finished. The response says where to look
 * ({@code Location}) and how long to wait before asking ({@code Retry-After}). A fixed {@code Thread.sleep} is either
 * too short (a flaky test) or too long (a slow suite). Awaitility polls until a condition holds and fails with a clear
 * message after a timeout.
 */
class AsyncPollingTest {

    private static PaymentLifecycleApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static ExtractableResponse<Response> startExecution(String amount) {
        String id = createPayment(api, amount);
        given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(200);
        return given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(202).extract();
    }

    @Test
    void pollTheLocationUntilTheWorkIsDone() {
        ExtractableResponse<Response> accepted = startExecution("40.00");
        String location = accepted.header("Location");

        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() ->
                given(spec(api)).basePath("").when().get(location)
                    .then().body("status", equalTo("EXECUTED")));
    }

    @Test
    void respectRetryAfter() {
        ExtractableResponse<Response> accepted = startExecution("40.00");
        Duration retryAfter = Duration.ofSeconds(Long.parseLong(accepted.header("Retry-After")));
        String location = accepted.header("Location");

        await()
            .pollDelay(retryAfter)                                     // don't ask before the server said to
            .atMost(retryAfter.plusSeconds(5))
            .until(() -> given(spec(api)).basePath("").when().get(location).path("status"),
                   oneOf("EXECUTED", "REJECTED"));
    }

    @Test
    void waitForAFinalStateThenCheckWhichOne() {
        ExtractableResponse<Response> accepted = startExecution("9000.00");
        String location = accepted.header("Location");

        String finalStatus = await()
            .atMost(Duration.ofSeconds(5))
            .until(() -> given(spec(api)).basePath("").when().get(location).<String>path("status"),
                   status -> !status.equals("EXECUTING"));

        assertEquals("REJECTED", finalStatus);
    }
}
