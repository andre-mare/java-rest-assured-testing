# Path Parameters in REST Assured: Named, Unnamed and URL-Encoded Values

Path Parameters in REST Assured: Fill the placeholders of a path with named and unnamed parameters, mix the two, see how values are URL-encoded, and find a real resource through its Location header.

Most REST resources live at a path with an id in it, such as `/api/v1/payments/pay-42`. You could build that string with `+` and send it, but then what happens when the value has a space in it, or a test needs the same path with two different ids? And when a test fails, how do you know which URL was really sent? REST Assured lets you write the path as a template, `/payments/{id}`, and fill the placeholders separately. It encodes the values for you, and it keeps the template readable.

This post is the first in the series for [Module 2 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02), which covers every way to put data into a request. It shows the two kinds of path parameters, how they combine and how their values are encoded. All the examples are in [`PathParametersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_01/PathParametersTest.java).

## What is a Path Parameter?

A path parameter is a value that fills a `{placeholder}` in the path. REST Assured has two ways to supply it:

1. **Named**, with `pathParam("id", "pay-42")` or `pathParams(map)` on the request specification. The name must match the placeholder.
2. **Unnamed**, as extra arguments to the method that sends the request: `get("/payments/{id}", "pay-42")`. The values fill the placeholders in order, and the names in the path don't matter.

You met the unnamed form in [Module 1](module-01-section-02-anatomy-of-a-rest-assured-test.md). Both forms can be used in one request: named values are filled first, and the unnamed values fill the placeholders that are still open, from left to right.

## The API Under Test

Module 2 tests a Payments API. It runs on the JDK's built-in HTTP server, like the Accounts API of Module 1, and its paths start with `/api/v1`. It has real resources, such as `POST /payments` and `GET /payments/{id}`, but most tests in this module use its **echo** endpoints instead.

### The Echo Endpoints

Any method on `/api/v1/echo`, `/api/v1/echo/{a}` or `/api/v1/echo/{a}/{b}` answers `200` with a JSON description of the request as the server received it. A request to `/api/v1/echo/Anna%20de%20Vries` returns (shortened):

```json
{
  "method": "GET",
  "path": "/api/v1/echo/Anna de Vries",
  "rawPath": "/api/v1/echo/Anna%20de%20Vries",
  "rawQuery": "",
  "query": {},
  "headers": {"accept": ["*/*"], "...": ["..."]},
  "cookies": {},
  "contentType": "",
  "bodyLength": 0,
  "body": ""
}
```

- **`path`** is the path after the server decoded it.
- **`rawPath`** is the path exactly as it was on the wire.

Because the echo shows both, a test can check what REST Assured really sent, not just whether the server accepted it. The other fields are used in the sections that follow.

All tests share one helper that points the request at the API:

```java
private static RequestSpecification paymentsApi() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH);
}
```

## Named Path Parameters

### Step 1: One Named Parameter

```java
@Test
void namedPathParameter() {
    paymentsApi()
        .pathParam("id", "pay-42")
    .when()
        .get("/echo/{id}")
    .then()
        .body("path", equalTo("/api/v1/echo/pay-42"));
}
```

The request specification holds the value, and the path stays a template. That makes the path easy to read and easy to search for in the code base.

### Step 2: Several Named Parameters From a Map

```java
@Test
void severalNamedPathParametersFromAMap() {
    paymentsApi()
        .pathParams(Map.of("resource", "payments", "id", "pay-42"))
    .when()
        .get("/echo/{resource}/{id}")
    .then()
        .body("path", equalTo("/api/v1/echo/payments/pay-42"));
}
```

Because every value has a name, the order of the map doesn't matter.

## Unnamed Path Parameters

### Step 3: Fill the Placeholders by Position

```java
@Test
void unnamedPathParametersAreFilledInOrder() {
    paymentsApi()
    .when()
        .get("/echo/{resource}/{id}", "payments", "pay-42")     // the names don't matter, the order does
    .then()
        .body("path", equalTo("/api/v1/echo/payments/pay-42"));
}
```

This is the shortest form, and the one you will see most. The first value fills the first placeholder, the second value the second.

### Step 4: Mix Named and Unnamed

```java
@Test
void namedAndUnnamedCanBeMixed() {
    paymentsApi()
        .pathParam("resource", "payments")                      // named ones are filled first ...
    .when()
        .get("/echo/{resource}/{id}", "pay-42")                 // ... the rest by position
    .then()
        .body("path", equalTo("/api/v1/echo/payments/pay-42"));
}
```

`{resource}` has a named value, so the only unnamed value goes to `{id}`, the first placeholder that is still open. This is useful when a helper sets the parts of the path that never change, and each test supplies the id.

> **The code above, ready to run**
>
> All six tests in [`PathParametersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_01/PathParametersTest.java) pass with `mvn test -pl module-02 -Dtest=PathParametersTest`. You need JDK 26 and Maven 3.9, and nothing else.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## URL Encoding

A path can't contain a space, so REST Assured encodes every path parameter value before it sends the request:

```java
@Test
void valuesAreUrlEncoded() {
    paymentsApi()
    .when()
        .get("/echo/{name}", "Anna de Vries")
    .then()
        .body("rawPath", equalTo("/api/v1/echo/Anna%20de%20Vries"))   // on the wire
        .body("path", equalTo("/api/v1/echo/Anna de Vries"));         // after the server decoded it
}
```

- **`rawPath`** proves that the space travelled as `%20`.
- **`path`** proves that the server decoded it back to the original value.

Pass the plain value and let REST Assured encode it. If you encode it yourself first, it is encoded twice. [Section 2](module-02-section-02-rest-assured-query-and-form-parameters.md) shows what that looks like and how to turn encoding off.

## A Real Resource

The echo endpoints show what was sent. The last test uses a real resource: it creates a payment, then reads it back by its id.

```java
@Test
void aRealResource() {
    String location = paymentsApi()
        .header("X-Request-ID", "path-params-1")
        .contentType("application/json")
        .body(validPaymentJson("10.00"))
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .extract().header("Location");                          // /api/v1/payments/pay-N (Module 4 covers extract)

    String id = location.substring(location.lastIndexOf('/') + 1);

    paymentsApi()
    .when()
        .get("/payments/{id}", id)
    .then()
        .statusCode(200)
        .body("id", equalTo(id));
}
```

`POST /payments` answers `201 Created` with a `Location` header such as `/api/v1/payments/pay-1`. The test takes the id from the end of it and uses it as a path parameter. Headers and bodies are the topics of [Section 3](module-02-section-03-rest-assured-headers-cookies-content-types.md) and [Section 4](module-02-section-04-rest-assured-request-bodies.md), and `extract()` is covered in Module 4.

Ids come from a counter, so the test never assumes `pay-1`. It uses whatever id the API gave it.

## Common Pitfalls

1. **Building the path with string concatenation.** `"/payments/" + id` works until the value has a space or a slash in it. A template with a path parameter is encoded for you and reads better.
2. **Encoding the value yourself.** `"Anna%20de%20Vries"` is sent as `Anna%2520de%2520Vries`, because the `%` is encoded too.
3. **A typo in a named parameter.** `pathParam("ID", ..)` doesn't fill `{id}`. REST Assured fails before the request is sent and names the placeholder that has no value.
4. **The wrong order for unnamed values.** `get("/echo/{resource}/{id}", "pay-42", "payments")` sends `/echo/pay-42/payments`. The names in the template don't help, only the order counts.
5. **Hard-coding ids that the server generates.** Read the id from the response, as in the last test, so the test doesn't depend on the order in which tests run.

## Wrapping Up

Write the path as a template and fill its placeholders with path parameters. Use `pathParam(..)` or `pathParams(..)` when the name makes the test clearer, and unnamed values when the order is obvious. The two can be mixed: named values first, then the rest by position. REST Assured encodes every value, so pass it as it is, and use the echo endpoint's `rawPath` whenever you want to see what really went over the wire.
