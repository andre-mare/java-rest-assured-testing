package com.example.module_10.section_04;

import static com.example.module_10.support.FxProviderStubs.quoteRequest;
import static com.example.module_10.support.FxProviderStubs.ratesBody;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.fx.QuoteService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Module 10.4 – stubs that adapt: response templates and stateful scenarios.
 * <ul>
 *   <li>A <b>response template</b> builds the answer from the request, so one stub serves every currency.</li>
 *   <li>A <b>scenario</b> is a small state machine: each stub applies in one state and may move to the next, so the
 *       provider can fail first and recover later.</li>
 * </ul>
 */
class TemplatingAndScenariosTest {

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

    private static ValidatableResponse quote(String target) {
        return given().baseUri(service.baseUri()).port(service.port())
            .contentType(ContentType.JSON).body(quoteRequest("EUR", target, "100.00"))
            .when().post("/api/v1/quotes")
            .then();
    }

    @Test
    void oneTemplatedStubForEveryCurrency() {
        // {{request.query.symbols}} is replaced by the symbols query parameter of each request.
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"base\": \"{{request.query.base}}\", \"rates\": {\"{{request.query.symbols}}\": 2.0}}")
                .withTransformers("response-template")));

        quote("GBP").statusCode(201).body("targetCurrency", equalTo("GBP")).body("targetAmount", equalTo("200.00"));
        quote("CHF").statusCode(201).body("targetCurrency", equalTo("CHF"));
        quote("JPY").statusCode(201).body("targetCurrency", equalTo("JPY"));
    }

    @Test
    void theProviderFailsTwiceThenRecovers() {
        // States: Started -> (503) -> failed once -> (503) -> failed twice -> (200, for good)
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates")).inScenario("outage")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.serviceUnavailable())
            .willSetStateTo("failed once"));
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates")).inScenario("outage")
            .whenScenarioStateIs("failed once")
            .willReturn(WireMock.serviceUnavailable())
            .willSetStateTo("failed twice"));
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates")).inScenario("outage")
            .whenScenarioStateIs("failed twice")
            .willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

        quote("GBP").statusCode(502);                                // 503, retry, 503: our API gives up
        quote("GBP").statusCode(201);                                // the provider is back
    }

    @Test
    void oneFailureIsHiddenByTheRetry() {
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates")).inScenario("blip")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.serviceUnavailable())
            .willSetStateTo("recovered"));
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates")).inScenario("blip")
            .whenScenarioStateIs("recovered")
            .willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

        quote("GBP").statusCode(201).body("rate", equalTo("0.85"));
    }
}
