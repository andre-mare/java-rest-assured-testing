package com.example.module_07.section_03;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.example.auth.BankAuthApi;

import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Module 7.3 – what must happen when credentials are missing, wrong or not enough.
 * <p>
 * Most security bugs are in the paths a happy-path test never takes. These tests check the error responses that the
 * standards define, so a client can tell "log in again" from "ask for more access":
 * <ul>
 *   <li>Token endpoint (RFC 6749 section 5.2): {@code invalid_client}, {@code unsupported_grant_type},
 *       {@code invalid_scope}.</li>
 *   <li>Resource server (RFC 6750 section 3): 401 with {@code WWW-Authenticate: Bearer realm=".."} and no error code
 *       for a missing token, 401 with {@code error="invalid_token"} for a bad one, 403 with
 *       {@code error="insufficient_scope"} for a missing scope.</li>
 * </ul>
 */
class NegativeSecurityTest {

    private static final String REALM = "Bearer realm=\"accounts-api\"";

    private static BankAuthApi api;

    @BeforeAll
    static void startApi() {
        api = BankAuthApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification bank() {
        return given().baseUri(api.baseUri()).port(api.port());
    }

    @Nested
    class TokenEndpoint {

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
    }

    @Nested
    class ResourceServer {

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

        @Test
        void aTamperedToken() {
            // Change the scope in the claims and keep the old signature: the signature no longer matches.
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

        @Test
        void theRightScopeOpensTheDoor() {
            String payments = api.mintToken("tpp-payments", List.of("payments:write"), BankAuthApi.AUDIENCE, Duration.ofMinutes(5));

            bank().auth().oauth2(payments).when().post("/api/v1/payments").then().statusCode(201);
        }
    }
}
