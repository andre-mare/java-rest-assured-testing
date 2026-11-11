# Testing Asynchronous APIs With REST Assured: 202 Accepted, Retry-After and Awaitility

Testing Asynchronous APIs With REST Assured: Wait for asynchronous work without Thread.sleep, covering 202 Accepted, polling a Location with Awaitility, respecting Retry-After, and waiting for any final state before checking which one.

Executing a payment takes time. The API doesn't keep the connection open while it happens: it answers `202 Accepted` at once, and does the work in the background. The test, though, needs the outcome. The first thing everyone tries is `Thread.sleep(1000)` and a check afterwards. On a fast machine that wastes most of a second per test. On a slow CI agent, one second is sometimes not enough, and the test fails for no reason anyone can reproduce. Both problems have the same fix: poll until the work is done, with a timeout.

This post follows [Section 5 of Module 9](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-09) of the REST Assured course. It waits for asynchronous payment execution with Awaitility. The examples are in [`AsyncPollingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_05/AsyncPollingTest.java).

## What is 202 Accepted?

`202 Accepted` means the request was accepted for processing, which has started but not finished. A good asynchronous API tells the client two more things:

1. **`Location`**: where to look for the result.
2. **`Retry-After`**: how many seconds to wait before asking.

[Awaitility](https://github.com/awaitility/awaitility) is a library for waiting in tests. It polls a condition at an interval until it holds, and fails with a clear message when a timeout passes. A test then takes as long as the work takes, and no longer.

## The Endpoint Under Test

`POST /api/v1/payments/{id}/execution` on an authorised payment answers:

```text
HTTP/1.1 202 Accepted
Location: /api/v1/payments/pay-6
Retry-After: 1

{"paymentId":"pay-6","status":"EXECUTING",...}
```

About half a second later, the payment is `EXECUTED`, or `REJECTED` with `rejectionReason: INSUFFICIENT_FUNDS` when the amount is above 5000.00.

A helper creates, authorises and executes a payment, and returns the `202` response:

```java
private static ExtractableResponse<Response> startExecution(String amount) {
    String id = createPayment(api, amount);
    given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(200);
    return given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(202).extract();
}
```

## Waiting With Awaitility

### Step 1: Poll the Location Until the Work Is Done

```java
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
```

- **`await()`** is a static import from `org.awaitility.Awaitility`.
- **`untilAsserted(..)`** runs the lambda again and again. An `AssertionError` means "not yet", so a REST Assured check fits directly.
- **`atMost(..)`** is the timeout, and **`pollInterval(..)`** the time between attempts.
- **The `Location`** is an absolute path, so the base path is cleared with `basePath("")`, as in [Section 4](module-09-section-04-rest-assured-http-semantics.md).

The test finishes as soon as the status is `EXECUTED`, about half a second here. The generous five seconds only matter when something is wrong. Then Awaitility fails with a message that includes the last assertion error:

```text
org.awaitility.core.ConditionTimeoutException: Assertion condition defined as a Lambda expression in ...
1 expectation failed.
JSON path status doesn't match.
Expected: EXECUTED
  Actual: REJECTED
 within 2 seconds.
```

### Step 2: Respect Retry-After

```java
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
```

`pollDelay(..)` waits before the first attempt. The server asked for one second, so the test asks after one second. Against a real API, ignoring `Retry-After` can count against a rate limit.

This test uses `until(supplier, matcher)` instead of `untilAsserted(..)`: the lambda returns a value, the status, and Awaitility checks it with a Hamcrest matcher.

> **The code above, ready to run**
>
> All three tests in [`AsyncPollingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_05/AsyncPollingTest.java) pass with `mvn test -pl module-09 -Dtest=AsyncPollingTest`. The class takes about two seconds, most of it the `Retry-After` delay.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 9 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/README.md)

## Waiting for Any Final State

```java
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
```

This is the most robust pattern. The test waits for the work to **finish**, whatever the outcome, and then checks **which** outcome it was.

Compare it with waiting for `EXECUTED` on this 9000.00 payment. The payment is rejected after half a second, but the test keeps polling for a state that will never come, and fails only after the full timeout. Waiting for any final state gives a fast, precise failure instead: `expected: <EXECUTED> but was: <REJECTED>`.

`until(..)` returns the value that met the condition, so the test doesn't need another request to read it. The predicate is a plain lambda here, since "anything but `EXECUTING`" is easier to say in Java than as a matcher.

## Common Pitfalls

1. **`Thread.sleep(..)`.** Too short and the test is flaky, too long and the suite is slow. Poll with Awaitility.
2. **Waiting for one specific outcome.** If the work ends in another final state, the test waits for the whole timeout. Wait for any final state, then check which.
3. **Logging on every failed poll.** A specification with `enableLoggingOfRequestAndResponseIfValidationFails(..)` logs the full request and response for every attempt that fails inside `untilAsserted(..)`, so one wait can print the same exchange ten times. Use `until(supplier, matcher)`, which doesn't validate with REST Assured, or a specification without that setting for polling.
4. **Ignoring `Retry-After`.** Polling every 100 ms against a real service is rude and may be rate-limited. Start with `pollDelay(..)`.
5. **A timeout that is too tight.** Asynchronous work varies more than synchronous requests. Set a generous `atMost(..)`: a passing test never waits for it anyway.

## Wrapping Up

`202 Accepted` means the work has started, not finished, and the response's `Location` and `Retry-After` say where and when to look. Awaitility polls until a condition holds, so a test takes as long as the work does, and fails with a clear message after a timeout. Use `untilAsserted(..)` for REST Assured checks and `until(supplier, matcher)` for values, respect `Retry-After` with `pollDelay(..)`, and wait for any final state before checking which one it is.
