# Verifying Outgoing Calls With WireMock: What Your API Sent to Its Dependency

Verifying Outgoing Calls With WireMock: Check the requests your API sends to a third party, covering verify(..) with an exact count, query parameters and headers, one retry for a 503, no retry for a 500, and reading recorded requests with findAll(..).

REST Assured checks what our API answers. That is half the story. The other half is what our API sends to its dependency, and nothing in the response tells you. Did it send the API key? The client id the provider bills by? Did it call the provider once per quote, or once per item in a loop? Did it retry a temporary outage, or hammer the provider ten times? A response can be perfect while every one of those is wrong. WireMock records every request it receives, so a test can check the outgoing side too.

This post follows [Section 3 of Module 10](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-10) of the REST Assured course. It verifies the calls the quote service makes to the FX provider. The examples are in [`VerifyingCallsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_03/VerifyingCallsTest.java).

## What is Verification?

WireMock keeps a journal of every request it receives. Two methods read it:

1. **`verify(count, pattern)`** checks that exactly `count` requests matched the pattern, and fails otherwise.
2. **`findAll(pattern)`** returns the matching requests as `LoggedRequest` objects, to check in any way you like.

The patterns start with `getRequestedFor(..)`, `postRequestedFor(..)` and so on, and take the same `withQueryParam(..)` and `withHeader(..)` conditions as stubs.

## Strict Stubs or Verification?

[Section 1](module-10-section-01-rest-assured-wiremock-stubbing-basics.md) made the stub strict: it only answers a request with the right query and headers. That checks the request too, but indirectly: a wrong header makes the stub not match, WireMock answers `404`, and the service reports an unsupported currency. The test fails, but the message points the wrong way.

Verification is direct. A loose stub answers anything, and `verify(..)` then checks the details, with a message that shows exactly what differed.

## Verifying the Calls

### Step 1: One Quote Means Exactly One Call With the Right Headers

```java
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
```

`requestAQuote()` sends one quote request to our API with REST Assured. The verification then checks the one call that should have followed, with every detail the provider expects. If the client id were wrong, WireMock would show the closest request it received:

```text
com.github.tomakehurst.wiremock.client.VerificationException:
No requests exactly matched. Most similar request was:  expected:<
GET
/v2/rates?base=EUR&symbols=GBP

X-Client-Id: another-client
> but was:<
GET
/v2/rates?base=EUR&symbols=GBP

X-Client-Id: payments-service
>
```

The count matters as much as the details. `verify(1, ..)` fails if the service called the provider twice, which would double the cost of every quote.

### Step 2: A Temporary Outage Is Retried Once

```java
@Test
void aTemporaryOutageIsRetriedOnce() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serviceUnavailable()));

    requestAQuote();

    fxProvider.verify(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));   // first try + one retry
}
```

`503 Service Unavailable` is temporary by definition, so the service retries once. Our API's answer is a `502` either way, as [Section 2](module-10-section-02-rest-assured-wiremock-faults.md) showed, so only verification can tell whether the retry happened. A wrong count gives:

```text
Expected exactly 1 requests matching the following pattern but received 2:
{
  "urlPath" : "/v2/rates",
  "method" : "GET"
}
```

### Step 3: Other Errors Are Not Retried

```java
@Test
void otherErrorsAreNotRetried() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.serverError()));

    requestAQuote();

    fxProvider.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
}
```

A `500` is not a temporary condition, and retrying it adds load to a provider that is already in trouble. Testing what must **not** happen is as important as testing what must.

> **The code above, ready to run**
>
> All four tests in [`VerifyingCallsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/src/test/java/com/example/module_10/section_03/VerifyingCallsTest.java) pass with `mvn test -pl module-10 -Dtest=VerifyingCallsTest`. Change the expected count in the retry test to see WireMock's message.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 10 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-10/README.md)

## Reading the Recorded Requests

```java
@Test
void readTheRecordedRequestsYourself() {
    fxProvider.stubFor(ratesRequest("EUR", "GBP").willReturn(WireMock.okJson(ratesBody("EUR", "GBP", "0.85"))));

    requestAQuote();

    List<LoggedRequest> calls = fxProvider.findAll(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v2/rates")));
    assertEquals(1, calls.size());
    assertEquals("/v2/rates?base=EUR&symbols=GBP", calls.getFirst().getUrl());
}
```

`findAll(..)` returns the requests themselves: URL, headers, body, time. Use it when `verify(..)` can't express the check, for example to compare two requests with each other, or to parse a request body with `JsonPath.from(..)` from [Module 4](module-04-section-02-rest-assured-jsonpath-and-xmlpath.md).

## What Verification Catches

| Bug | Response looks fine? | Caught by |
| --- | --- | --- |
| The API key is missing or wrong | Only with a loose stub | `withHeader("Authorization", ..)` |
| One call per item instead of one in total | Yes | `verify(1, ..)` |
| No retry for a temporary outage | Yes, still `502` | `verify(2, ..)` |
| Retrying a permanent error | Yes, still `502` | `verify(1, ..)` |
| A call for invalid input | Yes, still `422` | `verify(0, ..)` |

## Common Pitfalls

1. **Checking only our API's response.** Retries, call counts and headers sent to a dependency don't show in it. Verify the outgoing calls.
2. **Verifying without a count.** `verify(pattern)` means "at least once", so a duplicate call passes. Give the exact count.
3. **Strict stubs as the only check.** A mismatch shows up as WireMock's `404`, which the service may report as something else entirely. Combine a loose stub with a precise `verify(..)` when the request is what you test.
4. **Requests from earlier tests.** `WireMockExtension` resets the journal after each test. With a hand-started server, call `resetAll()` between tests, or counts include old requests.
5. **Verifying more than the contract.** Checking every incidental header makes tests break on harmless changes. Verify what the provider requires and what costs money.

## Wrapping Up

REST Assured checks what our API answers, and WireMock's journal shows what it sent. `verify(count, pattern)` checks the exact number of calls with the right URL, query and headers, and gives a clear message when they differ. Use it to prove that a temporary outage is retried once, that a permanent error isn't, and that invalid input never reaches the provider. `findAll(..)` returns the recorded requests for checks that `verify(..)` can't express.
