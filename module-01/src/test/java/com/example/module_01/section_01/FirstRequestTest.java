package com.example.module_01.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.accounts.AccountsApi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.1 – your very first REST Assured test.
 * <p>
 * What is new compared to a plain JUnit test:
 * <ul>
 *   <li>There is a real HTTP server. {@code @BeforeAll} starts the Accounts API once for the class on a free
 *       port, and {@code @AfterAll} stops it.</li>
 *   <li>The test sends an HTTP request and checks the HTTP response, instead of calling a Java method.</li>
 *   <li>REST Assured reads like a sentence: <b>given</b> this request, <b>when</b> I send it, <b>then</b> I
 *       expect this response. It maps directly onto Arrange, Act, Assert.</li>
 * </ul>
 */
class FirstRequestTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApi() {
        api = AccountsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @Test
    void healthEndpointAnswersWith200() {
        given()                                 // Arrange: describe the request
            .baseUri(api.baseUri())             //   http://localhost
            .port(api.port())                   //   the port the API picked
        .when()                                 // Act: send it
            .get("/api/health")
        .then()                                 // Assert: check the response
            .statusCode(200);
    }

    @Test
    void healthEndpointReportsThatItIsUp() {
        given()
            .baseUri(api.baseUri())
            .port(api.port())
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));     // "status" is a path into the JSON body {"status":"UP"}
    }
}
