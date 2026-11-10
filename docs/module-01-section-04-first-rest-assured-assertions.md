# First REST Assured Assertions: Status, Content Type, Headers, Body Paths and Response Time

First REST Assured Assertions: Check every part of an HTTP response with REST Assured, covering status codes and lines, content types, case-insensitive headers, GPath body paths, lists, Float numbers, problem details and response time.

Checking that an endpoint returns `200` is a start, but a client relies on much more than the status. Is the response really JSON? Does it set `Cache-Control: no-store` on account data? Does the list contain the four accounts you expect, and does the last one belong to Emily Clarke? And when something goes wrong, does the error look like an error the client can parse?

This post follows [Section 4 of Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) of the REST Assured course. It covers every check you need for a first real test suite, written against the Accounts API in [`FirstAssertionsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_04/FirstAssertionsTest.java). The [REST Assured usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#verifying-response-data) has the full reference.

## What Can You Check After then()?

Every check after `then()` takes either a plain value or a Hamcrest `Matcher`. These are the parts of a response you can check:

1. **Status**: `statusCode(..)` for the number and `statusLine(..)` for the whole first line, such as `HTTP/1.1 200 OK`.
2. **Content type**: `contentType(..)`, with a `ContentType` constant or a string.
3. **Headers**: `header(name, ..)` for one header and `headers(..)` for several.
4. **Body**: `body(path, ..)`, where the path is a GPath expression into the JSON or XML body.
5. **Time**: `time(..)`, the response time measured on the client.

`statusCode(200)` and `statusCode(equalTo(200))` mean the same thing. The matcher form lets you say more, for example `statusCode(lessThan(300))`.

## The Data Under Test

The Accounts API never changes its data, so tests can assert exact values. `GET /api/v1/accounts` returns `{"count":4,"accounts":[...]}`, and one account looks like this:

```json
{"id":"acc-1001","iban":"NL91ABNA0417164300","owner":"Anna de Vries","type":"CURRENT",
 "currency":"EUR","balance":1250.50,"status":"ACTIVE","openedOn":"2019-04-01"}
```

The other accounts are `acc-1002` (Anna de Vries, savings), `acc-1003` (Jan Jansen, blocked, with a balance of `-42.10`) and `acc-1004` (Emily Clarke, GBP). Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with the content type `application/problem+json`.

All tests share a small helper that returns the configured request:

```java
private static RequestSpecification accountsApi() {
    return given()
        .baseUri(api.baseUri())
        .port(api.port())
        .basePath(AccountsApi.BASE_PATH);
}
```

## Status, Content Type and Headers

### Step 1: Check the Status Code and Status Line

```java
@Test
void statusCodeAndStatusLine() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .statusCode(200)
        .statusCode(lessThan(300))                      // any matcher works, not only a number
        .statusLine("HTTP/1.1 200 OK")                  // the full first line of the response
        .statusLine(containsString("OK"));
}
```

### Step 2: Check the Content Type

```java
@Test
void contentType() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .contentType(ContentType.JSON)                  // ignores parameters such as "; charset=utf-8"
        .contentType("application/json");               // a String must be how the actual value starts
}
```

- **`ContentType.JSON`** matches `application/json` with any parameters. It also matches any `+json` type, such as `application/problem+json`.
- **A string** must be how the actual header value starts, so `"application/json"` matches `application/json; charset=utf-8`.

### Step 3: Check Headers

```java
@Test
void headers() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .header("Cache-Control", "no-store")
        .header("X-Request-Id", notNullValue())
        .header("X-Request-Id", matchesPattern("[0-9a-f-]{36}"));   // a UUID
}
```

HTTP header names are case-insensitive, and so is REST Assured. The JDK server sends `Cache-control` and `X-request-id`, and every spelling matches:

```java
.header("cache-control", "no-store")
.header("CACHE-CONTROL", "no-store");
```

A header you send can also be checked on the way back. The API echoes `X-Correlation-Id`:

```java
accountsApi()
    .header("X-Correlation-Id", "test-42")         // a header on the request (more in Module 2)
.when()
    .get("/accounts")
.then()
    .header("X-Correlation-Id", "test-42");
```

## Body Paths with GPath

REST Assured reads the body with GPath, the path language of Groovy. For JSON, the main forms are:

1. **A field**: `"owner"` reads one value from an object.
2. **An index**: `"accounts[0].id"` reads from the first element of a list, and `"accounts[-1].id"` from the last one.
3. **A collected field**: `"accounts.id"` collects the `id` of every element into a list.
4. **A method call**: `"accounts.size()"` calls `size()` on the list.

### Single Values

```java
@Test
void singleValuesInTheBody() {
    accountsApi()
    .when()
        .get("/accounts/{id}", "acc-1001")
    .then()
        .body("id", equalTo("acc-1001"))
        .body("iban", equalTo("NL91ABNA0417164300"))
        .body("status", is("ACTIVE"))                   // is(..) only makes the sentence read better
        .body("openedOn", equalTo("2019-04-01"));       // a LocalDate arrives as an ISO-8601 string
}
```

### Several Paths in One Call

`body(..)` takes any number of path and matcher pairs:

```java
.body("owner", equalTo("Jan Jansen"),
      "status", equalTo("BLOCKED"),
      "currency", equalTo("EUR"));
```

This is more than a shortcut. As [Section 6](module-01-section-06-reading-rest-assured-failures.md) shows, all pairs in one call are checked together, so a failure reports every mismatch at once.

### Lists

```java
@Test
void valuesInsideAList() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .body("count", equalTo(4))
        .body("accounts", hasSize(4))
        .body("accounts.size()", equalTo(4))                        // GPath can call size() itself
        .body("accounts[0].id", equalTo("acc-1001"))                // index into the list
        .body("accounts[-1].id", equalTo("acc-1004"))               // negative index counts from the end
        .body("accounts.id", hasItems("acc-1002", "acc-1003"))      // "accounts.id" collects every id
        .body("accounts.currency", everyItem(is(notNullValue())));
}
```

### Numbers Are Floats

JSON has one number type. REST Assured reads a decimal such as `1250.50` as a `Float`, so the expected value needs the `f` suffix:

```java
.body("balance", equalTo(1250.50f));
```

Comparing with `1250.50`, which is a `double`, fails even though the numbers look the same. Module 3 shows how to read decimals as `BigDecimal`, which is what money needs.

> **The code above, ready to run**
>
> All 14 tests in [`FirstAssertionsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_04/FirstAssertionsTest.java) pass with `mvn test -pl module-01 -Dtest=FirstAssertionsTest`. Change one expected value at a time and read the failure.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## Query Parameters and Errors

### Filtering With a Query Parameter

The accounts list accepts `status` and `currency` filters. `queryParam(..)` adds them to the URL:

```java
accountsApi()
    .queryParam("currency", "GBP")                  // sent as ?currency=GBP (more in Module 2)
.when()
    .get("/accounts")
.then()
    .body("count", equalTo(1))
    .body("accounts[0].owner", equalTo("Emily Clarke"));
```

### Problem Details

An unknown account gives a `404` with an RFC 9457 body. Because `ContentType.JSON` accepts any `+json` type, body paths work on errors too:

```java
@Test
void errorsAreProblemDetails() {
    accountsApi()
    .when()
        .get("/accounts/{id}", "acc-9999")
    .then()
        .statusCode(404)
        .contentType("application/problem+json")        // RFC 9457
        .contentType(ContentType.JSON)                  // any "+json" type counts as JSON
        .body("title", equalTo("Not Found"))
        .body("status", equalTo(404))
        .body("detail", containsString("acc-9999"));
}
```

### Unsupported Methods

Any method other than `GET` gives `405 Method Not Allowed` with an `Allow` header that lists the methods that are allowed:

```java
accountsApi()
.when()
    .delete("/accounts/{id}", "acc-1001")
.then()
    .statusCode(405)
    .header("Allow", "GET");
```

## Response Time

```java
@Test
void responseTime() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .time(lessThan(2L), TimeUnit.SECONDS);
}
```

Response time is measured on the client, so it includes the network and the JVM. Use it for generous upper bounds that catch something badly wrong. It is not a tool for performance testing.

## Common Pitfalls

1. **Comparing decimals with a `double`.** `equalTo(1250.50)` fails because the value is a `Float`. Use `1250.50f` for now, and `BigDecimal` for money from Module 3 on.
2. **Using a string for a `+json` content type.** `contentType("application/json")` does not match `application/problem+json`. Use `ContentType.JSON` when any JSON will do.
3. **Expecting header names to be case-sensitive.** They are not. Check the header value, not the spelling of the name.
4. **Mixing up the HTTP status and a body field.** `statusCode(404)` and `body("status", equalTo(404))` check different things. Problem details carry both, so check both.
5. **Tight response-time limits.** A limit of 50 ms passes on your laptop and fails on a busy CI agent. Keep limits generous.

## Wrapping Up

After `then()`, you can check the status code and line, the content type, any header, any value in the body and the response time. Each check takes a value or a Hamcrest matcher. GPath reaches into objects and lists, collects fields across a list and calls methods such as `size()`. Remember that decimals are `Float` by default, and that `ContentType.JSON` accepts every `+json` type.