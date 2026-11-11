package com.example.module_12.section_03;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import com.example.module_12.support.SmokeTest;
import com.example.reference.ReferenceDataApi;

import io.restassured.specification.RequestSpecification;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Module 12.3 – organising a large suite with tags, and running parts of it.
 * <p>
 * Tags describe what a test is for, so a pipeline can run the right subset:
 * <pre>
 *   mvn test -pl module-12 -Psmoke       # only @Tag("smoke"): after every deployment, in a minute
 *   mvn test -pl module-12 -Psecurity    # only @Tag("security"): the authentication and authorisation checks
 *   mvn test -pl module-12 -Pno-docker   # everything except @Tag("keycloak"), for machines without Docker
 *   mvn test -pl module-12 -Dgroups="smoke | security"   # a tag expression without a profile
 * </pre>
 * {@link SmokeTest} is a composed annotation ({@code @Test} plus {@code @Tag("smoke")}), as in Module 1 of the JUnit
 * course. {@code KeycloakTokensTest} is tagged {@code keycloak} and {@code security}.
 */
@Tag("reference-data")
class TaggedSuiteTest {

    private static ReferenceDataApi api;

    @BeforeAll
    static void startApi() {
        api = ReferenceDataApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification referenceData() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath("/api/v1");
    }

    @SmokeTest
    void theServiceAnswers() {
        referenceData()
        .when()
            .get("/currencies")
        .then()
            .statusCode(200)
            .time(lessThan(2L), TimeUnit.SECONDS);
    }

    @SmokeTest
    void theMainCurrencyIsThere() {
        referenceData().when().get("/currencies/{code}", "EUR").then().body("name", equalTo("Euro"));
    }

    @Test
    @Tag("regression")
    void pastHolidaysAreKept() {
        referenceData().when().get("/holidays/{country}", "NL").then().body("dates[0]", equalTo("2026-01-01"));
    }
}
