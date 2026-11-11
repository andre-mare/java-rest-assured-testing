# Simulating Provider Faults With WireMock: Errors, Delays, Resets and Broken JSON

Simulating Provider Faults With WireMock: Make a third-party dependency misbehave on demand, covering server errors, a slow provider with withFixedDelay, a reset connection with Fault, broken JSON, a missing value, rate limiting with Retry-After and an unknown currency, and check what your API's clients see.

A real FX provider works almost all of the time, which is exactly why it is useless for testing what happens when it doesn't. You can't ask it to answer with a `500`, to take three seconds, to reset the connection, or to send half a JSON document. Those are the cases that cause outages in production, and they are the ones a test suite most needs to cover. With WireMock, each of them is one line.

This post follows [Section 2 of Module 10](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-10) of the REST Assured course. It makes the FX provider misbehave in seven ways and checks how the quote service answers its own clients. The examples are in [`ProviderFaultsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_02/ProviderFaultsTest.java).

## What Should Happen When a Provider Fails?

The question that matters is not what the provider does, but what **our** API's clients see. A provider's `500` must not leak out as our own `500`, which would say that our service is broken. The quote service maps each provider failure to a status that tells its clients what happened:

| The provider... | Our API answers |
| --- | --- |
| answers `503` | retries once; `502` if it fails again |
| answers another `5xx` | `502 Bad Gateway` |
| answers `429` with `Retry-After` | `503 Service Unavailable` with the same `Retry-After` |
| answers `404` | `422`: the currency isn't supported |
| takes longer than a second | `504 Gateway Timeout` |
| resets the connection, sends broken JSON or no rate | `502 Bad Gateway` |

`502` and `504` are the gateway statuses: they say "something behind me failed", so a client can retry or alert the right team.

WireMock simulates each case with its response builder:

| WireMock | Simulates |
| --- | --- |
| `serverError()`, `serviceUnavailable()`, `notFound()` | Error statuses |
| `withFixedDelay(millis)` | A slow answer |
| `withFault(Fault.CONNECTION_RESET_BY_PEER)` | A broken connection |
| `withBody("{\"rates\": ")` | A malformed body |

## The Set-Up

Every test requests the same quote, EUR to GBP, and differs only in how the provider answers:

```java
private static ValidatableResponse requestAQuote() {
    return given().baseUri(service.baseUri()).port(service.port())
        .contentType(ContentType.JSON)
        .body(quoteRequest("EUR", "GBP", "100.00"))
        .when().post("/api/v1/quotes")
        .then();
}
```

WireMock is started with `WireMockExtension`, and `ratesRequest(..)` is the provider's request pattern, both from [Section 1](module-10-section-01-rest-assured-wiremock-stubbing-basics.md).

## Error Statuses

### Step 1: A Server Error Becomes Bad Gateway

```java
@Test
void aServerErrorBecomesBadGateway() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serverError()));

    requestAQuote()
        .statusCode(502)
        .contentType("application/problem+json")
        .body("detail", equalTo("The FX provider failed with status 500"));
}
```

The client gets a problem detail that names the cause:

```json
{"type":"about:blank","title":"Bad Gateway","status":502,"detail":"The FX provider failed with status 500","instance":"/api/v1/quotes"}
```

### Step 2: Rate Limiting Is Passed On

```java
@Test
void rateLimitingIsPassedOnWithRetryAfter() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
        WireMock.aResponse().withStatus(429).withHeader("Retry-After", "30")));

    requestAQuote().statusCode(503).header("Retry-After", "30");
}
```

The provider is rate limiting our service, not our client. The service answers `503` and passes the `Retry-After` on, so the client knows when it is worth trying again.

### Step 3: An Unknown Currency Is the Client's Problem

```java
@Test
void anUnknownCurrencyIsTheClientsProblem() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.notFound()));

    requestAQuote().statusCode(422).body("errors[0].field", equalTo("currency"));
}
```

A provider `404` means the provider doesn't know the pair. That is a problem with the client's input, so it is a `422` with a field error, not a gateway error.

> **The code above, ready to run**
>
> All seven tests in [`ProviderFaultsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_02/ProviderFaultsTest.java) pass with `mvn test -pl module-10 -Dtest=ProviderFaultsTest`. The slow-provider test takes about a second, because that is when our API gives up.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 10 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/README.md)

## Faults a Real Provider Won't Produce on Demand

### A Slow Provider Becomes Gateway Timeout

```java
@Test
void aSlowProviderBecomesGatewayTimeout() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
        WireMock.okJson("{\"rates\": {\"GBP\": 0.85}}").withFixedDelay(3000)));

    requestAQuote()
        .statusCode(504)
        .time(lessThan(2500L), TimeUnit.MILLISECONDS);            // our API gave up after about a second
}
```

The provider takes three seconds. The service's timeout is one second, so it answers `504` with `The FX provider didn't answer within PT1S`. The `time(..)` check proves the important part: our client wasn't kept waiting for the slow provider. Without a timeout in the service, every client would wait three seconds, or forever.

### A Reset Connection Becomes Bad Gateway

```java
@Test
void aResetConnectionBecomesBadGateway() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(
        WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    requestAQuote().statusCode(502).body("detail", containsString("unreachable"));
}
```

`Fault.CONNECTION_RESET_BY_PEER` closes the connection without an HTTP response. The service's HTTP client throws an `IOException`, and the service answers `The FX provider is unreachable: IOException`. WireMock has other faults too, such as `EMPTY_RESPONSE` and `RANDOM_DATA_THEN_CLOSE`.

### Broken JSON and a Missing Value

```java
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
```

A `200` doesn't mean the answer is usable. Half a JSON document, or a valid document without the rate we asked for, must not turn into a `NullPointerException` and a `500` in our service.

## Common Pitfalls

1. **Testing only the happy path of a dependency.** Outages come from the cases a real provider rarely produces. Simulate them.
2. **Letting a provider's status leak.** A provider `500` passed on as our `500` blames our service. Check what our clients see: `502`, `503` or `504`, with a useful detail.
3. **Checking the status of a timeout but not the time.** A service without a timeout also answers `504` eventually, after the provider's full delay. Check how long the client waited.
4. **Long delays in stubs.** A 30-second delay makes a 30-second test if the service has no timeout. Keep delays just above the service's timeout.
5. **Trusting a `200`.** Test malformed bodies and missing fields, not just error statuses.

## Wrapping Up

WireMock can make a dependency fail in ways a real one won't on demand: error statuses, `429` with `Retry-After`, delays with `withFixedDelay(..)`, broken connections with `withFault(..)`, malformed bodies and missing values. Each test checks what our API's clients see, which is the contract that matters: `502` for a provider failure, `504` for a timeout, `503` with `Retry-After` for rate limiting, and `422` when the client's input is the problem. Check the time on timeouts, and never let a provider's error become your own `500`.
