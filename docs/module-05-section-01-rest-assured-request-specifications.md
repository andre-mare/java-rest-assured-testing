# Request Specifications in REST Assured: RequestSpecBuilder, Layered Specs and Overrides

Request Specifications in REST Assured: Build a RequestSpecification once and reuse it, layer one specification on another, test without credentials, override a header with HeaderConfig, replace the base path, and inspect a specification with SpecificationQuerier.

Look at any test class from the earlier modules and you will find the same lines at the top of every request: base URI, port, base path. Now add an API where every request also needs a bearer token, an `Accept` header and an interaction id. Copy that into fifty tests, then change the token. In [Module 1](module-01-section-03-where-rest-assured-requests-go.md) you saw one way out, static defaults on `RestAssured`, and why they are global state. There is a better way: put the shared set-up in an object.

This post is the first in the series for [Module 5 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-05), which covers removing repetition from a suite and configuring REST Assured safely. It builds request specifications, layers them, and shows how a test changes a value a specification already set. The examples are in [`RequestSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_01/RequestSpecificationTest.java).

## What is a Request Specification?

A `RequestSpecification` holds everything a request can carry: base URI, port, base path, headers, parameters, cookies, body, config and filters. You have used one all along: `given()` returns one. A specification built with `RequestSpecBuilder` is different in two ways:

1. **It is built once and reused.** Each `set..` or `add..` call on the builder adds a setting, and `build()` returns the finished specification.
2. **It can't send requests itself.** You merge it into a new request with `given(spec)` or `given().spec(spec)`, and that request sends.

Unlike `RestAssured.baseURI` and the other static defaults, a specification is an ordinary object. Two test classes can use different ones, even in parallel.

## The API Under Test

Module 5 tests an account information API in the style of the UK Open Banking (OBIE) standard. Every path starts with `/open-banking/v4.0/aisp`, and every request must follow three rules:

| Rule | Without it |
| --- | --- |
| `Authorization: Bearer test-token` | `401` with `WWW-Authenticate: Bearer realm="aisp"` |
| An `x-fapi-interaction-id` header, which the API echoes back | `400` |
| An `Accept` header that allows JSON | `406` |

Bodies use OBIE's envelope, with capitalised `Data`, `Links` and `Meta`. `GET /accounts/22289` returns:

```json
{
  "Data": {
    "Account": [
      {
        "AccountId": "22289",
        "Currency": "EUR",
        "AccountType": "Personal",
        "AccountSubType": "CurrentAccount",
        "Nickname": "Main account",
        "Account": [{"SchemeName": "UK.OBIE.IBAN", "Identification": "NL91ABNA0417164300"}]
      }
    ]
  },
  "Links": {"Self": "/open-banking/v4.0/aisp/accounts/22289"},
  "Meta": {"TotalPages": 1}
}
```

There are three accounts: `22289` and `31820` (personal) and `40114` (business).

## Building Specifications

### Step 1: Build a Specification and One on Top of It

The test class builds two specifications in `@BeforeAll`:

```java
private static RequestSpecification server;          // where the API is
private static RequestSpecification authenticated;   // where it is, plus credentials

@BeforeAll
static void startApiAndBuildSpecifications() {
    api = OpenBankingApi.start();

    server = new RequestSpecBuilder()
            .setBaseUri(api.baseUri())
            .setPort(api.port())
            .setBasePath(OpenBankingApi.BASE_PATH)
            .setAccept(ContentType.JSON)
            .build();

    authenticated = new RequestSpecBuilder()
            .addRequestSpecification(server)          // build on another specification
            .addHeader("Authorization", "Bearer test-token")
            .build();
}
```

- **`server`** says where the API is, and that the client accepts JSON.
- **`authenticated`** starts from `server` with `addRequestSpecification(..)`, and adds the token.

Layering keeps each specification small and gives each a clear purpose. A security test can use `server` to send a request without credentials, and every other test uses `authenticated`.

### Step 2: Use a Specification With given(..)

```java
@Test
void givenTakesASpecification() {
    given(authenticated)
        .header("x-fapi-interaction-id", interactionId())
    .when()
        .get("/accounts")
    .then()
        .statusCode(200)
        .body("Data.Account", hasSize(3));
}
```

The test adds only what is special about it: an interaction id, which must be new for every request. `interactionId()` returns a random UUID.

### Step 3: Or Add It With spec(..)

```java
@Test
void orAddItWithSpec() {
    given()
        .spec(authenticated)
        .header("x-fapi-interaction-id", interactionId())
    .when()
        .get("/accounts/{id}", "22289")
    .then()
        .statusCode(200)
        .body("Data.Account[0].Nickname", equalTo("Main account"));
}
```

`given(spec)` and `given().spec(spec)` do the same thing. Use whichever reads better.

### Step 4: The Specification Without Credentials

```java
@Test
void theSpecificationWithoutCredentialsIsRejected() {
    given(server)
        .header("x-fapi-interaction-id", interactionId())
    .when()
        .get("/accounts")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", "Bearer realm=\"aisp\"");
}
```

Because the token lives in its own layer, the negative test needs no special set-up.

> **The code above, ready to run**
>
> All six tests in [`RequestSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_01/RequestSpecificationTest.java) pass with `mvn test -pl module-05 -Dtest=RequestSpecificationTest`. Add `.log().headers()` before `when()` in the override test to see which `Authorization` header is sent.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 5 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/README.md)

## Changing What a Specification Set

When a test sets something the specification already has, REST Assured merges the two. How they merge depends on what it is.

### Headers Are Merged

Headers merge, as [Module 2](module-02-section-03-rest-assured-headers-cookies-content-types.md) showed. A test that adds its own `Authorization` header sends **both**. Here is the log of a request that tries to replace the token without any extra configuration:

```text
Headers:		Accept=application/json, application/javascript, text/javascript, text/json
				Authorization=Bearer test-token
				Authorization=Bearer revoked-token
				x-fapi-interaction-id=b5ce988b-f7ba-499f-80dd-4351b1db2149
```

The API reads the first `Authorization` header, which is still the valid one, and answers `200`. A test that expects `401` fails. Worse, a test that expects `200` passes for the wrong reason.

To replace the header, tell REST Assured to overwrite headers with that name:

```java
@Test
void overridingAHeaderOfTheSpecification() {
    given(authenticated)
        .config(config().headerConfig(headerConfig().overwriteHeadersWithName("Authorization")))
        .header("Authorization", "Bearer revoked-token")
        .header("x-fapi-interaction-id", interactionId())
    .when()
        .get("/accounts")
    .then()
        .statusCode(401);
}
```

Now only the revoked token is sent, and the API rejects it.

### Other Values Are Replaced

Values that can have only one value, such as the base path, are simply replaced:

```java
@Test
void valuesOtherThanHeadersAreReplaced() {
    given(authenticated)
        .basePath("/test-support")                        // replaces the spec's base path for this request
    .when()
        .get("/landing")
    .then()
        .statusCode(200)
        .body("method", equalTo("GET"));
}
```

The request goes to `/test-support/landing`, with the specification's base URI and port. The specification itself doesn't change: it is immutable, and the next test that uses it gets the original base path.

## Inspecting a Specification

```java
@Test
void aSpecificationCanBeInspected() {
    QueryableRequestSpecification query = SpecificationQuerier.query(authenticated);

    assertEquals(OpenBankingApi.BASE_PATH, query.getBasePath());
    assertEquals(api.port(), query.getPort());
    assertEquals("Bearer test-token", query.getHeaders().getValue("Authorization"));
}
```

`SpecificationQuerier.query(..)` returns a read-only view of a specification. It is useful in a test of the code that builds your specifications, or to debug what a layered specification ends up with.

## Common Pitfalls

1. **Sending from a built specification.** Since REST Assured 6.2, `authenticated.get("/accounts")` throws `IllegalStateException: A request specification built by RequestSpecBuilder can't send a request or hold response expectations itself`. Go through `given(spec)`.
2. **Overriding a header without `HeaderConfig`.** Both values are sent, and the server usually reads the first one, the specification's. Use `overwriteHeadersWithName(..)`.
3. **Per-request values in a shared specification.** An interaction id, an idempotency key or a nonce must be new for every request. Add them in the test, or build a new specification per test, as [Section 5](module-05-section-05-rest-assured-junit-extension.md) does.
4. **One big specification for everything.** A specification with credentials can't be used for a test without them. Layer small specifications with `addRequestSpecification(..)`.
5. **Going back to static defaults.** `RestAssured.requestSpecification = spec` works, but it is the same global state as `RestAssured.baseURI`. Keep specifications in fields or pass them in.

## Wrapping Up

A request specification, built with `RequestSpecBuilder`, holds the set-up that every request to an API shares. Use it with `given(spec)` or `given().spec(spec)`, and layer specifications with `addRequestSpecification(..)`. When a test sets a value the specification has, headers are merged and other values are replaced, so use `HeaderConfig.overwriteHeadersWithName(..)` to replace a header. Specifications are immutable objects, so they are safe to share and need none of the clean-up that static defaults do.
