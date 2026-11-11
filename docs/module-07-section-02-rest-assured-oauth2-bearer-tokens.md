# OAuth 2.0 Bearer Tokens in REST Assured: The Client Credentials Grant and auth().oauth2(..)

OAuth 2.0 Bearer Tokens in REST Assured: Get an access token with the client credentials grant, authenticate the client with client_secret_basic or client_secret_post, send the token with auth().oauth2(..), and read the claims of a JWT access token.

Most modern APIs don't take a password on every request. A client first gets an access token from an authorization server, then sends that token with each request. In open banking, a third-party provider gets a token for its own client id, with the scopes it is allowed, and calls the bank's API with it. To test such an API, a test has to do the same: get a token, then use it.

This post follows [Section 2 of Module 7](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07) of the REST Assured course. It gets tokens with the OAuth 2.0 client credentials grant and uses them on the protected API. The examples are in [`BearerTokenTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_02/BearerTokenTest.java).

## What is the Client Credentials Grant?

The client credentials grant ([RFC 6749, section 4.4](https://www.rfc-editor.org/rfc/rfc6749#section-4.4)) is OAuth 2.0 for a client acting on its own behalf, without a user:

1. **The client posts a form** to the token endpoint, with `grant_type=client_credentials` and, optionally, the `scope` it wants.
2. **The client authenticates itself**, either with HTTP Basic (`client_secret_basic`, which RFC 6749 prefers) or with `client_id` and `client_secret` form parameters (`client_secret_post`).
3. **The server answers** with an access token, its type, its lifetime and the scopes it grants.
4. **The client sends the token** as `Authorization: Bearer <token>` on every API request ([RFC 6750](https://www.rfc-editor.org/rfc/rfc6750)).

## The Authorization Server Under Test

The bank's authorization server knows two clients:

| Client | Secret | Allowed scopes |
| --- | --- | --- |
| `tpp-accounts` | `accounts-secret` | `accounts:read` |
| `tpp-payments` | `payments-secret` | `accounts:read`, `payments:write` |

`POST /oauth2/token` answers with:

```json
{"access_token":"eyJ...","token_type":"Bearer","expires_in":300,"scope":"accounts:read"}
```

The access token is a JWT signed with RS256, valid for five minutes. `GET /api/v1/accounts` needs a token with `accounts:read` and the audience `accounts-api`, and answers:

```json
{"client":"tpp-accounts","accounts":[{"id":"acc-1001","iban":"NL91ABNA0417164300"}]}
```

## Getting a Token

### Step 1: A Token Helper, Called Once per Class

```java
private static String accountsToken;

@BeforeAll
static void startApiAndGetAToken() {
    api = BankAuthApi.start();
    accountsToken = clientCredentialsToken("tpp-accounts", "accounts-secret", "accounts:read");
}

/** The client credentials grant with client_secret_basic. */
static String clientCredentialsToken(String clientId, String secret, String scope) {
    return bank()
        .auth().preemptive().basic(clientId, secret)
        .formParam("grant_type", "client_credentials")
        .formParam("scope", scope)
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(200)
        .extract().path("access_token");
}
```

- **`auth().preemptive().basic(..)`** authenticates the client with `client_secret_basic`, preemptively, as [Section 1](module-07-section-01-rest-assured-basic-authentication.md) recommends.
- **`formParam(..)`** makes the request a form post, as [Module 2](module-02-section-02-rest-assured-query-and-form-parameters.md) showed.
- **`@BeforeAll`** gets the token once for the whole class. Getting a token for every test is slow and, against a real authorization server, may hit rate limits. The token lives five minutes, which is plenty for one class.

### Step 2: Check the Token Response

```java
@Test
void theTokenResponse() {
    bank()
        .auth().preemptive().basic("tpp-accounts", "accounts-secret")
        .formParam("grant_type", "client_credentials")
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(200)
        .header("Cache-Control", "no-store")                     // tokens must not be cached (RFC 6749 5.1)
        .body("token_type", equalTo("Bearer"))
        .body("expires_in", greaterThan(0))
        .body("scope", equalTo("accounts:read"))                 // no scope asked: all the client may have
        .body("access_token", matchesPattern(JWT));
}
```

The token endpoint has a contract of its own. `Cache-Control: no-store` is required by RFC 6749, so a proxy never keeps a copy of a token. `JWT` is a pattern for three base64url parts separated by dots.

### Step 3: client_secret_post Instead of Basic

```java
@Test
void clientSecretPostInsteadOfBasic() {
    bank()
        .formParam("grant_type", "client_credentials")
        .formParam("client_id", "tpp-payments")
        .formParam("client_secret", "payments-secret")
        .formParam("scope", "accounts:read payments:write")
    .when()
        .post("/oauth2/token")
    .then()
        .statusCode(200)
        .body("scope", equalTo("accounts:read payments:write"));
}
```

The credentials move from the header to the form. Several scopes are separated by spaces, as RFC 6749 defines.

## Using the Token

### Step 4: auth().oauth2(..)

```java
@Test
void useTheTokenWithOauth2() {
    bank()
        .auth().oauth2(accountsToken)
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(200)
        .body("client", equalTo("tpp-accounts"));
}
```

`auth().oauth2(token)` sends `Authorization: Bearer <token>`.

### Step 5: Or a Plain Header

```java
@Test
void orAsAPlainHeader() {
    bank()
        .header("Authorization", "Bearer " + accountsToken)
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(200);
}
```

Both send the same header. `auth().oauth2(..)` says what it means. The plain header is useful when the scheme isn't `Bearer`, or when a specification already builds headers.

> **The code above, ready to run**
>
> All five tests in [`BearerTokenTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_02/BearerTokenTest.java) pass with `mvn test -pl module-07 -Dtest=BearerTokenTest`. Print `accountsToken` and paste it into a JWT decoder to see its three parts.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

## Reading the Claims of a JWT

A JWT access token ([RFC 9068](https://www.rfc-editor.org/rfc/rfc9068)) is three base64url-encoded parts: a header, the claims and a signature. The header of this token is `{"alg":"RS256","typ":"at+jwt","kid":"key-e102c648"}`, and the claims are:

```json
{"iss":"http://localhost:59866","sub":"tpp-accounts","client_id":"tpp-accounts","aud":"accounts-api",
 "scope":"accounts:read","iat":1794378951,"exp":1794379251,"jti":"2caa0135-7271-44b9-a91d-35f8545b181c"}
```

```java
@Test
void theTokenIsAJwtWhoseClaimsCanBeRead() {
    String claimsJson = new String(Base64.getUrlDecoder().decode(accountsToken.split("\\.")[1]), StandardCharsets.UTF_8);
    JsonPath claims = JsonPath.from(claimsJson);

    assertEquals(api.issuer(), claims.getString("iss"));
    assertEquals(BankAuthApi.AUDIENCE, claims.getString("aud"));
    assertEquals("tpp-accounts", claims.getString("client_id"));
    assertEquals("accounts:read", claims.getString("scope"));
}
```

The test decodes the middle part and reads it with `JsonPath.from(..)` from [Module 4](module-04-section-02-rest-assured-jsonpath-and-xmlpath.md). This checks what the authorization server put in the token. It is **not** a validation of the token: it ignores the signature. Validating tokens is the resource server's job, and [Section 3](module-07-section-03-rest-assured-negative-security-tests.md) tests that it does it.

## Common Pitfalls

1. **A new token for every test.** It is slow, and real authorization servers rate-limit token requests. Get one per class, or cache it until it nearly expires.
2. **Challenged Basic for the client.** The token endpoint may not challenge. Use `auth().preemptive().basic(..)`.
3. **Commas between scopes.** OAuth 2.0 separates scopes with spaces: `"accounts:read payments:write"`.
4. **Treating decoded claims as validated.** Anyone can decode a JWT, and anyone can forge one without the signature. Decode claims to check what was issued, never to decide whether a token is valid.
5. **Tokens in logs.** A logged bearer token can be used by whoever reads the log, until it expires. Blacklist `Authorization`, as [Module 5](module-05-section-04-rest-assured-secrets-in-logs.md) shows.

## Wrapping Up

The client credentials grant gets an access token for a client: a form post to the token endpoint with `grant_type=client_credentials`, authenticated with `client_secret_basic` or `client_secret_post`. Check the token response's contract, including `Cache-Control: no-store`. Send the token with `auth().oauth2(..)`, which writes `Authorization: Bearer <token>`. Get it once per class in `@BeforeAll`. Decode a JWT's claims to check what was issued, and leave validation to the server.
