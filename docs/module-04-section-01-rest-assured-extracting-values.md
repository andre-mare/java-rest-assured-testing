# Extracting Values in REST Assured: extract(), path(..) and ExtractableResponse

Extracting Values in REST Assured: Get values out of a response after checking it, covering extract().path(..) for a value and a list, a kept ExtractableResponse with headers and the status, extract().response(), and a value used in the next request.

Until now, every test ended with checks. Many real tests need more: the id of the customer you just created, so you can read it back; the second item on a page, so you can open it; the `Location` header, so you can follow it. REST Assured can hand you any value from a response. The question is when to take it. Take it too early, and a wrong response gives you a `null` that fails three lines later, far from the real cause.

This post is the first in the series for [Module 4 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-04), which covers getting data out of responses. It shows how to extract single values, lists, headers and whole responses, always after the checks that prove the response is the one you expect. The examples are in [`ExtractingValuesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_01/ExtractingValuesTest.java).

## What is extract()?

`extract()` ends the validation chain and returns an `ExtractableResponse`. From it you can read:

1. **A value at a path**: `extract().path("name")`, with the same GPath as `body(..)`.
2. **A header or the status**: `header("Location")`, `statusCode()` and `contentType()`.
3. **The whole response**: `extract().response()`, a `Response` you can read in any way, or check again.
4. **The body as an object**: `extract().as(..)` and `extract().jsonPath()`, the topics of [Section 2](module-04-section-02-rest-assured-jsonpath-and-xmlpath.md) and [Section 3](module-04-section-03-rest-assured-mapping-to-java-records.md).

Because `extract()` comes after `then()`, every check before it runs first. That order is the point: **validate first, extract second**.

## The API Under Test

Module 4 tests a Customers and Consents API, modelled on open banking account access consents. This section uses its customers. Five exist at start-up, `cust-1` to `cust-5`. `GET /api/v1/customers/cust-1` returns:

```json
{"id":"cust-1","name":"Anna de Vries","email":"anna@example.nl","segment":"RETAIL","createdAt":"2026-11-10T21:22:12.081316Z"}
```

`GET /api/v1/customers?size=2` returns a page:

```json
{
  "items": [
    {"id": "cust-1", "name": "Anna de Vries", "segment": "RETAIL", ...},
    {"id": "cust-2", "name": "Jan Jansen", "segment": "RETAIL", ...}
  ],
  "page": 0,
  "size": 2,
  "totalItems": 5,
  "totalPages": 3
}
```

`POST /api/v1/customers` creates a customer and answers `201` with a `Location` header such as `/api/v1/customers/cust-6`.

## Extracting Values

### Step 1: One Value by Path

```java
@Test
void extractOneValueByPath() {
    String name = consentsApi()
    .when()
        .get("/customers/{id}", "cust-1")
    .then()
        .statusCode(200)
        .extract().path("name");                                    // the type is inferred from the variable

    assertEquals("Anna de Vries", name);
}
```

`path(..)` is generic, so Java infers the type from the variable you assign it to. There is no conversion: the variable's type must match the type REST Assured read. A JSON string is a `String`, and a whole number is an `Integer`, as [Module 3](module-03-section-03-rest-assured-numbers-and-money.md) explains.

### Step 2: A List by Path

```java
@Test
void extractAListByPath() {
    List<String> segments = consentsApi()
    .when()
        .get("/customers")
    .then()
        .extract().path("items.segment");

    assertTrue(segments.contains("BUSINESS"));
}
```

A path that collects a field across a list, such as `items.segment`, returns a `List`.

### Step 3: Keep the ExtractableResponse

When you need several parts of one response, keep the `ExtractableResponse` itself:

```java
@Test
void extractHeadersAndTheStatusCode() {
    ExtractableResponse<Response> created = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewCustomer("Pieter Post", "pieter@example.nl", "RETAIL"))
    .when()
        .post("/customers")
    .then()
        .statusCode(201)
        .extract();                                                 // keep it, and read several parts

    String id = created.path("id");
    assertEquals("/api/v1/customers/" + id, created.header("Location"));
    assertEquals(201, created.statusCode());
    assertTrue(created.contentType().startsWith("application/json"));
}
```

The request is sent once, and every read comes from the same response. The test checks a rule between two of its values: the `Location` header must end with the id from the body.

`NewCustomer` is a record from the tests' [`Models`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/support/Models.java) class. REST Assured serializes it to JSON, as in [Module 2](module-02-section-04-rest-assured-request-bodies.md).

### Step 4: The Whole Response

```java
@Test
void extractTheWholeResponse() {
    Response response = consentsApi()
    .when()
        .get("/customers/{id}", "cust-2")
    .then()
        .statusCode(200)
        .extract().response();

    assertEquals("Jan Jansen", response.path("name"));
    assertTrue(response.asString().contains("\"email\""));          // the raw body as text
    response.then().body("segment", equalTo("RETAIL"));            // and back to validation if you like
}
```

A `Response` has everything: `path(..)`, `asString()`, headers, cookies and timing. It can also start a new validation chain with `then()`.

> **The code above, ready to run**
>
> All five tests in [`ExtractingValuesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_01/ExtractingValuesTest.java) pass with `mvn test -pl module-04 -Dtest=ExtractingValuesTest`. Change `"cust-1"` to `"cust-99"` in the first test and read the failure: it is about the status code, not about a `null`.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 4 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/README.md)

## Using a Value in the Next Request

```java
@Test
void aValueFromOneResponseInTheNextRequest() {
    String secondCustomerOnPage = consentsApi()
        .queryParam("size", 2)
    .when()
        .get("/customers")
    .then()
        .extract().path("items[1].id");

    consentsApi()
    .when()
        .get("/customers/{id}", secondCustomerOnPage)
    .then()
        .statusCode(200)
        .body("id", equalTo(secondCustomerOnPage));
}
```

The first request finds a customer, and the second one opens it. This is the simplest form of a chain of requests. [Section 5](module-04-section-05-rest-assured-chaining-requests.md) builds a whole flow this way.

## Why Validate Before You Extract?

Leave out `statusCode(200)`, and ask for a customer that doesn't exist:

```java
String name = consentsApi().when().get("/customers/{id}", "cust-99").then().extract().path("name");
name.length();
```

The `404` body has no `name`, so `path("name")` returns `null`, and the test fails later with:

```text
java.lang.NullPointerException: Cannot invoke "String.length()" because "<local1>" is null
```

With `statusCode(200)` before `extract()`, the same request fails at once, with a message that names the real problem:

```text
1 expectation failed.
Expected status code <200> but was <404>.
```

## Common Pitfalls

1. **Extracting without validating.** A wrong response becomes a `null` or an empty list, and the test fails later with a confusing error. Check the status, and anything else you rely on, before `extract()`.
2. **The wrong variable type.** `Long total = extract().path("totalItems")` compiles, but throws `ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long`. Use the type REST Assured reads, or a typed getter from [Section 2](module-04-section-02-rest-assured-jsonpath-and-xmlpath.md).
3. **Sending the request twice.** Two chains that each extract one value send two requests, and the second may get a different answer. Keep the `ExtractableResponse` and read both values from it.
4. **Extracting what you could check.** `assertEquals("Anna de Vries", extract().path("name"))` works, but `body("name", equalTo("Anna de Vries"))` is shorter and fails with a better message. Extract values you need later, and check the rest in the chain.
5. **Hard-coding ids the server generates.** Extract them from the response, as the tests do, so they don't depend on what ran before.

## Wrapping Up

`extract()` ends the validation chain and gives you the response's values: `path(..)` for one value or a list, `header(..)` and `statusCode()` for the rest of the response, and `response()` for everything. Keep the `ExtractableResponse` when you need several values. Always validate first and extract second, so a wrong response fails with a clear message, not a `NullPointerException`.
