# Headers, Cookies and Content Types in REST Assured: What Is Merged, What Is Overwritten

Headers, Cookies and Content Types in REST Assured: Send headers one by one, from a map and as objects, learn which headers are merged and which are overwritten, see the charset REST Assured adds to text content types, and send cookies.

Headers carry the information about a request that isn't the data itself: a request id for tracing, the media types the client accepts, the type of the body, a session cookie. Setting one is a single method call. But what happens when a helper sets `X-Tag` and the test sets it again: does the second value replace the first, or are both sent? Why does a `text/plain` body arrive as `text/plain; charset=ISO-8859-1`? And how do several cookies travel?

This post follows [Section 3 of Module 2](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02) of the REST Assured course. It sends headers and cookies to the echo endpoint and checks exactly what the server received. The examples are in [`HeadersAndCookiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_03/HeadersAndCookiesTest.java).

## What Can You Set?

The request specification has a method for each part:

1. **`header(name, value, ..)`** adds one header, with one or more values. `headers(map)` adds several, and `header(new Header(..))` adds a header object.
2. **`accept(..)`** sets the `Accept` header, the media types the client wants back.
3. **`contentType(..)`** sets the `Content-Type` header, the media type of the body.
4. **`cookie(name, value)`** adds a cookie. `cookies(map)` adds several, and `cookie(new Cookie.Builder(..).build())` adds a cookie object.

## Reading Headers in the Echo

The echo endpoint returns `headers` as a map from each name to the list of its values. HTTP header names are case-insensitive, so the echo writes every name in lower case. A name with dashes must be quoted in a GPath, because a dash would otherwise be read as a minus sign:

```java
.body("headers.'x-request-id'", contains("req-1"))
```

The echo also returns `cookies`, a map from each cookie name to its value, and `contentType`, the `Content-Type` header as received.

## Headers

### Step 1: One Header

```java
@Test
void oneHeader() {
    paymentsApi()
        .header("X-Request-ID", "req-1")
    .when()
        .get("/echo")
    .then()
        .body("headers.'x-request-id'", contains("req-1"))
        .header("X-Request-ID", "req-1");                          // the API echoes it in the response
}
```

The body check reads the header the server received. The `header(..)` check after `then()` reads a response header: the Payments API copies `X-Request-ID` into every response, so a request can be traced from end to end.

### Step 2: Several Headers From a Map or as Objects

```java
@Test
void severalHeadersFromAMapOrAsObjects() {
    paymentsApi()
        .headers(Map.of("X-Request-ID", "req-2", "X-Channel", "mobile"))
        .header(new Header("X-Device", "android"))
    .when()
        .get("/echo")
    .then()
        .body("headers.'x-channel'", contains("mobile"))
        .body("headers.'x-device'", contains("android"));
}
```

A `Header` object is useful when headers are built in one place and used in another, for example in a helper method.

### Step 3: A Header With Several Values

```java
@Test
void aHeaderWithSeveralValues() {
    paymentsApi()
        .header("X-Tag", "salary", "monthly")                      // sent as two X-Tag headers
    .when()
        .get("/echo")
    .then()
        .body("headers.'x-tag'", contains("salary", "monthly"));
}
```

## Merging and Overwriting

### Most Headers Are Merged

Setting the same header twice doesn't replace the first value. Both are sent:

```java
@Test
void theSameHeaderTwiceIsMerged() {
    paymentsApi()
        .header("X-Tag", "salary")
        .header("X-Tag", "monthly")
    .when()
        .get("/echo")
    .then()
        .body("headers.'x-tag'", contains("salary", "monthly"));
}
```

### Accept and Content-Type Are Overwritten

A request can only have one body type, and a client usually wants one answer type, so these two headers are the exception. The last value wins:

```java
@Test
void acceptAndContentTypeAreOverwrittenNotMerged() {
    paymentsApi()
        .header("Accept", "text/plain")
        .header("Accept", "application/json")                      // replaces the first value
    .when()
        .get("/echo")
    .then()
        .body("headers.accept", contains("application/json"));
}
```

### Choose Which Headers Are Overwritten

`HeaderConfig` sets which header names are overwritten instead of merged:

```java
@Test
void headerConfigDecidesWhichHeadersAreOverwritten() {
    paymentsApi()
        .config(config().headerConfig(headerConfig().overwriteHeadersWithName("X-Tag")))
        .header("X-Tag", "salary")
        .header("X-Tag", "monthly")
    .when()
        .get("/echo")
    .then()
        .body("headers.'x-tag'", contains("monthly"));
}
```

This matters most when a shared specification sets a header and a test wants to replace it. Module 5 covers shared specifications and `RestAssuredConfig` in depth.

### accept(..) Has Its Own Method

```java
@Test
void acceptHasItsOwnMethod() {
    paymentsApi()
        .accept(ContentType.JSON)
    .when()
        .get("/echo")
    .then()
        .body("headers.accept[0]", equalTo("application/json, application/javascript, text/javascript, text/json"));
}
```

`ContentType.JSON` stands for every JSON media type REST Assured knows, so `accept(ContentType.JSON)` sends all four. If an API is strict about `Accept`, pass a string instead: `accept("application/json")`.

> **The code above, ready to run**
>
> All 12 tests in [`HeadersAndCookiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_03/HeadersAndCookiesTest.java) pass with `mvn test -pl module-02 -Dtest=HeadersAndCookiesTest`. Remove the `HeaderConfig` from the third merging test and watch it fail with both values.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## Content Types and Charsets

### JSON Is Sent as It Is

```java
@Test
void jsonGetsNoCharsetAdded() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body("{}")
    .when()
        .post("/echo")
    .then()
        .body("contentType", equalTo("application/json"));
}
```

JSON is always UTF-8 ([RFC 8259](https://www.rfc-editor.org/rfc/rfc8259)), so REST Assured adds no charset.

### Text Gets a Charset Added

```java
@Test
void textGetsTheDefaultCharsetAdded() {
    paymentsApi()
        .contentType("text/plain")
        .body("hello")
    .when()
        .post("/echo")
    .then()
        .body("contentType", equalTo("text/plain; charset=ISO-8859-1"));
}
```

This surprises many people. For text types, REST Assured adds `ISO-8859-1`, the default charset of the old HTTP/1.1 specification. It is harmless for plain ASCII, but a server that checks the content type exactly, or a body with characters outside ISO-8859-1, can trip over it. Form bodies get the same charset, which is why [Section 2](module-02-section-02-rest-assured-query-and-form-parameters.md) checks them with `startsWith(..)`.

### Turn the Charset Off

```java
@Test
void addingTheCharsetCanBeTurnedOff() {
    paymentsApi()
        .config(config().encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
        .contentType("text/plain")
        .body("hello")
    .when()
        .post("/echo")
    .then()
        .body("contentType", equalTo("text/plain"));
}
```

You can also name a charset yourself, such as `contentType("text/plain; charset=UTF-8")`. REST Assured only adds one when the content type has none.

## Cookies

### Step 1: One Cookie

```java
@Test
void oneCookie() {
    paymentsApi()
        .cookie("theme", "dark")
    .when()
        .get("/echo")
    .then()
        .body("cookies", hasEntry("theme", "dark"));
}
```

### Step 2: Several Cookies Share One Header

```java
@Test
void severalCookiesShareOneCookieHeader() {
    paymentsApi()
        .cookies(Map.of("theme", "dark"))
        .cookie(new Cookie.Builder("lang", "nl").build())
    .when()
        .get("/echo")
    .then()
        .body("cookies.theme", equalTo("dark"))
        .body("cookies.lang", equalTo("nl"))
        .body("headers.cookie.size()", equalTo(1));
}
```

All cookies travel in a single `Cookie` header, as `theme=dark; lang=nl`. The last check proves it: the `cookie` header has exactly one value. `Cookie.Builder` can also set attributes such as the domain and path, which matter for cookies the server sends back. Module 3 checks those.

## Common Pitfalls

1. **Expecting a second `header(..)` call to replace the first.** For every header except `Accept` and `Content-Type`, both values are sent. Use `HeaderConfig.overwriteHeadersWithName(..)` when you need to replace one.
2. **Forgetting the quotes in a GPath.** `headers.x-request-id` is read as `headers.x` minus `request` minus `id`. Write `headers.'x-request-id'`.
3. **Comparing a text content type exactly.** `text/plain` is sent as `text/plain; charset=ISO-8859-1`. Check with `startsWith(..)`, set a charset yourself, or turn the default off.
4. **Using `accept(ContentType.JSON)` against a strict API.** It sends four media types. Pass the one string the API expects.
5. **Setting a cookie with `header("Cookie", ..)`.** It works on its own, but as soon as the request also has a `cookie(..)`, REST Assured writes its own `Cookie` header and the one you set by hand is lost. Use `cookie(..)` for every cookie.

## Wrapping Up

Headers are set one by one, from a map or as objects, and a header can have several values. Setting a header twice merges the values, except for `Accept` and `Content-Type`, which are overwritten. `HeaderConfig` changes that per header. REST Assured adds `charset=ISO-8859-1` to text content types unless you name a charset or turn the default off with `EncoderConfig`. Cookies are added like headers, and all of them travel in one `Cookie` header.
