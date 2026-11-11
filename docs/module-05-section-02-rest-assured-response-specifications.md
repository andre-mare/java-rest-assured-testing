# Response Specifications in REST Assured: Shared Expectations, Error Shapes and given(request, response)

Response Specifications in REST Assured: Put the expectations many responses share into a ResponseSpecification, use it with then().spec(..) or given(request, response), check a shared error shape, and see every failure of a specification reported at once.

Request specifications remove the repetition at the start of a test. The end of a test repeats too. Every successful response of the Open Banking API should be `200`, be JSON, echo the interaction id and carry a `Links.Self`. Every error should be an RFC 9457 problem. Write those checks in every test, and some tests will forget one. Put them in one place, and every test checks them all.

This post follows [Section 2 of Module 5](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-05) of the REST Assured course. It builds response specifications for success and for errors, and shows how a specification reports its failures. The examples are in [`ResponseSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_02/ResponseSpecificationTest.java).

## What is a Response Specification?

A `ResponseSpecification` is a set of expectations for a response, built with `ResponseSpecBuilder`:

1. **`expectStatusCode(..)`**, **`expectContentType(..)`** and **`expectHeader(..)`** check the status line and headers.
2. **`expectBody(path, matcher)`** checks a value in the body.
3. **`build()`** returns the finished, immutable specification.

You apply it in one of two ways:

- **`then().spec(spec)`**, after the request, like any other check.
- **`given(request, response)`**, before the request, together with a request specification.

## The API Under Test

The Open Banking API from [Section 1](module-05-section-01-rest-assured-request-specifications.md). Every successful response:

- Has status `200` and a JSON content type.
- Echoes the request's `x-fapi-interaction-id` header.
- Has a `Links.Self` in the body.

Every error is an RFC 9457 problem with the content type `application/problem+json`:

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"Account 99999 does not exist","instance":"/open-banking/v4.0/aisp/accounts/99999"}
```

## Building the Specifications

The class builds one request specification and two response specifications in `@BeforeAll`:

```java
success = new ResponseSpecBuilder()
        .expectStatusCode(200)
        .expectContentType(ContentType.JSON)
        .expectHeader("x-fapi-interaction-id", matchesPattern("[0-9a-f-]{36}"))
        // A response-aware header expectation (REST Assured 6.1+): the length must match the actual body
        .expectHeader("Content-Length", response -> equalTo(String.valueOf(response.asByteArray().length)))
        .expectBody("Links.Self", notNullValue())
        .build();

problem = new ResponseSpecBuilder()
        .expectContentType("application/problem+json")
        .expectBody("title", notNullValue())
        .expectBody("status", notNullValue())
        .build();
```

- **`success`** describes every successful response, including a response-aware header check, available since REST Assured 6.1. The lambda receives the response, as in [Module 3](module-03-section-05-rest-assured-response-aware-matchers.md), so the `Content-Length` header is compared with the real length of the body.
- **`problem`** describes every error, whatever its status.

The interaction id must be new for each request, so it is added per test by a helper:

```java
private static RequestSpecification request() {
    return given(openBanking).header("x-fapi-interaction-id", UUID.randomUUID().toString());
}
```

## Using the Specifications

### Step 1: then().spec(..)

```java
@Test
void thenSpec() {
    request()
    .when()
        .get("/accounts")
    .then()
        .spec(success)
        .body("Data.Account", hasSize(3));                       // plus what is special about this response
}
```

The specification checks what every success has in common, and the test adds what is special about this response. A reader sees at once what this test is about.

### Step 2: Both Specifications in given(..)

```java
@Test
void bothSpecificationsInGiven() {
    RequestSpecification withInteractionId = new RequestSpecBuilder()
        .addRequestSpecification(openBanking)
        .addHeader("x-fapi-interaction-id", UUID.randomUUID().toString())
        .build();

    given(withInteractionId, success)
        .get("/accounts/{id}/balances", "31820")                 // a RequestSender has no when(): send directly
    .then()
        .body("Data.Balance[0].Amount.Amount", equalTo("15000.00"));
}
```

`given(request, response)` returns a `RequestSender`, not a `RequestSpecification`. That has two consequences:

1. **The request is complete.** You can't add a header after `given(..)`, so the interaction id is built into a new request specification first.
2. **There is no `when()`.** You call `get(..)` directly.

The response specification is checked when the response arrives, and `then()` adds further checks. This form is compact when a test has nothing to add to the request.

### Step 3: Errors Share a Shape

```java
@Test
void errorsShareAShape() {
    request()
    .when()
        .get("/accounts/{id}", "99999")
    .then()
        .statusCode(404)
        .spec(problem);
}
```

The status is specific to this test, so it stays in the test. The shape of the body is shared by every error, so it lives in `problem`. Any error test in the suite can now prove that the API answers with a proper problem.

> **The code above, ready to run**
>
> All four tests in [`ResponseSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_02/ResponseSpecificationTest.java) pass with `mvn test -pl module-05 -Dtest=ResponseSpecificationTest`. Remove the `assertThrows(..)` from the last one to see the failure as JUnit would report it.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 5 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/README.md)

## A Specification Reports Every Failure

In [Module 1](module-01-section-06-reading-rest-assured-failures.md) you saw that a `then()` chain stops at its first failing call. A response specification is different: it is checked as a whole, so every expectation that fails is reported.

```java
@Test
void aSpecificationReportsAllItsFailuresAtOnce() {
    AssertionError error = assertThrows(AssertionError.class, () ->
        request()
        .when()
            .get("/accounts/{id}", "99999")                     // a 404 problem, not a success
        .then()
            .spec(success));

    String message = error.getMessage();
    assertTrue(message.startsWith("2 expectations failed."), message);   // the status and Links.Self
    assertTrue(message.contains("Expected status code <200> but was <404>"), message);
    assertTrue(message.contains("Links.Self"), message);
}
```

The test checks the failure message itself. Here it is in full:

```text
2 expectations failed.
Expected status code <200> but was <404>.

JSON path Links.Self doesn't match.
Expected: not null
  Actual: null
```

Only two of the five expectations fail. A problem is still JSON, because `ContentType.JSON` accepts any `+json` type, and the API echoes the interaction id on errors too. The message tells you exactly which parts of the contract this response broke.

Put `statusCode(200)` before `spec(success)` in the chain, and you lose that: the chain stops at the status code, and the message reports `1 expectation failed.`

## Common Pitfalls

1. **Adding to the request after `given(request, response)`.** It returns a `RequestSender`, which has no `header(..)` or `when()`. Build everything into the request specification first.
2. **Test-specific checks in a shared specification.** A `Data.Account` size of 3 is true for one endpoint only. Keep the specification to what every response has in common.
3. **A status code in an error specification.** `404`, `400` and `401` are all problems. Leave the status to the test, and the shape to the specification.
4. **Separate checks before the specification.** A failing `statusCode(..)` earlier in the chain hides the other failures the specification would have reported.
5. **Rebuilding specifications in every test.** Build them once, in `@BeforeAll` or in a shared class. They are immutable, so sharing them is safe.

## Wrapping Up

A response specification, built with `ResponseSpecBuilder`, holds the expectations that many responses share: status, content type, headers and body values, including response-aware checks since REST Assured 6.1. Apply it with `then().spec(..)`, or with `given(request, response)` when the request is complete. Keep one specification for success and one for the shape of errors. A specification is checked as a whole, so its failure message lists every expectation the response broke.
