package com.example.module_07.section_02;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.auth.BankAuthApi;

import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 7.2 – getting an OAuth 2.0 access token with the client credentials grant, and using it.
 * <p>
 * The token request is a form post to the token endpoint. The client authenticates either with HTTP Basic
 * ({@code client_secret_basic}, preferred by RFC 6749) or with form parameters ({@code client_secret_post}). The token is
 * then sent as {@code Authorization: Bearer <token>}, which {@code auth().oauth2(token)} does.
 * <p>
 * Getting a token for every test is slow and, against a real authorization server, may hit rate limits. This class
 * gets one in {@code @BeforeAll} and reuses it; it is valid for five minutes.
 */
class BearerTokenTest {

    private static final String JWT = "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+";

    private static BankAuthApi api;
    private static String accountsToken;

    @BeforeAll
    static void startApiAndGetAToken() {
        api = BankAuthApi.start();
        accountsToken = clientCredentialsToken("tpp-accounts", "accounts-secret", "accounts:read");
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification bank() {
        return given().baseUri(api.baseUri()).port(api.port());
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

    @Test
    void orAsAPlainHeader() {
        bank()
            .header("Authorization", "Bearer " + accountsToken)
        .when()
            .get("/api/v1/accounts")
        .then()
            .statusCode(200);
    }

    @Test
    void theTokenIsAJwtWhoseClaimsCanBeRead() {
        // A JWT access token (RFC 9068) is three base64url parts: header, claims, signature. Reading the claims in a
        // test checks what the authorization server put in; it is not a validation of the token.
        String claimsJson = new String(Base64.getUrlDecoder().decode(accountsToken.split("\\.")[1]), StandardCharsets.UTF_8);
        JsonPath claims = JsonPath.from(claimsJson);

        assertEquals(api.issuer(), claims.getString("iss"));
        assertEquals(BankAuthApi.AUDIENCE, claims.getString("aud"));
        assertEquals("tpp-accounts", claims.getString("client_id"));
        assertEquals("accounts:read", claims.getString("scope"));
    }
}
