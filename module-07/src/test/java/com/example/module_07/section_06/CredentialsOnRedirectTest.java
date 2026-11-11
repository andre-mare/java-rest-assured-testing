package com.example.module_07.section_06;

import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;

import com.example.auth.BankAuthApi;

import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 7.6 – a token must not follow a redirect to another server.
 * <p>
 * {@code GET /api/v1/partner-redirect} answers 302 with a {@code Location} on a different port, so a different
 * origin. Since REST Assured 6.1, the {@code Authorization} and {@code Cookie} headers are dropped when a redirect
 * crosses to another origin (scheme, host or port), as browsers do. Before 6.1 they were sent along, leaking the
 * token to whatever the server redirected to. The partner endpoint reports what it received.
 */
class CredentialsOnRedirectTest {

    private static BankAuthApi api;

    @BeforeAll
    static void startApi() {
        api = BankAuthApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification withCredentials() {
        return given().baseUri(api.baseUri()).port(api.port())
            .auth().oauth2("a-secret-token")
            .cookie("SESSION", "a-secret-session");
    }

    @Test
    void credentialsAreDroppedOnACrossOriginRedirect() {
        withCredentials()
        .when()
            .get("/api/v1/partner-redirect")
        .then()
            .statusCode(200)
            .body("authorizationReceived", equalTo(false))
            .body("cookieReceived", equalTo(false));
    }

    @Test
    void theOldBehaviourCanBeTurnedBackOn() {
        // Only for a partner you trust with the credentials; the default is the safe choice.
        withCredentials()
            .config(config().redirect(redirectConfig().stripSensitiveHeadersOnCrossHostRedirect(false)))
        .when()
            .get("/api/v1/partner-redirect")
        .then()
            .statusCode(200)
            .body("authorizationReceived", equalTo(true));
    }
}
