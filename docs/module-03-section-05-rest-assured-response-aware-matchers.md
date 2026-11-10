# Response-Aware Matchers in REST Assured: Using the Response to Check Itself

Response-Aware Matchers in REST Assured: Check values the server chooses by building the expectation from the response, covering a Location header that must end with the new id, endsWithPath(..), one body value checked against another, and a matcher on the whole body.

When you create a transaction, the server picks its id. The response has the id in the body and again at the end of the `Location` header, and the two must agree. But the test doesn't know the id in advance, so what does it expect? You could extract the id first, then check the header in a second step. REST Assured has a shorter way: a matcher that is built from the response it is about to check.

This post follows [Section 5 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. It shows response-aware matchers for headers and body values, and a matcher on the whole body. The examples are in [`ResponseAwareMatcherTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_05/ResponseAwareMatcherTest.java).

## What is a Response-Aware Matcher?

A `ResponseAwareMatcher` is a function that takes the response and returns a matcher. REST Assured calls it with the actual response, then checks the value with the matcher it returned. It is a functional interface, so a lambda is enough:

```java
.header("Location", response -> endsWith("/transactions/" + response.path("id")))
```

1. **`response`** is the whole response, so `response.path(..)`, `response.header(..)` and the other methods can read any part of it.
2. **The lambda returns a matcher** such as `endsWith(..)`, built from what it read.
3. **REST Assured applies that matcher** to the value being checked, here the `Location` header.

`header(..)` and `body(..)` both accept a response-aware matcher where they accept a normal one.

## The Endpoint Under Test

`POST /api/v1/accounts/acc-1001/transactions` adds a transaction. It takes an amount and a counterparty name, and answers `201 Created`:

```http
HTTP/1.1 201 Created
Location: /api/v1/accounts/acc-1001/transactions/tx-7
Content-type: application/json; charset=utf-8

{"id":"tx-7","bookingDate":"2026-11-10","type":"DEBIT","amount":-3.50,
 "counterparty":{"name":"Bakkerij Bart","iban":"NL00TEST0000000000"},"category":"OTHER",...}
```

- **The id** is the next free number, so it depends on what the class has already created.
- **The type** is chosen by the server from the sign of the amount: `DEBIT` for a negative amount, `CREDIT` otherwise.

Every test in the class sends the same request, from a helper:

```java
private static RequestSpecification newTransaction() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
        .contentType(ContentType.JSON)
        .body(Map.of("amount", -3.5, "counterpartyName", "Bakkerij Bart"));
}
```

These tests change the data, so it matters that every test class starts its own API in `@BeforeAll`. The transactions added here never show up in the other classes, which check exact totals.

## Checking Values the Server Chooses

### Step 1: The Location Ends With the New Id

```java
@Test
void theLocationHeaderEndsWithTheNewId() {
    newTransaction()
    .when()
        .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
    .then()
        .statusCode(201)
        .header("Location", response -> endsWith("/transactions/" + response.path("id")));
}
```

The lambda reads the id from the body, for example `tx-7`, and returns `endsWith("/transactions/tx-7")`. REST Assured then checks the `Location` header with it. The test works whatever id the server picks.

### Step 2: The Ready-Made endsWithPath(..)

This check is so common that REST Assured has it built in:

```java
@Test
void readyMadeResponseAwareMatchers() {
    newTransaction()
    .when()
        .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
    .then()
        .header("Location", endsWithPath("id"));                     // the same check, from RestAssuredMatchers
}
```

`endsWithPath("id")` is a static import from `io.restassured.matcher.RestAssuredMatchers`. It reads the value at the path `id` and checks that the header ends with it. The same class has `equalToPath(..)`, `startsWithPath(..)` and `containsPath(..)`.

> **The code above, ready to run**
>
> All four tests in [`ResponseAwareMatcherTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_05/ResponseAwareMatcherTest.java) pass with `mvn test -pl module-03 -Dtest=ResponseAwareMatcherTest`. Run them in a different order and they still pass: none of them assumes an id.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Checking One Body Value Against Another

```java
@Test
void aBodyValueCheckedAgainstAnotherBodyValue() {
    newTransaction()
    .when()
        .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
    .then()
        .body("type", response -> equalTo(response.<Float>path("amount") < 0 ? "DEBIT" : "CREDIT"));
}
```

The test doesn't hard-code `DEBIT`. It states the rule: the type must agree with the sign of the amount. If the helper sent a positive amount, the same test would expect `CREDIT`.

- **`response.<Float>path("amount")`** reads the amount with a type witness, so the comparison with `0` compiles. The amount is a `Float`, as [Section 3](module-03-section-03-rest-assured-numbers-and-money.md) explains.
- **The rule lives in the test.** Keep it as simple as this. A response-aware matcher that repeats the server's logic in detail tests nothing but itself.

## A Matcher on the Whole Body

```java
@Test
void theWholeBody() {
    newTransaction()
    .when()
        .post("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
    .then()
        .body(containsString("Bakkerij Bart"));                      // a matcher without a path sees the body text
}
```

`body(..)` without a path applies the matcher to the whole body as a string. It is a quick check that a value appears somewhere, but it doesn't say where. Prefer a path when you know one, such as `body("counterparty.name", equalTo("Bakkerij Bart"))`.

## When to Use a Response-Aware Matcher

1. **A value the server generates appears twice**, such as an id in the body and in a header.
2. **Two values must agree by a rule**, such as a type and the sign of an amount, or a total and its items.
3. **Not for values you know.** When the expected value is fixed, write it in the test. A plain matcher is easier to read.

When you need the value in a later request, a response-aware matcher is not enough. Extract it instead, as shown in Module 4.

## Common Pitfalls

1. **Hard-coding a generated id.** `header("Location", endsWith("tx-7"))` passes only when the test runs at the right moment. Build the expectation from the response.
2. **Forgetting the type witness.** `response.path("amount") < 0` doesn't compile, because `path(..)` is generic. Write `response.<Float>path("amount")`.
3. **Repeating the server's logic.** A matcher that recalculates everything the server does will also repeat its bugs. Check a simple rule, or a known value.
4. **A whole-body `containsString(..)` when a path would do.** It passes when the text appears in the wrong field. Use a path.
5. **Shared data between test classes.** Tests that create data must not run against a server that other classes count on. Start an API per class, as this module does.

## Wrapping Up

A response-aware matcher is a lambda from the response to a matcher. It lets a test check values the server chooses, such as a new id in the `Location` header, and rules between two values in one response. `RestAssuredMatchers` has ready-made ones such as `endsWithPath(..)`. A matcher without a path checks the whole body as text. Use response-aware matchers for values you can't know in advance, and plain matchers for everything else.
