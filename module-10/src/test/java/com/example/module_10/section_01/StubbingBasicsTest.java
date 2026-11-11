package com.example.module_10.section_01;

import static com.example.module_10.support.FxProviderStubs.quoteRequest;
import static com.example.module_10.support.FxProviderStubs.ratesBody;
import static com.example.module_10.support.FxProviderStubs.ratesRequest;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.fx.QuoteService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Module 10.1 – testing our API through REST Assured while WireMock plays the third party it calls.
 * <pre>
 *   test --REST Assured--> QuoteService --HttpClient--> WireMock (the FX provider)
 * </pre>
 * The {@link WireMockExtension} starts WireMock on a free port before the class, and removes all stubs after each test,
 * so every test sets up exactly the provider behaviour it needs. A request that no stub matches gets a 404 from
 * WireMock.
 */
class StubbingBasicsTest {

    @RegisterExtension
    static WireMockExtension fxProvider = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static QuoteService service;

    @BeforeAll
    static void startService() {
        service = QuoteService.start(fxProvider.baseUrl());          // the service calls WireMock, not the internet
    }

    @AfterAll
    static void stopService() {
        service.close();
    }

    private static RequestSpecification quotes() {
        return given().baseUri(service.baseUri()).port(service.port()).basePath("/api/v1").contentType(ContentType.JSON);
    }

    @Test
    void aQuoteUsesTheProvidersRate() {
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.8523"))));

        quotes()
            .body(quoteRequest("EUR", "GBP", "100.00"))
        .when()
            .post("/quotes")
        .then()
            .statusCode(201)
            .body("rate", equalTo("0.8523"))
            .body("targetAmount", equalTo("85.23"));
    }

    @Test
    void roundingIsHalfEven() {
        // With a rate of 0.5, 10.05 gives 5.025 and 10.15 gives 5.075: exact ties. Half-even ("banker's rounding")
        // rounds a tie to the even neighbour, so 5.02 and 5.08. A stub makes such edge cases easy to reach.
        fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.5"))));

        quotes().body(quoteRequest("EUR", "GBP", "10.05")).when().post("/quotes").then().body("targetAmount", equalTo("5.02"));
        quotes().body(quoteRequest("EUR", "GBP", "10.15")).when().post("/quotes").then().body("targetAmount", equalTo("5.08"));
    }

    @Test
    void aRequestWithoutTheRightHeadersMatchesNoStub() {
        // This stub demands a different API key, so the service's request doesn't match it, WireMock answers 404,
        // and the service reports an unsupported currency. The stub is part of the test's specification.
        fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
            .withHeader("Authorization", WireMock.equalTo("Bearer another-key"))
            .willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

        quotes().body(quoteRequest("EUR", "GBP", "100.00")).when().post("/quotes").then().statusCode(422);
    }

    @Test
    void restAssuredCanCallTheStubDirectly() {
        // Handy while writing a stub: see exactly what it answers.
        fxProvider.stubFor(ratesRequest("EUR", "USD").willReturn(WireMock.okJson(ratesBody("EUR", "USD", "1.09"))));

        given()
            .baseUri(fxProvider.baseUrl())
            .header("Authorization", "Bearer " + QuoteService.API_KEY)
            .header("X-Client-Id", QuoteService.CLIENT_ID)
            .queryParam("base", "EUR")
            .queryParam("symbols", "USD")
        .when()
            .get("/v2/rates")
        .then()
            .statusCode(200)
            .body("rates.USD", equalTo(1.09f));
    }

    @Test
    void invalidInputNeverReachesTheProvider() {
        quotes().body(quoteRequest("EUR", "EUR", "100.00")).when().post("/quotes").then().statusCode(422);

        fxProvider.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
    }
}
