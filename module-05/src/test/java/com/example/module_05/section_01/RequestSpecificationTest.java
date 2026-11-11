package com.example.module_05.section_01;

import static io.restassured.RestAssured.given;
import static io.restassured.config.HeaderConfig.headerConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.openbanking.OpenBankingApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 5.1 – a {@link RequestSpecification} built once and reused by every test.
 * <p>
 * Every request to this API needs the same base URI, port, base path, bearer token and {@code Accept} header. A
 * specification holds them, so each test only adds what is special about it. Unlike {@code RestAssured.baseURI} and
 * friends, a specification is an ordinary object: two test classes can use different ones in parallel.
 * <p>
 * Since REST Assured 6.2, sending a request directly from a built specification ({@code spec.get(..)}) fails with an
 * {@code IllegalStateException}; always go through {@code given(spec)} or {@code given().spec(spec)}.
 */
class RequestSpecificationTest {

    private static OpenBankingApi api;
    private static RequestSpecification server;          // where the API is
    private static RequestSpecification authenticated;   // where it is, plus credentials

    @BeforeAll
    static void startApiAndBuildSpecifications() {
        api = OpenBankingApi.start();

        server = new RequestSpecBuilder()
                .setBaseUri(api.baseUri())
                .setPort(api.port())
                .setBasePath(OpenBankingApi.BASE_PATH)
                .setAccept(ContentType.JSON)
                .build();

        authenticated = new RequestSpecBuilder()
                .addRequestSpecification(server)          // build on another specification
                .addHeader("Authorization", "Bearer test-token")
                .build();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static String interactionId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void givenTakesASpecification() {
        given(authenticated)
            .header("x-fapi-interaction-id", interactionId())
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .body("Data.Account", hasSize(3));
    }

    @Test
    void orAddItWithSpec() {
        given()
            .spec(authenticated)
            .header("x-fapi-interaction-id", interactionId())
        .when()
            .get("/accounts/{id}", "22289")
        .then()
            .statusCode(200)
            .body("Data.Account[0].Nickname", equalTo("Main account"));
    }

    @Test
    void theSpecificationWithoutCredentialsIsRejected() {
        given(server)
            .header("x-fapi-interaction-id", interactionId())
        .when()
            .get("/accounts")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", "Bearer realm=\"aisp\"");
    }

    @Test
    void overridingAHeaderOfTheSpecification() {
        // Headers merge by default: a second Authorization header would be sent next to the spec's one. To replace
        // it, tell REST Assured to overwrite headers with that name. An invalid token now reaches the server alone.
        given(authenticated)
            .config(config().headerConfig(headerConfig().overwriteHeadersWithName("Authorization")))
            .header("Authorization", "Bearer revoked-token")
            .header("x-fapi-interaction-id", interactionId())
        .when()
            .get("/accounts")
        .then()
            .statusCode(401);
    }

    @Test
    void valuesOtherThanHeadersAreReplaced() {
        given(authenticated)
            .basePath("/test-support")                        // replaces the spec's base path for this request
        .when()
            .get("/landing")
        .then()
            .statusCode(200)
            .body("method", equalTo("GET"));
    }

    @Test
    void aSpecificationCanBeInspected() {
        QueryableRequestSpecification query = SpecificationQuerier.query(authenticated);

        assertEquals(OpenBankingApi.BASE_PATH, query.getBasePath());
        assertEquals(api.port(), query.getPort());
        assertEquals("Bearer test-token", query.getHeaders().getValue("Authorization"));
    }
}
