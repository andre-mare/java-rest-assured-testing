package com.example.module_07.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.auth.BankAuthApi;

import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 7.1 – HTTP Basic authentication, challenged and preemptive.
 * <ul>
 *   <li><b>Challenged</b> ({@code auth().basic(..)}): REST Assured first sends the request without credentials. Only
 *       when the server answers 401 with {@code WWW-Authenticate: Basic} does it send them. That costs an extra round
 *       trip, and it fails when the server never challenges.</li>
 *   <li><b>Preemptive</b> ({@code auth().preemptive().basic(..)}): the {@code Authorization} header is sent with the
 *       first request. This is what APIs usually need.</li>
 * </ul>
 * {@code /admin/report} answers 404 instead of 401 without credentials, to hide that it exists, so it never
 * challenges.
 */
class BasicAuthenticationTest {

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

    @Test
    void withoutCredentialsTheServerChallenges() {
        bank()
        .when()
            .get("/admin/health")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", "Basic realm=\"admin\"");
    }

    @Test
    void challengedBasicAnswersTheChallenge() {
        bank().auth().basic("admin", "changeit").when().get("/admin/health").then().statusCode(200);
    }

    @Test
    void preemptiveBasicSendsCredentialsStraightAway() {
        bank().auth().preemptive().basic("admin", "changeit").when().get("/admin/health").then().statusCode(200);
    }

    @Test
    void challengedBasicFailsWhenTheServerNeverChallenges() {
        bank()
            .auth().basic("admin", "changeit")
        .when()
            .get("/admin/report")
        .then()
            .statusCode(404);                                        // the credentials were never sent
    }

    @Test
    void preemptiveBasicWorksThere() {
        bank()
            .auth().preemptive().basic("admin", "changeit")
        .when()
            .get("/admin/report")
        .then()
            .statusCode(200)
            .body("report", equalTo("daily"));
    }

    @Test
    void wrongCredentials() {
        bank().auth().preemptive().basic("admin", "guess").when().get("/admin/health").then().statusCode(401);
    }
}
