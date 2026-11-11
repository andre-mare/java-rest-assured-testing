package com.example.module_10.section_02;

import static com.example.module_10.support.FxProviderStubs.quoteRequest;
import static com.example.module_10.support.FxProviderStubs.ratesRequest;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import com.example.fx.QuoteService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Module 10.2 – the provider misbehaves: our API must still answer sensibly.
 * <p>
 * These are the tests that are hard or impossible against a real third party: you can't ask a real provider to be
 * slow, to reset the connection or to send broken JSON on demand. WireMock can. Each test also says what our API's
 * clients see, which is the contract that matters: a 5xx from the provider must not leak as our own 500.
 */
class ProviderFaultsTest {

    @RegisterExtension
    static WireMockExtension fxProvider = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static QuoteService service;

    @BeforeAll
    static void startService() {
        service = QuoteService.start(fxProvider.baseUrl());
    }

    @AfterAll
    static void stopService() {
        service.close();
    }

    private static ValidatableResponse requestAQuote() {
        return given().baseUri(service.baseUri()).port(service.port())
            .contentType(ContentType.JSON)
            .body(quoteRequest("EUR", "GBP", "100.00"))
            .when().post("/api/v1/quotes")
            .then();
    }

    @Test
    void aServerErrorBecomesBadGateway() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serverError()));

        requestAQuote()
            .statusCode(502)
            .contentType("application/problem+json")
            .body("detail", equalTo("The FX provider failed with status 500"));
    }

    @Test
    void aSlowProviderBecomesGatewayTimeout() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
            WireMock.okJson("{\"rates\": {\"GBP\": 0.85}}").withFixedDelay(3000)));

        requestAQuote()
            .statusCode(504)
            .time(lessThan(2500L), TimeUnit.MILLISECONDS);            // our API gave up after about a second
    }

    @Test
    void aResetConnectionBecomesBadGateway() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
            WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        requestAQuote().statusCode(502).body("detail", containsString("unreachable"));
    }

    @Test
    void brokenJsonBecomesBadGateway() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"rates\": ")));

        requestAQuote().statusCode(502).body("detail", equalTo("The FX provider sent an invalid response"));
    }

    @Test
    void aMissingRateBecomesBadGateway() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson("{\"rates\": {\"USD\": 1.09}}")));

        requestAQuote().statusCode(502).body("detail", equalTo("The FX provider sent no rate for GBP"));
    }

    @Test
    void rateLimitingIsPassedOnWithRetryAfter() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
            WireMock.aResponse().withStatus(429).withHeader("Retry-After", "30")));

        requestAQuote().statusCode(503).header("Retry-After", "30");
    }

    @Test
    void anUnknownCurrencyIsTheClientsProblem() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.notFound()));

        requestAQuote().statusCode(422).body("errors[0].field", equalTo("currency"));
    }
}
