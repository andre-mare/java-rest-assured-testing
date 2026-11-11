package com.example.module_07.section_04;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.auth.BankAuthApi;

import io.restassured.filter.session.SessionFilter;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Module 7.4 – a browser-style login: an HTML form with a CSRF token, then a session cookie.
 * <p>
 * A {@link SessionFilter} remembers the session id ({@code JSESSIONID} by default) from a response and sends it with
 * every later request that uses the same filter. The CSRF token is read from the login page with {@code htmlPath()}.
 * REST Assured also has {@code auth().form(..)} with {@code FormAuthConfig} and {@code CsrfConfig}, which does these
 * steps for you; doing them by hand shows what happens and works with any login page.
 */
class SessionAndCsrfTest {

    private static BankAuthApi api;
    private SessionFilter session;

    @BeforeAll
    static void startApi() {
        api = BankAuthApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @BeforeEach
    void newSession() {
        session = new SessionFilter();
    }

    private RequestSpecification portal() {
        return given().baseUri(api.baseUri()).port(api.port()).filter(session);
    }

    private String csrfTokenFromTheLoginPage() {
        return portal()
            .when().get("/portal/login")
            .then().statusCode(200)
            .extract().htmlPath().getString("**.find { it.@name == '_csrf' }.@value");
    }

    private void logIn(String csrf) {
        portal()
            .formParam("_csrf", csrf)
            .formParam("username", "anna")
            .formParam("password", "correct horse")
        .when()
            .post("/portal/login")
        .then()
            .statusCode(302)                                         // POST redirects aren't followed by default
            .header("Location", "/portal/home")
            .cookie("JSESSIONID", notNullValue());
    }

    @Test
    void logInAndUseTheSession() {
        logIn(csrfTokenFromTheLoginPage());

        assertNotNull(session.getSessionId());

        portal()
        .when()
            .get("/portal/home")
        .then()
            .statusCode(200)
            .body("user", equalTo("anna"));
    }

    @Test
    void withoutASessionYouAreSentToTheLoginPage() {
        given().baseUri(api.baseUri()).port(api.port())
            .redirects().follow(false)
        .when()
            .get("/portal/home")
        .then()
            .statusCode(302)
            .header("Location", "/portal/login");
    }

    @Test
    void theSessionIdCanBeSetExplicitly() {
        logIn(csrfTokenFromTheLoginPage());

        given().baseUri(api.baseUri()).port(api.port())
            .sessionId(session.getSessionId())                       // sends JSESSIONID=<id>
        .when()
            .get("/portal/home")
        .then()
            .body("user", equalTo("anna"));
    }

    @Test
    void aMissingCsrfTokenIsRejected() {
        portal()
            .formParam("username", "anna")
            .formParam("password", "correct horse")
        .when()
            .post("/portal/login")
        .then()
            .statusCode(403);
    }

    @Test
    void aCsrfTokenWorksOnlyOnce() {
        String csrf = csrfTokenFromTheLoginPage();
        logIn(csrf);

        portal()
            .formParam("_csrf", csrf)
            .formParam("username", "anna")
            .formParam("password", "correct horse")
        .when()
            .post("/portal/login")
        .then()
            .statusCode(403);
    }
}
