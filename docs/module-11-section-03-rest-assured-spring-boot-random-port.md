# Testing Spring Boot on a Random Port With REST Assured: @SpringBootTest and Real Tokens

Testing Spring Boot on a Random Port With REST Assured: Start the whole Spring Boot application on a free port with @SpringBootTest(webEnvironment = RANDOM_PORT), test it over real HTTP with plain REST Assured, and send real signed JWTs so signature, expiry, audience and scope are all checked.

The web slice in [Section 2](module-11-section-02-rest-assured-webmvctest-spring-security.md) tested the security rules with a stand-in for the token. That leaves a gap: is the token itself checked? Does the service reject a token that has expired, or one issued for another API? And does the application behave the same with a real servlet container, real HTTP headers and real error pages as it does under `MockMvc`? Only one kind of test answers that: the whole application, on a real port, with real tokens.

This post follows [Section 3 of Module 11](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-11) of the REST Assured course. It starts the Spring Boot application on a random port and tests it with plain REST Assured, exactly as earlier modules tested their APIs. The examples are in [`RandomPortServerTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_03/RandomPortServerTest.java) and [`TestTokens`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/support/TestTokens.java).

## What is a Random-Port Test?

`@SpringBootTest(webEnvironment = RANDOM_PORT)` starts the **whole** application, with an embedded Tomcat on a free port:

1. **Every bean** is created, as in production: controllers, services, security, error handling.
2. **Requests go over the network**, through the servlet container and every filter, converter and error handler.
3. **`@LocalServerPort`** injects the port that was chosen. In Spring Boot 4, it lives in `org.springframework.boot.test.web.server`.

The test uses plain REST Assured, `io.restassured.RestAssured.given`, not `RestAssuredMockMvc`. From REST Assured's point of view, this is just an HTTP server on `localhost`.

## Real Tokens for Tests

The service verifies RS256 JWTs with a public key from `src/main/resources/jwt`, and requires the audience `accounts-api`. The matching private key, for tests only, is in `src/test/resources/jwt`. `TestTokens` uses it to mint real signed tokens, with the JDK only:

```java
public static String withScopes(String... scopes) {
    return token(List.of(scopes), AUDIENCE, Duration.ofMinutes(5));
}

public static String token(List<String> scopes, String audience, Duration lifetime) {
    Instant now = Instant.now();
    String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    String claims = """
        {"iss":"https://auth.example.com","sub":"tpp-client","aud":"%s","scope":"%s","iat":%d,"exp":%d}"""
        .formatted(audience, String.join(" ", scopes), now.getEpochSecond(), now.plus(lifetime).getEpochSecond());
    String signingInput = encode(header) + "." + encode(claims);
    ...
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(KEY);
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    return signingInput + "." + BASE64URL.encodeToString(signer.sign());
}
```

- **`withScopes(..)`** gives a valid token with the scopes the test needs.
- **`token(scopes, audience, lifetime)`** can produce exactly the invalid tokens the negative tests need: a negative lifetime for an expired one, another audience for a misdirected one.

In a real set-up, only the authorization server holds the private key. [Module 12](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-12) gets real tokens from a real Keycloak.

## The Test Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RandomPortServerTest {

    @LocalServerPort
    private int port;

    private RequestSpecification accounts() {
        return given().baseUri("http://localhost").port(port).basePath("/api/v1/accounts");
    }
    ...
}
```

Spring starts the application once and reuses it for every test in the class, and for other classes with the same configuration, through its test context cache.

## Happy Paths

### Step 1: A Reader Can List Accounts

```java
@Test
void aReaderCanListAccounts() {
    accounts()
        .auth().oauth2(TestTokens.withScopes("accounts:read"))
    .when()
        .get()
    .then()
        .statusCode(200)
        .body("id", hasItems("acc-1001", "acc-1002"));
}
```

`auth().oauth2(..)` sends `Authorization: Bearer <token>`, as in [Module 7](module-07-section-02-rest-assured-oauth2-bearer-tokens.md). The base path is the whole collection, so `get()` needs no path.

The check uses `hasItems(..)`, not `contains(..)`. The application is shared by all tests in the class, and another test opens an account, so the list may have more than two.

### Step 2: A Writer Opens an Account, a Reader Reads It

```java
@Test
void aWriterCanOpenAnAccountAndAReaderCanReadIt() {
    String location = accounts()
        .auth().oauth2(TestTokens.withScopes("accounts:write"))
        .contentType(ContentType.JSON)
        .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Lotte Laan\", \"currency\": \"EUR\"}")
    .when()
        .post()
    .then()
        .statusCode(201)
        .extract().header("Location");

    given().baseUri("http://localhost").port(port)
        .auth().oauth2(TestTokens.withScopes("accounts:read"))
    .when()
        .get(location)
    .then()
        .statusCode(200)
        .body("owner", equalTo("Lotte Laan"));
}
```

The `Location` is an absolute path, so the second request is built without the base path, as in [Module 4](module-04-section-05-rest-assured-chaining-requests.md). Two tokens with different scopes act as two different clients.

> **The code above, ready to run**
>
> All six tests in [`RandomPortServerTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_03/RandomPortServerTest.java) pass with `mvn test -pl module-11 -Dtest=RandomPortServerTest`. The first test waits for the application to start; the others reuse it.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 11 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/README.md)

## Token Validation

These are the tests a web slice can't do, because `jwt()` skips validation.

### An Expired Token

```java
@Test
void anExpiredToken() {
    accounts()
        .auth().oauth2(TestTokens.token(List.of("accounts:read"), TestTokens.AUDIENCE, Duration.ofMinutes(-5)))
    .when()
        .get()
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", containsString("invalid_token"));
}
```

### A Token for Another Audience

```java
@Test
void aTokenForAnotherAudience() {
    accounts()
        .auth().oauth2(TestTokens.token(List.of("accounts:read"), "payments-api", Duration.ofMinutes(5)))
    .when()
        .get()
    .then()
        .statusCode(401);
}
```

The token is correctly signed and not expired, but it was issued for `payments-api`. The audience check is a custom validator in `SecurityConfig`. This test is the only one in the module that proves it is wired in.

### The Wrong Scope

```java
@Test
void theWrongScope() {
    accounts()
        .auth().oauth2(TestTokens.withScopes("accounts:write"))
    .when()
        .get("/{id}", "acc-1001")
    .then()
        .statusCode(403)
        .header("WWW-Authenticate", containsString("insufficient_scope"));
}
```

## Problem Details Over HTTP

```java
@Test
void anUnknownAccountIsAProblem() {
    accounts()
        .auth().oauth2(TestTokens.withScopes("accounts:read"))
    .when()
        .get("/{id}", "acc-9999")
    .then()
        .statusCode(404)
        .contentType("application/problem+json")
        .body("status", equalTo(404))
        .body("detail", equalTo("Account acc-9999 does not exist"));
}
```

The error travels through the servlet container's error handling, as it would in production, and still arrives as an RFC 9457 problem.

## Choosing a Style

| Style | Starts | Tests | Use for |
| --- | --- | --- | --- |
| [Standalone MockMvc](module-11-section-01-rest-assured-mockmvc-standalone.md) | The controller | Routing, JSON, validation | Many quick controller tests |
| [Web slice](module-11-section-02-rest-assured-webmvctest-spring-security.md) | The web layer and security | Authorisation rules, error format | The rules, without tokens |
| Random port | The whole application | Everything, over real HTTP | Token validation, end-to-end behaviour |

A good suite uses all three: many fast tests at the bottom, a few complete ones at the top.

## Common Pitfalls

1. **`RestAssuredMockMvc` against a random port.** It never opens a socket. Use plain `RestAssured.given` with the injected port.
2. **The wrong `@LocalServerPort`.** In Spring Boot 4 it is `org.springframework.boot.test.web.server.LocalServerPort`.
3. **Exact counts on shared state.** The application lives for the whole class, and other tests change its data. Use `hasItems(..)`, or check data the test created itself.
4. **A `Location` with the base path still set.** The request goes to `/api/v1/accounts/api/v1/accounts/..`. Build that request without the base path.
5. **Only happy-path tokens.** A service that ignores expiry or audience passes every test with a valid token. Send expired and misdirected tokens too.

## Wrapping Up

`@SpringBootTest(webEnvironment = RANDOM_PORT)` starts the whole Spring Boot application on a free port, and `@LocalServerPort` tells the test which. Plain REST Assured then tests it over real HTTP, through every filter, converter and error handler. Real signed tokens from a test key let the tests check what a web slice can't: signature, expiry and audience, as well as scopes. It is the slowest style and the closest to production, so use it for the end-to-end and security tests, and leave the many small checks to MockMvc.
