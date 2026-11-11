# Negative Security Tests in REST Assured: Missing, Invalid, Expired and Insufficient Tokens

Negative Security Tests in REST Assured: Test what must happen when credentials are missing, wrong or not enough, covering the token endpoint errors of RFC 6749, the WWW-Authenticate responses of RFC 6750, expired, wrong-audience and tampered tokens, and a missing scope.

A happy-path test proves that the right token opens the door. It doesn't prove that the wrong one keeps it shut. And yet that is where most security bugs live: an endpoint that someone forgot to protect, a check that ignores the expiry time, a resource server that accepts tokens meant for another API, a signature that is never verified. None of these break a single happy-path test. Only tests that send the wrong thing on purpose find them.

This post follows [Section 3 of Module 7](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07) of the REST Assured course. It tests every way a request can fail authentication or authorisation, and the exact error responses the OAuth 2.0 standards require. The examples are in [`NegativeSecurityTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_03/NegativeSecurityTest.java).

## What Must Happen When Credentials Are Wrong?

The OAuth 2.0 standards define the errors, so a client can tell "get a new token" from "ask for more access":

| Situation | Status | `WWW-Authenticate` or body |
| --- | --- | --- |
| Token endpoint: wrong client secret | `401` | `{"error":"invalid_client"}` and a `Basic` challenge |
| Token endpoint: unsupported grant | `400` | `{"error":"unsupported_grant_type"}` |
| Token endpoint: scope the client may not have | `400` | `{"error":"invalid_scope"}` |
| API: no token | `401` | `Bearer realm=".."`, **no** error code |
| API: expired, wrong audience or tampered token | `401` | `Bearer realm="..", error="invalid_token", error_description=".."` |
| API: valid token without the scope | `403` | `Bearer realm="..", error="insufficient_scope", scope=".."` |

The first three come from [RFC 6749, section 5.2](https://www.rfc-editor.org/rfc/rfc6749#section-5.2), the last three from [RFC 6750, section 3](https://www.rfc-editor.org/rfc/rfc6750#section-3).

The difference between `401` and `403` matters: `401` means "I don't know who you are", so the client should get a new token. `403` means "I know who you are, but you may not do this", so a new token with the same scopes won't help.

## Minting Tokens for Tests

The resource server checks the token's signature, expiry, audience and scope. To test each check, the tests need tokens that fail exactly one of them. The test API has a helper for that:

```java
String expired = api.mintToken("tpp-accounts", List.of("accounts:read"), BankAuthApi.AUDIENCE, Duration.ofMinutes(-1));
```

`mintToken(client, scopes, audience, lifetime)` signs a token with the server's real key, so everything about it is valid except what the test changes. A negative lifetime gives a token that has already expired. Real authorization servers don't offer such a helper, but a test double or a test realm can, as Module 12 shows with Keycloak.

The test class groups its tests with JUnit's `@Nested`: one inner class for the token endpoint, one for the resource server.

## The Token Endpoint

### Step 1: A Wrong Client Secret

```java
@Test
void wrongClientSecret() {
    bank()
        .auth().preemptive().basic("tpp-accounts", "not-the-secret")
        .formParam("grant_type", "client_credentials")
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", startsWith("Basic"))
        .body("error", equalTo("invalid_client"));
}
```

The body is `{"error":"invalid_client","error_description":"Client authentication failed"}`. Note what it doesn't say: whether the client id or the secret was wrong. Telling them apart would help an attacker guess client ids.

### Step 2: An Unsupported Grant and a Scope the Client May Not Have

```java
@Test
void aGrantTypeTheServerDoesNotSupport() {
    bank()
        .auth().preemptive().basic("tpp-accounts", "accounts-secret")
        .formParam("grant_type", "password")                // the resource owner password grant
        .formParam("username", "anna")
        .formParam("password", "secret")
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(400)
        .body("error", equalTo("unsupported_grant_type"));
}

@Test
void aScopeTheClientMayNotHave() {
    bank()
        .auth().preemptive().basic("tpp-accounts", "accounts-secret")
        .formParam("grant_type", "client_credentials")
        .formParam("scope", "payments:write")
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(400)
        .body("error", equalTo("invalid_scope"));
}
```

The password grant is left out of OAuth 2.1 because the client sees the user's password. A server that refuses it, and a client that can't ask for more than it was given, are both security properties worth a test.

## The Resource Server

### Step 3: Every Endpoint Rejects a Missing Token

```java
private static final String REALM = "Bearer realm=\"accounts-api\"";

@ParameterizedTest(name = "{0} {1} needs a token")
@CsvSource({
    "GET,  /api/v1/accounts",
    "POST, /api/v1/payments",
})
void everyEndpointRejectsAMissingToken(Method method, String path) {
    bank()
    .when()
        .request(method, path)
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", equalTo(REALM));         // no error code when no token was sent
}
```

A parameterized test covers every endpoint with one method. JUnit converts `GET` and `POST` to REST Assured's `Method` enum, and `request(method, path)` from [Module 2](module-02-section-05-rest-assured-http-methods.md) sends it. Add a row for every new endpoint, and an endpoint that someone forgot to protect fails here.

RFC 6750 says that a request without a token gets no error code, only the realm. A client hasn't done anything wrong yet; it just hasn't authenticated.

### Step 4: An Expired Token

```java
@Test
void anExpiredToken() {
    String expired = api.mintToken("tpp-accounts", List.of("accounts:read"), BankAuthApi.AUDIENCE, Duration.ofMinutes(-1));

    bank()
        .auth().oauth2(expired)
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", allOf(startsWith(REALM), containsString("error=\"invalid_token\""),
                containsString("expired")));
}
```

The full header is `Bearer realm="accounts-api", error="invalid_token", error_description="The token has expired"`.

### Step 5: A Token for Another Audience

```java
@Test
void aTokenForAnotherAudience() {
    String forPayments = api.mintToken("tpp-accounts", List.of("accounts:read"), "payments-api", Duration.ofMinutes(5));

    bank()
        .auth().oauth2(forPayments)
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", containsString("another audience"));
}
```

The token is signed, current and has the right scope, but it was issued for `payments-api`. Without the audience check, a token stolen from one API would open every API that trusts the same authorization server.

### Step 6: A Tampered Token

```java
@Test
void aTamperedToken() {
    String token = api.mintToken("tpp-accounts", List.of("accounts:read"), BankAuthApi.AUDIENCE, Duration.ofMinutes(5));
    String[] parts = token.split("\\.");
    String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace("accounts:read", "payments:write");
    String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

    bank()
        .auth().oauth2(tampered)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", containsString("signature is invalid"));
}
```

The test grants itself a scope by editing the claims, and keeps the original signature. If the server only decoded the token, as [Section 2](module-07-section-02-rest-assured-oauth2-bearer-tokens.md) did to read it, this payment would go through. The server verifies the signature, and refuses.

> **The code above, ready to run**
>
> All ten tests in [`NegativeSecurityTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_03/NegativeSecurityTest.java) pass with `mvn test -pl module-07 -Dtest=NegativeSecurityTest`. The parameterized test shows up as one test per endpoint in the report.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

## Authorisation: The Scope

### A Valid Token Without the Scope

```java
@Test
void aValidTokenWithoutTheScope() {
    String readOnly = api.mintToken("tpp-accounts", List.of("accounts:read"), BankAuthApi.AUDIENCE, Duration.ofMinutes(5));

    bank()
        .auth().oauth2(readOnly)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(403)                                     // authenticated, but not allowed
        .header("WWW-Authenticate", allOf(containsString("error=\"insufficient_scope\""),
                containsString("scope=\"payments:write\"")));
}
```

The header names the scope that is needed: `Bearer realm="accounts-api", error="insufficient_scope", scope="payments:write"`. A client can use it to ask for the right access.

### The Right Scope Opens the Door

```java
@Test
void theRightScopeOpensTheDoor() {
    String payments = api.mintToken("tpp-payments", List.of("payments:write"), BankAuthApi.AUDIENCE, Duration.ofMinutes(5));
    bank().auth().oauth2(payments).when().post("/api/v1/payments").then().statusCode(201);
}
```

This positive test belongs with the negative ones. It proves that the `403` above was caused by the scope, and not by something else that would block every request.

## Common Pitfalls

1. **Testing only the status.** A `401` for an expired token and a `401` for a missing one are different situations. Check `WWW-Authenticate` too, so the test fails when the server refuses for the wrong reason.
2. **`401` where `403` is right.** A client that gets `401` for a missing scope will fetch a new token, get the same scopes, and loop. Test that a missing scope gives `403`.
3. **Forgetting new endpoints.** A parameterized "every endpoint needs a token" test only helps if every endpoint is in it. Add the row when you add the endpoint.
4. **Negative tests without a positive control.** If every request fails, every negative test passes. Include a test where the right token succeeds.
5. **Only testing the tokens the real server issues.** It never issues expired, tampered or wrong-audience tokens on purpose. Mint them, so each check in the resource server is tested on its own.

## Wrapping Up

Negative security tests check what happens when credentials are missing, wrong or not enough, which is where most security bugs are. At the token endpoint, RFC 6749 defines `invalid_client`, `unsupported_grant_type` and `invalid_scope`. At the resource server, RFC 6750 defines a bare `Bearer realm` for a missing token, `401` with `error="invalid_token"` for an expired, wrong-audience or tampered one, and `403` with `error="insufficient_scope"` for a missing scope. Mint tokens that fail one check each, cover every endpoint with a parameterized test, and keep a positive control.
