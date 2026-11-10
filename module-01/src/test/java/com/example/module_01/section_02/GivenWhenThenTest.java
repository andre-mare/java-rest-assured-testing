package com.example.module_01.section_02;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.accounts.AccountsApi;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.2 – the parts of a REST Assured test, and the shorter forms you will meet in other code bases.
 * <p>
 * {@code given()} returns a {@link RequestSpecification}. {@code when()} is only there to read well and returns the
 * same object. {@code get(..)} sends the request and returns a {@link Response}. {@code then()} turns the response
 * into a {@code ValidatableResponse}, which checks each expectation as you add it.
 */
class GivenWhenThenTest {

    private static AccountsApi api;
    private static String healthUrl;

    @BeforeAll
    static void startApi() {
        api = AccountsApi.start();
        healthUrl = api.baseUri() + ":" + api.port() + "/api/health";
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @Test
    void fullForm() {
        given()
            .baseUri(api.baseUri())
            .port(api.port())
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
        .and()                                  // and() is syntactic sugar too: it changes nothing
            .body("status", equalTo("UP"));
    }

    @Test
    void givenCanBeLeftOutWhenTheRequestNeedsNoSetUp() {
        when()
            .get(healthUrl)                     // an absolute URL needs no baseUri or port
        .then()
            .statusCode(200);
    }

    @Test
    void theShortestForm() {
        get(healthUrl).then().statusCode(200);
    }

    @Test
    void theRequestSpecificationIsAnOrdinaryObject() {
        RequestSpecification request = given()
            .baseUri(api.baseUri())
            .port(api.port());

        request.when().get("/api/health").then().statusCode(200);
    }

    @Test
    void aResponseCanBeKeptAndCheckedLater() {
        Response response = given()
            .baseUri(api.baseUri())
            .port(api.port())
        .when()
            .get("/api/health");

        // Either hand it to REST Assured's own assertions ...
        response.then().statusCode(200);

        // ... or read values from it and use any assertion library. Module 4 covers extraction in depth.
        assertEquals(200, response.statusCode());
        assertEquals("UP", response.path("status"));
    }

    @Test
    void pathParametersFillPlaceholdersInTheUrl() {
        given()
            .baseUri(api.baseUri())
            .port(api.port())
        .when()
            .get("/api/v1/accounts/{id}", "acc-1001")   // {id} is replaced by "acc-1001"; Module 2 has more
        .then()
            .statusCode(200)
            .body("owner", equalTo("Anna de Vries"));
    }
}
