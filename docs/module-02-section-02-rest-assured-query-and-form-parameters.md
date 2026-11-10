# Query, Form and Plain Parameters in REST Assured: queryParam, formParam and param

Query, Form and Plain Parameters in REST Assured: Send query parameters, multi-value and empty parameters, java.time values and form bodies, control URL encoding, and learn where the catch-all param(..) puts its value.

A search endpoint takes its filters from the query string: `?status=RECEIVED&currency=EUR`. A classic HTML form sends its fields in the body, as `name=Jan%20Jansen&iban=...`. Both are name and value pairs, and REST Assured has a method for each, plus a third one that picks for you. Which one do you use, and where does each value end up? What happens to a `&` or a space in a value? And what if a value is already encoded?

This post follows [Section 2 of Module 2](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02) of the REST Assured course. It sends parameters to the echo endpoint, which reports the raw query and the body exactly as they arrived, so every claim below is checked by a test. The examples are in [`ParametersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_02/ParametersTest.java).

## What are the Three Kinds of Parameters?

REST Assured has three methods for name and value pairs:

1. **`queryParam(..)`** always puts the value in the URL, after the `?`.
2. **`formParam(..)`** always puts the value in the body, encoded as `application/x-www-form-urlencoded`.
3. **`param(..)`** picks for you: the query string for `GET`, and a form body for `POST` when the request has no other body.

Each has a plural form that takes a map: `queryParams(..)`, `formParams(..)` and `params(..)`.

## What the Echo Shows

The echo endpoint from [Section 1](module-02-section-01-rest-assured-path-parameters.md) reports three fields that matter here:

- **`rawQuery`**: the query string as it was sent, such as `status=RECEIVED&status=CANCELLED`. It is `""` when the URL has no query.
- **`query`**: the decoded query parameters, as a map from each name to the **list** of its values, such as `{"status":["RECEIVED","CANCELLED"]}`.
- **`body`** and **`bodyLength`**: the body as text, and its length in bytes.

Because `query.status` is a list, the tests check it with `contains(..)`, which matches every element in order.

## Query Parameters

### Step 1: One Parameter, or Several From a Map

```java
@Test
void oneQueryParameter() {
    paymentsApi()
        .queryParam("status", "RECEIVED")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("status=RECEIVED"))
        .body("query.status", contains("RECEIVED"));
}
```

A map sends every entry as a parameter:

```java
paymentsApi()
    .queryParams(Map.of("status", "RECEIVED", "currency", "EUR"))
.when()
    .get("/echo")
.then()
    .body("query.status", contains("RECEIVED"))
    .body("query.currency", contains("EUR"));
```

### Step 2: Several Values for One Name

```java
@Test
void aMultiValueQueryParameterRepeatsTheName() {
    paymentsApi()
        .queryParam("status", "RECEIVED", "CANCELLED")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("status=RECEIVED&status=CANCELLED"))
        .body("query.status", contains("RECEIVED", "CANCELLED"));
}
```

REST Assured repeats the name for every value. That is how the Payments API expects several statuses on `GET /payments`. Some APIs want `status=RECEIVED,CANCELLED` instead. In that case, join the values yourself and pass one string.

### Step 3: A Parameter Without a Value

```java
@Test
void aQueryParameterWithoutAValue() {
    paymentsApi()
        .queryParam("verbose")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("verbose"));
}
```

Only the name is sent, with no `=`. APIs use this for flags.

### Step 4: Dates and Times

```java
@Test
void javaTimeValuesAreSentAsIso8601() {
    paymentsApi()
        .queryParam("from", LocalDate.of(2026, 1, 31))
    .when()
        .get("/echo")
    .then()
        .body("query.from", contains("2026-01-31"));
}
```

Since REST Assured 6.1, a `java.time` value is sent with its `toString()`, which is ISO-8601. You can pass a `LocalDate`, `Instant` or `OffsetDateTime` directly, without formatting it first.

### Step 5: Filter Real Data

Query parameters are not only for the echo. `GET /payments` filters on `status` and `currency`:

```java
@Test
void queryParametersFilterRealData() {
    paymentsApi()
        .queryParam("currency", "SEK")                              // nobody pays in SEK in this module
    .when()
        .get("/payments")
    .then()
        .statusCode(200)
        .body("count", equalTo(0));
}
```

## URL Encoding

### Values Are Encoded by Default

```java
@Test
void valuesAreUrlEncodedByDefault() {
    paymentsApi()
        .queryParam("name", "Anna de Vries & Co")
    .when()
        .get("/echo")
    .then()
        .body("query.name", contains("Anna de Vries & Co"));         // the server decodes it back
}
```

Without encoding, the `&` would end the parameter and start a new one called ` Co`. REST Assured encodes it, so the server gets the value back in one piece.

### An Encoded Value Is Encoded Again

```java
@Test
void anAlreadyEncodedValueIsEncodedAgain() {
    paymentsApi()
        .queryParam("q", "a%20b")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("q=a%2520b"));                   // % itself became %25
}
```

REST Assured can't tell an encoded value from a value that happens to contain `%`, so it encodes the `%` as `%25`. The server then decodes it to `a%20b`, not `a b`.

### Turn Encoding Off

When you already have encoded values, for example from a URL you copied, turn encoding off:

```java
@Test
void urlEncodingCanBeTurnedOff() {
    paymentsApi()
        .urlEncodingEnabled(false)                                  // you are responsible for encoding now
        .queryParam("q", "a%20b")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("q=a%20b"))
        .body("query.q", contains("a b"));
}
```

`urlEncodingEnabled(false)` applies to the whole request, including its path parameters. From then on, every value must be encoded already.

> **The code above, ready to run**
>
> All 14 tests in [`ParametersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_02/ParametersTest.java) pass with `mvn test -pl module-02 -Dtest=ParametersTest`. Add `.log().all()` before `when()` in any of them to see the URL and body REST Assured builds.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## Form Parameters

### Step 1: A Form Body

```java
@Test
void formParametersBecomeAUrlEncodedBody() {
    paymentsApi()
        .formParam("name", "Jan Jansen")
        .formParam("iban", "NL69INGB0123456789")
    .when()
        .post("/echo")
    .then()
        .body("contentType", startsWith("application/x-www-form-urlencoded"))
        .body("rawQuery", equalTo(""))                              // nothing in the URL
        .body("body", equalTo("name=Jan%20Jansen&iban=NL69INGB0123456789"));
}
```

- **The content type** is set for you. The test uses `startsWith(..)`, because REST Assured adds a charset to it ([Section 3](module-02-section-03-rest-assured-headers-cookies-content-types.md) explains why).
- **The URL** has no query string. Everything is in the body.
- **A space** is sent as `%20`, not as `+`. Browsers send `+` in forms, but some servers decode `+` wrongly, so REST Assured uses `%20`, which every server reads correctly.

### Step 2: Create a Beneficiary With a Form

`POST /beneficiaries` only accepts a form, like an HTML page would send:

```java
@Test
void aFormCreatesABeneficiary() {
    paymentsApi()
        .formParam("name", "Jan & Piet")
        .formParam("iban", "NL69INGB0123456789")
    .when()
        .post("/beneficiaries")
    .then()
        .statusCode(201)
        .body("name", equalTo("Jan & Piet"))                        // encoded on the way, decoded by the server
        .body("$", not(hasKey("nickname")));                        // "$" is the whole body
}
```

- **`"Jan & Piet"`** survives the trip because the `&` is encoded.
- **`"$"`** is the GPath for the whole body. The API leaves out fields that have no value, so a beneficiary without a nickname has no `nickname` key at all, and `not(hasKey(..))` checks exactly that.

## The Catch-All param(..)

`param(..)` decides where the value goes from the HTTP method. For `GET`, it goes in the query string:

```java
@Test
void paramGoesIntoTheQueryForGet() {
    paymentsApi()
        .param("status", "RECEIVED")
    .when()
        .get("/echo")
    .then()
        .body("rawQuery", equalTo("status=RECEIVED"))
        .body("bodyLength", equalTo(0));
}
```

For `POST`, the same call puts it in a form body:

```java
@Test
void paramGoesIntoAFormBodyForPost() {
    paymentsApi()
        .param("status", "RECEIVED")
    .when()
        .post("/echo")
    .then()
        .body("rawQuery", equalTo(""))
        .body("body", equalTo("status=RECEIVED"));
}
```

That is convenient, but the reader has to know the rule to see where the value goes. In tests, `queryParam(..)` and `formParam(..)` say what they mean, so prefer them.

## Checking Every Value

When a parameter has several values, `everyItem(..)` checks all of them:

```java
paymentsApi()
    .queryParam("status", "RECEIVED", "RECEIVED")
.when()
    .get("/echo")
.then()
    .body("query.status", everyItem(equalTo("RECEIVED")));
```

## Common Pitfalls

1. **Using `param(..)` and expecting the query string on a POST.** It goes in the body. Use `queryParam(..)` when the value must be in the URL, whatever the method.
2. **Encoding values yourself.** `a%20b` arrives as `a%2520b`. Pass plain values, or turn encoding off with `urlEncodingEnabled(false)`.
3. **Comparing a multi-value parameter with `equalTo(..)`.** In the echo, `query.status` is a list, even for one value. Use `contains(..)` or `hasItem(..)`.
4. **Expecting `+` for a space in a form.** REST Assured sends `%20`. A server that only understands `+` is the exception, not the rule.
5. **Mixing `formParam(..)` with `body(..)`.** A form is the body. REST Assured refuses to send both and throws `IllegalStateException: You can either send form parameters OR body content in POST, not both!`

## Wrapping Up

`queryParam(..)` always writes to the URL, `formParam(..)` always writes a form body, and `param(..)` picks from the method. A parameter with several values repeats its name, a parameter without a value sends only its name, and `java.time` values are sent as ISO-8601. REST Assured encodes every value, including the `%` of a value you encoded yourself, unless you call `urlEncodingEnabled(false)`.
