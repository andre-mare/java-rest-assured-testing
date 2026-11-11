package com.example.module_12.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.example.resource.ProtectedAccountsApi;

import dasniko.testcontainers.keycloak.KeycloakContainer;

import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Module 12.1 – real access tokens from a real authorization server: Keycloak in Docker.
 * <p>
 * Modules 7 and 11 minted tokens themselves. Here Keycloak issues them, from a realm imported at start-up
 * ({@code src/test/resources/keycloak/open-banking-realm.json}): two confidential clients with the client credentials
 * grant, two client scopes and an audience mapper. The API under test trusts Keycloak's published keys, so the whole
 * chain is exercised: discovery, token endpoint, signing keys, scopes and audience. This is the same set-up as the
 * CIBA lab, with REST Assured on both sides.
 * <p>
 * {@code @Testcontainers(disabledWithoutDocker = true)} skips the class, rather than failing it, on a machine without
 * Docker. The first run downloads the Keycloak image, which takes a while.
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("keycloak")
@Tag("security")
class KeycloakTokensTest {

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
        .withRealmImportFile("/keycloak/open-banking-realm.json");

    private static String issuer;
    private static String tokenEndpoint;
    private static ProtectedAccountsApi api;

    @BeforeAll
    static void discoverKeycloakAndStartTheApi() {
        issuer = KEYCLOAK.getAuthServerUrl() + "/realms/open-banking";

        // OpenID Connect discovery: ask the server for its endpoints instead of hard-coding them.
        JsonPath discovery = given()
            .when().get(issuer + "/.well-known/openid-configuration")
            .then().statusCode(200)
            .body("issuer", equalTo(issuer))
            .body("grant_types_supported", hasItem("client_credentials"))
            .extract().jsonPath();
        tokenEndpoint = discovery.getString("token_endpoint");

        api = ProtectedAccountsApi.start(issuer, discovery.getString("jwks_uri"));
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

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

    private static RequestSpecification api() {
        return given().baseUri(api.baseUri()).port(api.port());
    }

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
}
