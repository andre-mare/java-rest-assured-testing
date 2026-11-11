package com.example.module_10.support;

import com.example.fx.QuoteService;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * The FX provider's contract, as WireMock request patterns. Tests start from these and choose the response, so the
 * details of the provider's API live in one place.
 * <p>
 * WireMock's {@code get(..)} and {@code equalTo(..)} clash with REST Assured's and Hamcrest's static imports, so this
 * course calls them through the {@link WireMock} class.
 */
public final class FxProviderStubs {

    private FxProviderStubs() {
    }

    /** {@code GET /v2/rates?base=..&symbols=..} with the headers the quote service must send. */
    public static MappingBuilder ratesRequest(String base, String symbol) {
        return WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
            .withQueryParam("base", WireMock.equalTo(base))
            .withQueryParam("symbols", WireMock.equalTo(symbol))
            .withHeader("Authorization", WireMock.equalTo("Bearer " + QuoteService.API_KEY))
            .withHeader("X-Client-Id", WireMock.equalTo(QuoteService.CLIENT_ID));
    }

    /** The provider's normal answer for one rate. */
    public static String ratesBody(String base, String symbol, String rate) {
        return """
            {"base": "%s", "timestamp": 1767225600, "rates": {"%s": %s}}
            """.formatted(base, symbol, rate);
    }

    /** A quote request for our own API. */
    public static String quoteRequest(String source, String target, String amount) {
        return """
            {"sourceCurrency": "%s", "targetCurrency": "%s", "sourceAmount": "%s"}
            """.formatted(source, target, amount);
    }
}
