# Stubbing a Dependency With WireMock: Testing Your API With REST Assured

Stubbing a Dependency With WireMock: Test an API whose third-party dependency you don't control, with REST Assured calling your API and WireMock playing the provider it calls, covering WireMockExtension, request patterns, a stubbed rate, half-even rounding, unmatched requests and input that never reaches the provider.

Most APIs don't work alone. A quote service asks an FX provider for today's exchange rate; a payment service asks a fraud-check service; an onboarding API asks a credit bureau. In a test, the real provider is a problem: it costs money per call, its rates change every minute, it is down for maintenance on Sunday, and you can't make it return the exact rate that tests a rounding edge case. You need a stand-in that answers whatever the test needs. That is what WireMock is.

This post is the first in the series for [Module 10 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-10), which covers testing an API whose own dependency you don't control. It stubs an FX provider with WireMock and tests a quote service through it with REST Assured. The examples are in [`StubbingBasicsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_01/StubbingBasicsTest.java) and [`FxProviderStubs`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/support/FxProviderStubs.java).

## What is WireMock?

[WireMock](https://wiremock.org/) is an HTTP server for tests. Instead of running real logic, it answers with what you told it to, a **stub**:

1. **A request pattern** says which requests the stub matches: method, path, query parameters, headers, body.
2. **A response** says what to answer: status, headers, body, and, as [Section 2](module-10-section-02-rest-assured-wiremock-faults.md) shows, delays and faults.

In this module, each tool has its own side:

```text
test --REST Assured--> QuoteService --HttpClient--> WireMock (the FX provider)
```

REST Assured tests **our** API. WireMock plays the **provider** our API calls. The service is configured with WireMock's URL instead of the real provider's.

## The API Under Test

`QuoteService` answers `POST /api/v1/quotes`:

```json
{"sourceCurrency": "EUR", "targetCurrency": "GBP", "sourceAmount": "100.00"}
```

To answer, it asks the FX provider for the rate:

```text
GET /v2/rates?base=EUR&symbols=GBP
Authorization: Bearer fx-api-key
X-Client-Id: payments-service
```

The provider answers `{"base": "EUR", "timestamp": 1767225600, "rates": {"GBP": 0.8523}}`, and the service returns a quote, with the target amount rounded half-even to two decimals:

```json
{"quoteId":"q-eca2dfa5","sourceCurrency":"EUR","targetCurrency":"GBP","sourceAmount":"100.00",
 "rate":"0.8523","targetAmount":"85.23","expiresAt":"2026-11-11T07:25:35.087204Z"}
```

If the provider answers `404`, the service treats the currency pair as unsupported and answers `422`.

## The Provider's Contract in One Place

`FxProviderStubs` describes the provider's API once, so the tests don't repeat it:

```java
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
```

The request pattern is strict: it matches only a request with the right path, query and headers. A stub built from it answers only when our service calls the provider correctly.

WireMock's `get(..)` and `equalTo(..)` have the same names as REST Assured's `get(..)` and Hamcrest's `equalTo(..)`. Statically importing both doesn't compile, so the course calls WireMock's through the class: `WireMock.get(..)`, `WireMock.equalTo(..)`.

## Starting WireMock

```java
@RegisterExtension
static WireMockExtension fxProvider = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

private static QuoteService service;

@BeforeAll
static void startService() {
    service = QuoteService.start(fxProvider.baseUrl());          // the service calls WireMock, not the internet
}
```

- **`WireMockExtension`** starts WireMock before the class and stops it after.
- **`dynamicPort()`** picks a free port, like the APIs in this course.
- **The extension removes all stubs after each test**, so every test sets up exactly the provider behaviour it needs, and no stub leaks into the next test.
- **`fxProvider.baseUrl()`** is WireMock's address, which the service uses as the provider's.

## Stubbing the Provider

### Step 1: A Quote Uses the Provider's Rate

```java
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
```

`stubFor(..)` registers the stub, and `okJson(..)` answers `200` with a JSON body. The test then uses REST Assured as usual, against our service. The rate is fixed, so the target amount is predictable.

### Step 2: Rounding Is Half-Even

```java
@Test
void roundingIsHalfEven() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.5"))));

    quotes().body(quoteRequest("EUR", "GBP", "10.05")).when().post("/quotes").then().body("targetAmount", equalTo("5.02"));
    quotes().body(quoteRequest("EUR", "GBP", "10.15")).when().post("/quotes").then().body("targetAmount", equalTo("5.08"));
}
```

With a rate of `0.5`, `10.05` gives `5.025` and `10.15` gives `5.075`: exact ties. Half-even rounding, also called banker's rounding, rounds a tie to the even neighbour, so `5.02` and `5.08`. Half-up rounding would give `5.03` and `5.08`. A real provider would never hand you a rate that hits these ties on demand. A stub does.

> **The code above, ready to run**
>
> All five tests in [`StubbingBasicsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_01/StubbingBasicsTest.java) pass with `mvn test -pl module-10 -Dtest=StubbingBasicsTest`. WireMock runs in the same JVM, so there is nothing to install or start.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 10 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/README.md)

## When No Stub Matches

```java
@Test
void aRequestWithoutTheRightHeadersMatchesNoStub() {
    fxProvider.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/rates"))
        .withHeader("Authorization", WireMock.equalTo("Bearer another-key"))
        .willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

    quotes().body(quoteRequest("EUR", "GBP", "100.00")).when().post("/quotes").then().statusCode(422);
}
```

The stub demands a different API key, so the service's request doesn't match it. WireMock answers `404` to any request no stub matches, and the service turns that into `422`, `the FX provider doesn't support EUR/GBP`.

This shows the stub is part of the test's specification: a strict stub fails the test when the service sends the wrong headers. It also shows a trap: a missing or wrong stub looks to the service like a real `404`. When a test fails in a surprising way, check whether the stub matched.

## Calling the Stub Directly

```java
@Test
void restAssuredCanCallTheStubDirectly() {
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
```

While writing a stub, it helps to see exactly what it answers. REST Assured can call WireMock like any other server. A request that matches no stub gets WireMock's report of the closest stub and what differed:

```text
                                               Request was not matched
                                               =======================
| Closest stub                                             | Request                                                  |
GET                                                        | GET
[path] /v2/rates                                           | /v2/other                                           <<<<< URL does not match
```

## Input That Never Reaches the Provider

```java
@Test
void invalidInputNeverReachesTheProvider() {
    quotes().body(quoteRequest("EUR", "EUR", "100.00")).when().post("/quotes").then().statusCode(422);
    fxProvider.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
}
```

A quote from EUR to EUR is invalid, and the service rejects it with `422` before calling anyone. `verify(0, ..)` proves that no call was made: no wasted request, and no cost. [Section 3](module-10-section-03-rest-assured-wiremock-verifying-calls.md) covers verification in depth.

## Common Pitfalls

1. **Static import clashes.** WireMock's `get(..)` and `equalTo(..)` collide with REST Assured's and Hamcrest's. Call WireMock's through the `WireMock` class.
2. **A missing stub that looks like a real answer.** WireMock's `404` for an unmatched request reaches the service like a real provider's `404`. If a test fails unexpectedly, check the stub first, or call it directly.
3. **Loose stubs everywhere.** A stub that matches any request can't catch a missing header. Match what the provider requires.
4. **Stubs that leak between tests.** `WireMockExtension` resets them after each test. A hand-started `WireMockServer` doesn't, so reset it yourself.
5. **The real provider's URL in tests.** Make the dependency's base URL configurable, and give the service WireMock's.

## Wrapping Up

WireMock plays a dependency your API calls, while REST Assured tests your API. `WireMockExtension` starts it on a free port and clears the stubs after each test. A stub is a request pattern plus a response: keep the provider's patterns in one place, make them strict, and choose the response per test. Stubs make edge cases such as exact rounding ties easy to reach. Remember that WireMock answers `404` when no stub matches, which your service can't tell from a real one, and use `verify(..)` to prove calls were, or weren't, made.
