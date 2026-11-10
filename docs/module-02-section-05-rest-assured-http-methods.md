# Every HTTP Method in REST Assured: GET, PUT, PATCH, DELETE, HEAD, OPTIONS and QUERY

Every HTTP Method in REST Assured: Read, replace, patch and cancel a payment, get headers with HEAD, discover methods with OPTIONS, search with the new QUERY method, send any method by name, and test a 405.

So far, almost every request in this module has been a `GET` or a `POST`. A real resource supports more. A payment can be read, replaced, partly changed and cancelled, and each of those is a different HTTP method with its own rules. Which methods take a body? What does `HEAD` return? How do you send `QUERY`, a method that is so new your HTTP client may not know it, or a custom method such as `PURGE`?

This post follows [Section 5 of Module 2](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02) of the REST Assured course. It runs every method against one payment and shows how REST Assured sends each of them. The examples are in [`HttpMethodsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_05/HttpMethodsTest.java).

## Which Methods Does REST Assured Support?

After `when()`, REST Assured has a method for each common HTTP method:

| Method | REST Assured | Has a body | What the Payments API does |
| --- | --- | --- | --- |
| GET | `get(..)` | No | Reads a payment |
| POST | `post(..)` | Yes | Creates a payment |
| PUT | `put(..)` | Yes | Replaces a payment |
| PATCH | `patch(..)` | Yes | Changes part of a payment |
| DELETE | `delete(..)` | No | Cancels a payment |
| HEAD | `head(..)` | No | Like GET, but without the body |
| OPTIONS | `options(..)` | No | Lists the allowed methods |
| QUERY | `query(..)` | Yes | Searches, with the criteria in the body |

For any other method, `request(..)` takes the method by name.

## A Fresh Payment for Every Test

Several of these methods change or cancel the payment. If the tests shared one payment, they would depend on the order in which they run. Instead, `@BeforeEach` creates a new payment before every test:

```java
private String paymentId;

@BeforeEach
void createPayment() {
    paymentId = paymentsApi()
        .contentType(ContentType.JSON)
        .body(validPayment("20.00"))
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .extract().path("id");
}
```

`extract().path("id")` reads the `id` from the response body. Module 4 covers `extract()` in depth. The server is still started once per class in `@BeforeAll`, as in every other test class.

## Reading and Changing a Payment

### Step 1: GET Reads It

```java
@Test
void getReadsIt() {
    paymentsApi().when().get("/payments/{id}", paymentId).then().statusCode(200).body("status", equalTo("RECEIVED"));
}
```

### Step 2: PUT Replaces It

```java
@Test
void putReplacesIt() {
    Map<String, Object> replacement = validPayment("99.99");
    replacement.put("creditorName", "Emily Clarke");

    paymentsApi()
        .contentType(ContentType.JSON)
        .body(replacement)
    .when()
        .put("/payments/{id}", paymentId)
    .then()
        .statusCode(200)
        .body("amount", equalTo("99.99"))
        .body("creditorName", equalTo("Emily Clarke"));
}
```

`PUT` replaces the whole resource, so the body must be a complete, valid payment. A field you leave out is gone, not kept.

### Step 3: PATCH Changes Part of It

```java
@Test
void patchChangesPartOfIt() {
    paymentsApi()
        .contentType("application/merge-patch+json")            // RFC 7396 JSON Merge Patch
        .body(Map.of("reference", "Rent March"))
    .when()
        .patch("/payments/{id}", paymentId)
    .then()
        .statusCode(200)
        .body("reference", equalTo("Rent March"))
        .body("amount", equalTo("20.00"));                       // untouched
}
```

- **The body** holds only the fields to change.
- **The content type** is `application/merge-patch+json`, from [RFC 7396](https://www.rfc-editor.org/rfc/rfc7396). It tells the server how to apply the body. The Payments API answers `415` for any other content type.
- **The last check** proves that the fields you didn't send are untouched.

REST Assured serializes the map to JSON because the content type ends in `+json`.

### Step 4: DELETE Cancels It

```java
@Test
void deleteCancelsIt() {
    paymentsApi().when().delete("/payments/{id}", paymentId).then().statusCode(204).body(emptyString());

    paymentsApi().when().get("/payments/{id}", paymentId).then().body("status", equalTo("CANCELLED"));

    paymentsApi()                                                // a cancelled payment can't be replaced
        .contentType(ContentType.JSON)
        .body(validPayment("1.00"))
    .when()
        .put("/payments/{id}", paymentId)
    .then()
        .statusCode(409);
}
```

A payment is never really deleted. `DELETE` answers `204 No Content` and marks it as cancelled. The test then checks the effect from the outside: a `GET` shows the new status, and a `PUT` is refused with `409 Conflict`. Testing what a method changes, not only what it returns, is what makes this a useful test.

> **The code above, ready to run**
>
> All nine tests in [`HttpMethodsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_05/HttpMethodsTest.java) pass with `mvn test -pl module-02 -Dtest=HttpMethodsTest`. Each one runs against its own payment, so you can run any of them on its own.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## Methods Without Side Effects

### HEAD Returns the Headers Only

```java
@Test
void headReturnsTheHeadersOnly() {
    paymentsApi()
    .when()
        .head("/payments/{id}", paymentId)
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(emptyString());
}
```

`HEAD` answers with the same status and headers as `GET`, but without the body. It is a cheap way to check that a resource exists. The content type is still JSON, because it describes the body a `GET` would return.

### OPTIONS Lists the Allowed Methods

```java
@Test
void optionsListsTheAllowedMethods() {
    paymentsApi()
    .when()
        .options("/payments/{id}", paymentId)
    .then()
        .statusCode(204)
        .header("Allow", containsString("PATCH"))
        .header("Allow", containsString("DELETE"));
}
```

The full header is `Allow: GET, PUT, PATCH, DELETE, HEAD, OPTIONS`. The test uses `containsString(..)`, so it doesn't break when the order changes or a method is added.

### QUERY Is a Safe Method With a Body

`GET` has no body, so search criteria go in the URL. Long or sensitive criteria don't belong there: URLs have length limits and end up in access logs. `QUERY` is a new method, specified in an IETF draft, that is safe like `GET` but carries its criteria in the body. REST Assured supports it since version 6.1:

```java
@Test
void queryIsASafeMethodWithABody() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body(Map.of("currency", "EUR", "minAmount", "20.00"))
    .when()
        .query("/payments")
    .then()
        .statusCode(200)
        .body("count", greaterThanOrEqualTo(1))
        .body("payments.currency", everyItem(equalTo("EUR")));
}
```

The answer has the same shape as `GET /payments`. The test uses `greaterThanOrEqualTo(1)` instead of an exact count, because other tests in the class create payments on the same server.

## Any Method by Name

```java
@Test
void anyMethodByName() {
    paymentsApi().when().request(Method.GET, "/payments/{id}", paymentId).then().statusCode(200);

    paymentsApi()                                                // a custom method, such as a cache PURGE
    .when()
        .request("PURGE", "/echo")
    .then()
        .body("method", equalTo("PURGE"));
}
```

- **`request(Method.GET, ..)`** takes the method as an enum. It is useful when the method is a parameter, for example in a parameterized test.
- **`request("PURGE", ..)`** takes any name, including methods that aren't in the HTTP specification. Caches and CDNs use `PURGE` to remove an entry.

## A Method the Resource Doesn't Support

```java
@Test
void aMethodTheResourceDoesntSupport() {
    paymentsApi().when().post("/payments/{id}", paymentId).then().statusCode(405);
}
```

You can't `POST` to a single payment, so the API answers `405 Method Not Allowed`, with the same `Allow` header as `OPTIONS`. Testing the methods an API must refuse is as important as testing the ones it accepts.

## Common Pitfalls

1. **Sending a partial body with PUT.** `PUT` replaces the whole resource. Use `PATCH` to change a few fields.
2. **Sending PATCH as `application/json`.** A merge patch has its own media type. The Payments API answers `415`, and other APIs may apply the body in a different way.
3. **Expecting a body from HEAD.** `HEAD` never has one. Check the status and headers instead.
4. **Exact counts on shared data.** Other tests add payments to the same server. Use `greaterThanOrEqualTo(..)` or `everyItem(..)`, or filter on data the test created itself.
5. **Tests that depend on each other's changes.** If one test cancels a payment that another one reads, the result depends on the order. Create what each test needs in `@BeforeEach`.

## Wrapping Up

REST Assured has a method for every common HTTP method, and `request(..)` sends any other method by name. `PUT` replaces a resource, `PATCH` with `application/merge-patch+json` changes part of it, and `DELETE` here cancels it. `HEAD` gives the headers without a body, `OPTIONS` lists the allowed methods, and `QUERY` searches with the criteria in the body. Give each test its own data in `@BeforeEach`, check what a change does from the outside, and test the methods the API must refuse.
