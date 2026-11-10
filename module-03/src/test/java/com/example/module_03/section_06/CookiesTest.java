package com.example.module_03.section_06;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.transactions.TransactionsApi;

import io.restassured.http.Cookie;
import io.restassured.response.ValidatableResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.6 – cookies the server sets, and their security attributes.
 * <p>
 * {@code POST /sessions} answers with:
 * <pre>
 *   Set-Cookie: SESSION=&lt;uuid&gt;; Path=/api; Max-Age=900; Secure; HttpOnly; SameSite=Strict
 *   Set-Cookie: lang=nl; Path=/
 * </pre>
 * {@code cookie(name, value)} checks only the value. For a session cookie, the attributes matter more: without
 * {@code Secure} it travels over plain HTTP, without {@code HttpOnly} scripts can read it, and without {@code SameSite}
 * it is sent with cross-site requests. {@code detailedCookie()} checks them.
 */
class CookiesTest {

    private static final String UUID = "[0-9a-f-]{36}";

    private static TransactionsApi api;

    @BeforeAll
    static void startApi() {
        api = TransactionsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static ValidatableResponse signIn() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
            .when().post("/sessions")
            .then().statusCode(201);
    }

    @Test
    void cookieValues() {
        signIn()
            .cookie("lang", "nl")
            .cookie("SESSION")                                          // only that it exists
            .cookie("SESSION", matchesPattern(UUID));
    }

    @Test
    void theSessionCookieIsLockedDown() {
        signIn()
            .cookie("SESSION", detailedCookie()
                    .value(matchesPattern(UUID))
                    .path("/api")
                    .maxAge(900)
                    .secured(true)
                    .httpOnly(true)
                    .sameSite("Strict"));
    }

    @Test
    void readingACookieAfterwards() {
        Cookie session = signIn().extract().detailedCookie("SESSION");

        assertTrue(session.isSecured());
        assertTrue(session.isHttpOnly());
        assertEquals("Strict", session.getSameSite());
    }
}
