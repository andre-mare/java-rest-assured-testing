package com.example.module_06.section_04;

import static com.example.module_06.support.SamplePayments.domesticPayment;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.module_06.support.OpenApiContractFilter;
import com.example.pisp.PaymentInitiationApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 6.4 – every response checked against the API's OpenAPI description, without writing one schema check.
 * <p>
 * The {@link OpenApiContractFilter} sits in the request specification, so it runs for every request these tests send.
 * The tests themselves only check behaviour; the filter fails a test as soon as a response doesn't match the
 * documented contract. A contract test answers "does the API keep its promises?", a functional test "does it do the
 * right thing?". A good suite has both.
 */
class OpenApiContractTest {

    private static PaymentInitiationApi api;
    private static RequestSpecification pisp;

    @BeforeAll
    static void startApi() {
        api = PaymentInitiationApi.start();
        pisp = new RequestSpecBuilder()
            .setBaseUri(api.baseUri()).setPort(api.port()).setBasePath(PaymentInitiationApi.BASE_PATH)
            .addFilter(OpenApiContractFilter.fromClasspath("openapi/payment-initiation.openapi.json"))
            .build();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @Test
    void initiateAndReadAPayment() {
        String id = given(pisp)
            .contentType(ContentType.JSON).body(domesticPayment("INV-10", "99.95"))
            .when().post("/domestic-payments")
            .then().statusCode(201)
            .extract().path("paymentId");

        given(pisp).when().get("/domestic-payments/{id}", id).then().statusCode(200);
    }

    @Test
    void documentedErrorsPassToo() {
        given(pisp).when().get("/domestic-payments/{id}", "pmt-404").then().statusCode(404);
        given(pisp).contentType(ContentType.JSON).body("{}").when().post("/domestic-payments").then().statusCode(422);
    }

    @Test
    void aBodyThatBreaksTheContractFailsTheTest() {
        String id = given(pisp)
            .contentType(ContentType.JSON).body(domesticPayment("INV-11", "1.00"))
            .when().post("/domestic-payments")
            .then().extract().path("paymentId");

        AssertionError error = assertThrows(AssertionError.class, () ->
            given(pisp).queryParam("format", "v0").when().get("/domestic-payments/{id}", id));

        assertThat(error.getMessage(), containsString("GET /domestic-payments/{paymentId} 200 body"));
    }

    @Test
    void anUndocumentedOperationFailsTheTest() {
        AssertionError error = assertThrows(AssertionError.class, () ->
            given(pisp).when().get("/domestic-payments"));            // listing payments isn't in the contract

        assertThat(error.getMessage(), containsString("GET /domestic-payments is not in the OpenAPI description"));
    }
}
