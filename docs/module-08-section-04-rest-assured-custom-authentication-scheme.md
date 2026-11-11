# A Custom Authentication Scheme in REST Assured: ContextAuthenticationScheme for an API Key

A Custom Authentication Scheme in REST Assured: Write an API key scheme with ContextAuthenticationScheme (REST Assured 6.3), use it in a specification, remove it with auth().none(), replace it with another scheme, and set it as the global default.

Not every API uses Basic or OAuth 2.0. Partner APIs often authenticate with an API key in a header, such as `X-API-Key`. You could add that header to a specification and be done. But then how does a test send a request *without* the key, to check that the API refuses it? A header in a specification is merged into every request, and there is no simple way to take it back out. An authentication scheme behaves differently: it can be replaced or removed per request, like any other.

This post follows [Section 4 of Module 8](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-08) of the REST Assured course. It writes an API key scheme and uses it wherever REST Assured accepts authentication. The examples are in [`CustomAuthenticationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_04/CustomAuthenticationTest.java).

## What is a ContextAuthenticationScheme?

`ContextAuthenticationScheme`, new in REST Assured 6.3, is a functional interface for custom authentication. Its one method gets an `AuthenticationContext`, which can:

1. **Set headers**, such as an API key.
2. **Answer Basic challenges**, sign with OAuth, or use a client certificate.

It does all this without Apache HttpClient 4 types. Older custom schemes implemented `AuthenticationScheme` and worked directly with HttpClient 4 classes, which leave REST Assured's API in version 7.

Why a scheme instead of a header? Because authentication is one slot on a request:

| | A header in a specification | An authentication scheme |
| --- | --- | --- |
| A request sets another one | Both are sent (merged) | The request's scheme replaces it |
| `auth().none()` | The header is still sent | Removed |

## The Endpoint Under Test

`GET /api/v1/partner/accounts` needs `X-API-Key: partner-key-123` and an `X-Correlation-Id`. Without a valid key:

```json
{"type":"about:blank","title":"Unauthorized","status":401,"detail":"A valid X-API-Key is required","instance":"/api/v1/partner/accounts"}
```

## The Scheme

```java
static ContextAuthenticationScheme apiKey(String key) {
    return context -> context.header("X-API-Key", key);
}
```

One line. The lambda gets the context and sets the header. It is used with `setAuth(..)` in a specification:

```java
private static RequestSpecification partnerApi() {
    return new RequestSpecBuilder()
        .setBaseUri(api.baseUri()).setPort(api.port())
        .addFilter(new CorrelationIdFilter())
        .setAuth(apiKey(SignedPaymentsApi.PARTNER_API_KEY))
        .build();
}
```

The `CorrelationIdFilter` from [Section 1](module-08-section-01-rest-assured-writing-filters.md) adds the other header the endpoint needs.

## Using the Scheme

### Step 1: The Scheme in a Specification

```java
@Test
void theSchemeInASpecification() {
    given(partnerApi()).when().get("/api/v1/partner/accounts").then().statusCode(200).body("$", hasSize(1));
}
```

### Step 2: auth().none() Removes It

```java
@Test
void authNoneRemovesIt() {
    given(partnerApi()).auth().none().when().get("/api/v1/partner/accounts").then().statusCode(401);
}
```

The negative test uses the same specification as every other test, and removes only the key. Had the key been a plain header in the specification, `auth().none()` would have left it in place, and this request would have got `200`.

### Step 3: Another Scheme Replaces It

```java
@Test
void aWrongKey() {
    given().baseUri(api.baseUri()).port(api.port())
        .filter(new CorrelationIdFilter())
        .auth().preemptive().basic("not", "an-api-key")          // another scheme, so no X-API-Key at all
    .when()
        .get("/api/v1/partner/accounts")
    .then()
        .statusCode(401);
}
```

A request has one authentication scheme. Set Basic, and the API key isn't sent. The same holds when the API key comes from a specification: `given(partnerApi()).auth().preemptive().basic(..)` sends `Authorization: Basic ..` and no `X-API-Key`.

> **The code above, ready to run**
>
> All four tests in [`CustomAuthenticationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_04/CustomAuthenticationTest.java) pass with `mvn test -pl module-08 -Dtest=CustomAuthenticationTest`. Add `.log().headers()` before `when()` to see which header each scheme sends.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 8 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/README.md)

## As the Global Default

```java
@Test
void asTheDefaultForEveryRequest() {
    RestAssured.authentication = apiKey(SignedPaymentsApi.PARTNER_API_KEY);
    try {
        given().baseUri(api.baseUri()).port(api.port())
            .filter(new CorrelationIdFilter())
        .when()
            .get("/api/v1/partner/accounts")
        .then()
            .statusCode(200);
    } finally {
        RestAssured.reset();
    }
}
```

`RestAssured.authentication` sets the scheme every request starts with. It is global state, so the test resets it in `finally`, as with the global filters in Section 1. A specification is usually the better place.

## When to Write a Scheme

1. **An API key or token in a custom header**, as here.
2. **A token that must be fetched or refreshed**, where the scheme hides the details from the tests.
3. **Not for request signing.** A signature covers the final request, including headers that filters add later. That is a job for an `OrderedFilter` that runs last, as in [Section 3](module-08-section-03-rest-assured-signing-requests.md).

## Common Pitfalls

1. **An API key as a header in a shared specification.** Tests can't remove it, so there is no clean way to test a missing key. Use a scheme.
2. **Expecting two schemes to combine.** A request has one. Setting Basic drops the API key, and the other way round.
3. **Implementing the old `AuthenticationScheme`.** It works with Apache HttpClient 4 types that REST Assured 7 removes. Use `ContextAuthenticationScheme`.
4. **A global default without `reset()`.** Every later test, in every class, sends the key. Reset in `finally`, or use a specification.
5. **Keys in logs.** `X-API-Key` is a secret like any other. Blacklist it, as [Module 5](module-05-section-04-rest-assured-secrets-in-logs.md) shows.

## Wrapping Up

`ContextAuthenticationScheme`, new in REST Assured 6.3, is a functional interface for custom authentication: a lambda that sets headers through an `AuthenticationContext`, with no Apache HttpClient 4 types. Use it with `setAuth(..)` in a specification, `auth()` on a request, or `RestAssured.authentication` globally. Unlike a header, a scheme is replaced when a request sets another one and removed by `auth().none()`, which makes negative tests as easy as positive ones.
