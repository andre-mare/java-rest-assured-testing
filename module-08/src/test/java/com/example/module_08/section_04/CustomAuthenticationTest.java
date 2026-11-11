package com.example.module_08.section_04;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

import com.example.module_08.support.CorrelationIdFilter;
import com.example.signing.SignedPaymentsApi;

import io.restassured.RestAssured;
import io.restassured.authentication.ContextAuthenticationScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 8.4 – a custom authentication scheme for an API key.
 * <p>
 * Since REST Assured 6.3, a custom scheme implements {@link ContextAuthenticationScheme}: one method that gets an
 * {@code AuthenticationContext} and can set headers, answer Basic challenges, sign with OAuth or use a client
 * certificate, without touching Apache HttpClient 4 types (which leave the API in REST Assured 7). It is a functional
 * interface, so a lambda will do.
 * <p>
 * Why a scheme instead of a plain header? An authentication scheme is replaced, not merged, when a request sets
 * another one, and {@code auth().none()} removes it. A header in a specification can't be taken away that easily.
 */
class CustomAuthenticationTest {

    private static SignedPaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = SignedPaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    static ContextAuthenticationScheme apiKey(String key) {
        return context -> context.header("X-API-Key", key);
    }

    private static RequestSpecification partnerApi() {
        return new RequestSpecBuilder()
            .setBaseUri(api.baseUri()).setPort(api.port())
            .addFilter(new CorrelationIdFilter())
            .setAuth(apiKey(SignedPaymentsApi.PARTNER_API_KEY))
            .build();
    }

    @Test
    void theSchemeInASpecification() {
        given(partnerApi()).when().get("/api/v1/partner/accounts").then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void authNoneRemovesIt() {
        given(partnerApi()).auth().none().when().get("/api/v1/partner/accounts").then().statusCode(401);
    }

    @Test
    void aWrongKey() {
        given().baseUri(api.baseUri()).port(api.port())
            .filter(new CorrelationIdFilter())
            .auth().preemptive().basic("not", "an-api-key")          // another scheme, so no X-API-Key at all
        .when()
            .get("/api/v1/partner/accounts")
        .then()
            .statusCode(401);
    }

    @Test
    void asTheDefaultForEveryRequest() {
        RestAssured.authentication = apiKey(SignedPaymentsApi.PARTNER_API_KEY);
        try {
            given().baseUri(api.baseUri()).port(api.port())
                .filter(new CorrelationIdFilter())
            .when()
                .get("/api/v1/partner/accounts")
            .then()
                .statusCode(200);
        } finally {
            RestAssured.reset();
        }
    }
}
