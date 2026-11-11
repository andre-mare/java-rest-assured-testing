package com.example.module_06.section_02;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

import com.example.pisp.PaymentInitiationApi;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 6.2 – one contract for every error response: RFC 9457 problem details.
 * <p>
 * {@code problem.schema.json} uses {@code if}/{@code then}/{@code else} (draft 2020-12): a 422 must list field errors
 * in {@code errors}, and any other status must not. Combining it with the content type in a response specification
 * gives a single {@code spec(problem)} to check any error.
 */
class ErrorContractTest {

    private static PaymentInitiationApi api;
    private static ResponseSpecification problem;

    @BeforeAll
    static void startApi() {
        api = PaymentInitiationApi.start();
        problem = new ResponseSpecBuilder()
            .expectContentType("application/problem+json")
            .expectBody(matchesJsonSchemaInClasspath("schemas/problem.schema.json"))
            .build();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification pisp() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentInitiationApi.BASE_PATH);
    }

    @Test
    void notFound() {
        pisp().when().get("/domestic-payments/{id}", "pmt-999").then().statusCode(404).spec(problem);
    }

    @Test
    void malformedJson() {
        pisp().contentType(ContentType.JSON).body("{ not json").when().post("/domestic-payments")
            .then().statusCode(400).spec(problem);
    }

    @Test
    void wrongContentType() {
        pisp().contentType(ContentType.TEXT).body("hello").when().post("/domestic-payments")
            .then().statusCode(415).spec(problem);
    }

    @Test
    void validationErrorsListTheFields() {
        pisp()
            .contentType(ContentType.JSON)
            .body("{\"instructionId\": \"INV-1\", \"amount\": {\"value\": \"1\", \"currency\": \"euro\"}}")
        .when()
            .post("/domestic-payments")
        .then()
            .statusCode(422)
            .spec(problem)
            .body("errors.field", hasItems("debtor", "creditor", "amount.value", "amount.currency"));
    }

    @Test
    void theSchemaItselfIsStrictAboutErrors() {
        // Test the schema too: a 422 without errors, or a 404 with them, must be rejected.
        assertThat("{\"type\":\"about:blank\",\"title\":\"Unprocessable Content\",\"status\":422}",
                not(matchesJsonSchemaInClasspath("schemas/problem.schema.json")));
        assertThat("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
                        + "\"errors\":[{\"field\":\"id\",\"detail\":\"unknown\"}]}",
                not(matchesJsonSchemaInClasspath("schemas/problem.schema.json")));
    }
}
