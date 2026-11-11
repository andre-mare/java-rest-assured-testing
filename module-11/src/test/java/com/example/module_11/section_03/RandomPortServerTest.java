package com.example.module_11.section_03;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import com.example.module_11.support.TestTokens;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Module 11.3 – the whole application on a real port, tested with plain REST Assured over HTTP.
 * <p>
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} starts the application with an embedded Tomcat on a free
 * port, and {@code @LocalServerPort} tells the test which. Requests now travel over the network stack exactly as a
 * client's would, through every filter, converter and error handler. Tokens are real signed JWTs from
 * {@link TestTokens}, so signature, expiry and audience are checked too. Slower than a slice, but the closest to
 * production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RandomPortServerTest {

    @LocalServerPort
    private int port;

    private RequestSpecification accounts() {
        return given().baseUri("http://localhost").port(port).basePath("/api/v1/accounts");
    }

    @Test
    void aReaderCanListAccounts() {
        accounts()
            .auth().oauth2(TestTokens.withScopes("accounts:read"))
        .when()
            .get()
        .then()
            .statusCode(200)
            .body("id", hasItems("acc-1001", "acc-1002"));
    }

    @Test
    void aWriterCanOpenAnAccountAndAReaderCanReadIt() {
        String location = accounts()
            .auth().oauth2(TestTokens.withScopes("accounts:write"))
            .contentType(ContentType.JSON)
            .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Lotte Laan\", \"currency\": \"EUR\"}")
        .when()
            .post()
        .then()
            .statusCode(201)
            .extract().header("Location");

        given().baseUri("http://localhost").port(port)
            .auth().oauth2(TestTokens.withScopes("accounts:read"))
        .when()
            .get(location)
        .then()
            .statusCode(200)
            .body("owner", equalTo("Lotte Laan"));
    }

    @Test
    void anExpiredToken() {
        accounts()
            .auth().oauth2(TestTokens.token(List.of("accounts:read"), TestTokens.AUDIENCE, Duration.ofMinutes(-5)))
        .when()
            .get()
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", containsString("invalid_token"));
    }

    @Test
    void aTokenForAnotherAudience() {
        accounts()
            .auth().oauth2(TestTokens.token(List.of("accounts:read"), "payments-api", Duration.ofMinutes(5)))
        .when()
            .get()
        .then()
            .statusCode(401);
    }

    @Test
    void theWrongScope() {
        accounts()
            .auth().oauth2(TestTokens.withScopes("accounts:write"))
        .when()
            .get("/{id}", "acc-1001")
        .then()
            .statusCode(403)
            .header("WWW-Authenticate", containsString("insufficient_scope"));
    }

    @Test
    void anUnknownAccountIsAProblem() {
        accounts()
            .auth().oauth2(TestTokens.withScopes("accounts:read"))
        .when()
            .get("/{id}", "acc-9999")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("status", equalTo(404))
            .body("detail", equalTo("Account acc-9999 does not exist"));
    }
}
