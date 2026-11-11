# Real OAuth 2.0 Tokens From Keycloak: REST Assured and Testcontainers

Real OAuth 2.0 Tokens From Keycloak: Run Keycloak in Docker with Testcontainers, discover its endpoints with OpenID Connect discovery, get real access tokens with the client credentials grant, and test an API that verifies them against Keycloak's published keys.

[Module 7](module-07-section-03-rest-assured-negative-security-tests.md) and [Module 11](module-11-section-03-rest-assured-spring-boot-random-port.md) minted their own tokens, with keys the tests controlled. That tests the API's rules well, but it leaves one question open: does the API work with the tokens a real authorization server issues? Real servers have their own ideas: which claim holds the client, whether the audience is a string or a list, how often signing keys rotate. The only way to find out is to run a real one. With Docker and Testcontainers, that is a few lines in a test class.

This post is the first in the series for [Module 12 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-12), which covers running REST Assured against real infrastructure, and making a large suite fast and organised. It gets real tokens from Keycloak and uses them on a protected API. The examples are in [`KeycloakTokensTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_01/KeycloakTokensTest.java).

## What are Testcontainers and Keycloak?

1. **[Testcontainers](https://testcontainers.com/)** starts Docker containers from a test, waits until they are ready, and removes them afterwards. Its JUnit integration does all of that with two annotations.
2. **[Keycloak](https://www.keycloak.org/)** is an open-source identity and access management server, and a complete OAuth 2.0 and OpenID Connect authorization server. The module uses Keycloak 26.8 through the `KeycloakContainer` from the `testcontainers-keycloak` library.
3. **A realm** is Keycloak's unit of configuration: clients, scopes and users. The test imports one at start-up from a JSON file.

The chain under test:

```text
test --REST Assured--> Keycloak (token endpoint)            client credentials -> access token
test --REST Assured--> ProtectedAccountsApi                 Authorization: Bearer <token>
                       ProtectedAccountsApi --> Keycloak    fetch signing keys (JWKS)
```

## The Set-Up Under Test

The realm [`open-banking-realm.json`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/resources/keycloak/open-banking-realm.json) defines:

| Client | Secret | May get scopes |
| --- | --- | --- |
| `tpp-accounts` | `accounts-secret` | `accounts:read` |
| `tpp-payments` | `payments-secret` | `accounts:read`, `payments:write` |

Both clients use the client credentials grant, and an audience mapper adds `accounts-api` to their tokens.

`ProtectedAccountsApi` is a resource server: `GET /api/v1/accounts` needs `accounts:read`, and `POST /api/v1/payments` needs `payments:write`. Its `JwksTokenVerifier` checks the RS256 signature with the keys Keycloak publishes, matched by the token's key id (`kid`), plus the issuer, audience and expiry. When it sees an unknown key id, it reloads the keys, which is how a resource server survives key rotation.

## Starting Keycloak

```java
@Testcontainers(disabledWithoutDocker = true)
@Tag("keycloak")
@Tag("security")
class KeycloakTokensTest {

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
        .withRealmImportFile("/keycloak/open-banking-realm.json");
    ...
}
```

- **`@Testcontainers`** finds the `@Container` fields and manages them.
- **A `static` container** is started once for the class and stopped after it. An instance field would start a new Keycloak for every test.
- **`disabledWithoutDocker = true`** skips the class on a machine without Docker, instead of failing it. The rest of the module still runs.
- **`withRealmImportFile(..)`** imports the realm from the test classpath when Keycloak starts.
- **The tags** let a build select or exclude these tests, as [Section 3](module-12-section-03-rest-assured-tags-and-maven-profiles.md) shows.

The first run downloads the Keycloak image, which takes a while. After that, the class takes about eleven seconds, most of it Keycloak's start-up.

## Discovering the Endpoints

```java
@BeforeAll
static void discoverKeycloakAndStartTheApi() {
    issuer = KEYCLOAK.getAuthServerUrl() + "/realms/open-banking";

    JsonPath discovery = given()
        .when().get(issuer + "/.well-known/openid-configuration")
        .then().statusCode(200)
        .body("issuer", equalTo(issuer))
        .body("grant_types_supported", hasItem("client_credentials"))
        .extract().jsonPath();

    tokenEndpoint = discovery.getString("token_endpoint");
    api = ProtectedAccountsApi.start(issuer, discovery.getString("jwks_uri"));
}
```

OpenID Connect discovery publishes the server's configuration at `/.well-known/openid-configuration`. The test asks for it instead of hard-coding URLs:

```text
issuer=http://localhost:50248/realms/open-banking
token_endpoint=http://localhost:50248/realms/open-banking/protocol/openid-connect/token
jwks_uri=http://localhost:50248/realms/open-banking/protocol/openid-connect/certs
```

The port is different on every run, because Testcontainers maps Keycloak's port to a free one. Discovery handles that, and the same code would work against any compliant server.

The API under test is started with the issuer and the JWKS URI, so it trusts exactly this Keycloak.

## Getting Real Tokens

```java
private static String token(String clientId, String secret, String scope) {
    return given()
        .auth().preemptive().basic(clientId, secret)
        .formParam("grant_type", "client_credentials")
        .formParam("scope", scope)
    .when()
        .post(tokenEndpoint)
    .then()
        .statusCode(200)
        .body("token_type", equalTo("Bearer"))
        .extract().path("access_token");
}
```

This is the same client credentials request as in [Module 7](module-07-section-02-rest-assured-oauth2-bearer-tokens.md), now sent to a real server. Keycloak answers:

```json
{"access_token":"eyJ...","expires_in":300,"refresh_expires_in":0,"token_type":"Bearer","not-before-policy":0,"scope":"payments:write accounts:read"}
```

## Using the Tokens

### Step 1: An Accounts Token Reads Accounts

```java
@Test
void anAccountsTokenReadsAccounts() {
    api()
        .auth().oauth2(token("tpp-accounts", "accounts-secret", "accounts:read"))
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(200)
        .body("client", equalTo("tpp-accounts"));
}
```

The API verified Keycloak's signature with the key it fetched from the JWKS URI, and read the client from the token.

### Step 2: A Payments Token Initiates Payments

```java
@Test
void aPaymentsTokenInitiatesPayments() {
    api()
        .auth().oauth2(token("tpp-payments", "payments-secret", "payments:write"))
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(201)
        .body("paymentId", notNullValue());
}
```

### Step 3: An Accounts Token Can't Initiate Payments

```java
@Test
void anAccountsTokenCannotInitiatePayments() {
    api()
        .auth().oauth2(token("tpp-accounts", "accounts-secret", "accounts:read"))
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(403)
        .header("WWW-Authenticate", containsString("insufficient_scope"));
}
```

The full header is `Bearer realm="accounts-api", error="insufficient_scope", scope="payments:write"`, as RFC 6750 requires.

### Step 4: Keycloak Rejects a Wrong Secret

```java
@Test
void keycloakRejectsAWrongSecret() {
    given()
        .auth().preemptive().basic("tpp-accounts", "not-the-secret")
        .formParam("grant_type", "client_credentials")
    .when()
        .post(tokenEndpoint)
    .then()
        .statusCode(401);
}
```

Keycloak answers `{"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}`. Note the error code: RFC 6749 names `invalid_client` for a failed client authentication, which is what the test API in Module 7 returned. This is exactly the kind of difference a real server shows, and why the test checks the status, not the error code.

> **The code above, ready to run**
>
> All five tests in [`KeycloakTokensTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_01/KeycloakTokensTest.java) pass with `mvn test -pl module-12 -Dtest=KeycloakTokensTest` when Docker is running. Without Docker, they are reported as skipped.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 12 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/README.md)

## What Keycloak Puts in a Token

```java
@Test
void theTokenHasTheExpectedClaims() {
    String token = token("tpp-payments", "payments-secret", "accounts:read payments:write");
    JsonPath claims = JsonPath.from(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8));

    assertThat(claims.getString("iss"), equalTo(issuer));
    assertThat(claims.getString("azp"), equalTo("tpp-payments"));            // the client the token was issued to
    assertThat(claims.getString("scope"), containsString("payments:write"));
    Object aud = claims.get("aud");                                           // a string, or a list of strings
    List<Object> audiences = aud instanceof List<?> list ? new ArrayList<>(list) : List.of(aud);
    assertThat(audiences, hasItem("accounts-api"));
}
```

A real Keycloak token's claims:

```json
{"exp":1794382873,"iat":1794382573,"jti":"trrtcc:40a5a13a-5416-4c0c-3810-0d6d3fbc8a70",
 "iss":"http://localhost:50248/realms/open-banking","aud":"accounts-api",
 "sub":"6811a718-230d-454e-8e4e-2e3f30e3004a","typ":"Bearer","azp":"tpp-payments",
 "scope":"payments:write accounts:read"}
```

Compare them with the tokens earlier modules minted:

- **The client is in `azp`** (authorized party), not in `client_id`. `sub` is the id of the client's service account, a UUID.
- **`aud` is a string here**, but becomes a list as soon as a token has more than one audience. The test, like the API's verifier, accepts both.
- **The scopes come in Keycloak's order**, not the order asked for, so the test uses `containsString(..)`.

These details are why a test against the real server is worth its eleven seconds.

## Common Pitfalls

1. **An instance `@Container` field.** It starts a container per test. Make it `static`.
2. **Failing without Docker.** A developer without Docker gets a red build for reasons that have nothing to do with their change. Use `disabledWithoutDocker = true`, and tag the class so it can be excluded.
3. **Hard-coded URLs and ports.** The port changes every run. Use discovery, or the container's `getAuthServerUrl()`.
4. **Assuming every server uses the same claims.** Keycloak puts the client in `azp` and may send `aud` as a string or a list. Read the real tokens, and make the API accept the real shape.
5. **Checking exact error codes from a real server.** Keycloak's `unauthorized_client` differs from RFC 6749's `invalid_client`. Check what your API depends on, and know where the server deviates.

## Wrapping Up

Testcontainers starts Keycloak in Docker from a test class: a `static` `@Container` for one Keycloak per class, a realm imported at start-up, and `disabledWithoutDocker = true` so machines without Docker skip the class. OpenID Connect discovery provides the token endpoint and the JWKS URI, so nothing is hard-coded. REST Assured gets real tokens with the client credentials grant and sends them to an API that verifies them against Keycloak's published keys. A real server shows the details self-minted tokens hide: `azp`, an `aud` that may be a string or a list, and error codes that don't always follow the RFC.
