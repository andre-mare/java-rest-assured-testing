package com.example.module_04.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.consents.ConsentsApi;
import com.example.module_04.support.Models.NewCustomer;

import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 4.1 – getting values out of a response, after checking it.
 * <p>
 * {@code then()...extract()} ends the validation chain and gives an {@link ExtractableResponse}. Validate first and
 * extract second: if the status is wrong, the test fails with a clear message instead of a confusing
 * {@code NullPointerException} later.
 */
class ExtractingValuesTest {

    private static ConsentsApi api;

    @BeforeAll
    static void startApi() {
        api = ConsentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification consentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(ConsentsApi.BASE_PATH);
    }

    @Test
    void extractOneValueByPath() {
        String name = consentsApi()
        .when()
            .get("/customers/{id}", "cust-1")
        .then()
            .statusCode(200)
            .extract().path("name");                                    // the type is inferred from the variable

        assertEquals("Anna de Vries", name);
    }

    @Test
    void extractAListByPath() {
        List<String> segments = consentsApi()
        .when()
            .get("/customers")
        .then()
            .extract().path("items.segment");

        assertTrue(segments.contains("BUSINESS"));
    }

    @Test
    void extractHeadersAndTheStatusCode() {
        ExtractableResponse<Response> created = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewCustomer("Pieter Post", "pieter@example.nl", "RETAIL"))
        .when()
            .post("/customers")
        .then()
            .statusCode(201)
            .extract();                                                 // keep it, and read several parts

        String id = created.path("id");
        assertEquals("/api/v1/customers/" + id, created.header("Location"));
        assertEquals(201, created.statusCode());
        assertTrue(created.contentType().startsWith("application/json"));
    }

    @Test
    void extractTheWholeResponse() {
        Response response = consentsApi()
        .when()
            .get("/customers/{id}", "cust-2")
        .then()
            .statusCode(200)
            .extract().response();

        assertEquals("Jan Jansen", response.path("name"));
        assertTrue(response.asString().contains("\"email\""));          // the raw body as text
        response.then().body("segment", equalTo("RETAIL"));            // and back to validation if you like
    }

    @Test
    void aValueFromOneResponseInTheNextRequest() {
        String secondCustomerOnPage = consentsApi()
            .queryParam("size", 2)
        .when()
            .get("/customers")
        .then()
            .extract().path("items[1].id");

        consentsApi()
        .when()
            .get("/customers/{id}", secondCustomerOnPage)
        .then()
            .statusCode(200)
            .body("id", equalTo(secondCustomerOnPage));
    }
}
