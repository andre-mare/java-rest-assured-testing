package com.example.module_10.section_03;

import static com.example.module_10.support.FxProviderStubs.quoteRequest;
import static com.example.module_10.support.FxProviderStubs.ratesBody;
import static com.example.module_10.support.FxProviderStubs.ratesRequest;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.fx.QuoteService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.restassured.http.ContentType;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Module 10.3 – checking what our API sent to the provider.
 * <p>
 * REST Assured checks our API's answers. WireMock records every request it receives, so the test can also check the
 * outgoing side: the right URL, query, headers, and how many calls. That catches a leaked API key, a missing header or
 * an accidental call per item instead of one in total.
 */
class VerifyingCallsTest {

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

    private static void requestAQuote() {
        given().baseUri(service.baseUri()).port(service.port())
            .contentType(ContentType.JSON).body(quoteRequest("EUR", "GBP", "100.00"))
            .when().post("/api/v1/quotes");
    }

    @Test
    void oneQuoteMeansExactlyOneCallWithTheRightHeaders() {
        // A deliberately loose stub, so the verification below is what checks the details.
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
            .willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

        requestAQuote();

        fxProvider.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates"))
            .withQueryParam("base", WireMock.equalTo("EUR"))
            .withQueryParam("symbols", WireMock.equalTo("GBP"))
            .withHeader("Authorization", WireMock.equalTo("Bearer " + QuoteService.API_KEY))
            .withHeader("X-Client-Id", WireMock.equalTo(QuoteService.CLIENT_ID))
            .withHeader("Accept", WireMock.equalTo("application/json")));
    }

    @Test
    void aTemporaryOutageIsRetriedOnce() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serviceUnavailable()));

        requestAQuote();

        fxProvider.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));   // first try + one retry
    }

    @Test
    void otherErrorsAreNotRetried() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serverError()));

        requestAQuote();

        fxProvider.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
    }

    @Test
    void readTheRecordedRequestsYourself() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

        requestAQuote();

        List<LoggedRequest> calls = fxProvider.findAll(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
        assertEquals(1, calls.size());
        assertEquals("/v2/rates?base=EUR&symbols=GBP", calls.getFirst().getUrl());
    }
}
