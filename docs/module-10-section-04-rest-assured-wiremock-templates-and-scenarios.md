# WireMock Response Templates and Scenarios: Stubs That Adapt

WireMock Response Templates and Scenarios: Build stubs that adapt, covering one templated stub for every currency with response-template and {{request.query..}}, and stateful scenarios in which the provider fails twice and recovers, or fails once and is hidden by a retry.

The stubs so far answer the same way every time. That is fine for one currency pair, but a test of three currencies needs three stubs, almost identical. And some behaviours can't be expressed with a fixed answer at all: a provider that is down for two requests and then comes back. A retry can't be tested properly without that. WireMock has two features for stubs that adapt: response templates, which build the answer from the request, and scenarios, which change the answer over time.

This post follows [Section 4 of Module 10](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-10) of the REST Assured course. It uses a template for every currency, and scenarios for an outage and a short blip. The examples are in [`TemplatingAndScenariosTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_04/TemplatingAndScenariosTest.java).

## What are Templates and Scenarios?

1. **A response template** is a response body with placeholders, filled from the request. `{{request.query.symbols}}` is replaced by the `symbols` query parameter. The template is processed only when the stub asks for it with `withTransformers("response-template")`.
2. **A scenario** is a small state machine shared by several stubs:
   - `inScenario(name)` puts a stub in a scenario,
   - `whenScenarioStateIs(state)` makes it match only in that state,
   - `willSetStateTo(state)` moves the scenario on when it matches.

Every scenario starts in `Scenario.STARTED`. `WireMockExtension` resets scenarios after each test, along with the stubs.

## The Set-Up

The tests request a quote from EUR to a target currency, through the quote service from [Section 1](module-10-section-01-rest-assured-wiremock-stubbing-basics.md):

```java
private static ValidatableResponse quote(String target) {
    return given().baseUri(service.baseUri()).port(service.port())
        .contentType(ContentType.JSON).body(quoteRequest("EUR", target, "100.00"))
        .when().post("/api/v1/quotes")
        .then();
}
```

Recall the service's retry rule from [Section 3](module-10-section-03-rest-assured-wiremock-verifying-calls.md): a provider `503` is retried once, and if the retry fails too, the client gets `502`.

## One Templated Stub for Every Currency

```java
@Test
void oneTemplatedStubForEveryCurrency() {
    fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
        .willReturn(WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"base\": \"{{request.query.base}}\", \"rates\": {\"{{request.query.symbols}}\": 2.0}}")
            .withTransformers("response-template")));

    quote("GBP").statusCode(201).body("targetCurrency", equalTo("GBP")).body("targetAmount", equalTo("200.00"));
    quote("CHF").statusCode(201).body("targetCurrency", equalTo("CHF"));
    quote("JPY").statusCode(201).body("targetCurrency", equalTo("JPY"));
}
```

For a request `GET /v2/rates?base=EUR&symbols=CHF`, the body becomes `{"base": "EUR", "rates": {"CHF": 2.0}}`. One stub answers for every currency, with the rate under the key the service asked for.

Leave out `withTransformers("response-template")`, and the placeholders are sent as they are. The provider's answer then has a rate for a currency literally called `{{request.query.symbols}}`, and the service answers `502`, `The FX provider sent no rate for GBP`.

Templates are useful when the answer must echo part of the request: an id, a currency, a date. Keep the logic in them simple: a template that calculates is a second implementation of the provider.

## Scenarios

### Step 1: The Provider Fails Twice, Then Recovers

```java
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
```

| Provider call | Scenario state | Answer | Next state |
| --- | --- | --- | --- |
| 1st (quote 1) | `Started` | `503` | `failed once` |
| 2nd (quote 1, retry) | `failed once` | `503` | `failed twice` |
| 3rd (quote 2) | `failed twice` | `200` | stays |

The first quote uses up both failures, its first try and its retry, and gets `502`. The second quote finds the provider recovered. The test proves two things at once: the service gives up after one retry, and it doesn't stay broken once the provider is back.

### Step 2: One Failure Is Hidden by the Retry

```java
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
```

This is what the retry is for. The provider fails once, the service retries, the second call succeeds, and the client never knows. A stub that always answered `503` could prove that a retry happens. Only a scenario can prove that the retry actually saves the request.

> **The code above, ready to run**
>
> All three tests in [`TemplatingAndScenariosTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_04/TemplatingAndScenariosTest.java) pass with `mvn test -pl module-10 -Dtest=TemplatingAndScenariosTest`. Remove `withTransformers("response-template")` from the first test to see what the service makes of an untemplated body.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 10 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/README.md)

## When to Use Which

| Need | Use |
| --- | --- |
| The same answer every time | A plain stub |
| An answer that echoes the request | A response template |
| An answer that changes over time | A scenario |
| Different answers for different requests | Several plain stubs with different request patterns |

## Common Pitfalls

1. **Forgetting `withTransformers("response-template")`.** The placeholders are sent literally, and the failure points at the provider's data, not at the stub.
2. **Templates that compute.** A template that calculates rates reimplements the provider, bugs included. Echo values; don't derive them.
3. **Miscounting scenario steps.** A retry is a provider call too. Count every call the service makes, not just every request the test sends.
4. **Scenarios across tests.** A scenario's state survives between requests. `WireMockExtension` resets it after each test; a hand-started server needs `resetAll()` or `resetScenarios()`.
5. **Testing retries with a stub that always fails.** It proves the retry happens, not that it helps. Add a scenario that recovers.

## Wrapping Up

Response templates build the answer from the request: with `withTransformers("response-template")`, placeholders such as `{{request.query.symbols}}` let one stub serve every currency. Scenarios make a stub a small state machine with `inScenario(..)`, `whenScenarioStateIs(..)` and `willSetStateTo(..)`, so a provider can fail and then recover. Together they test what fixed stubs can't: that a retry gives up when it should, and that it saves a request when the provider only blips.
