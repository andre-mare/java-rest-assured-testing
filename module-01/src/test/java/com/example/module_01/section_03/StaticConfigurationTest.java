package com.example.module_01.section_03;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;

import com.example.accounts.AccountsApi;

import io.restassured.RestAssured;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.3 – the same settings as static defaults on {@link RestAssured}.
 * <p>
 * Static fields such as {@link RestAssured#baseURI}, {@link RestAssured#port} and {@link RestAssured#basePath} apply
 * to every request in the JVM that doesn't set its own value. They make tests short, but they are global state:
 * <ul>
 *   <li>Always undo them, here with {@link RestAssured#reset()} in {@code @AfterAll}, or later test classes
 *       inherit them by accident.</li>
 *   <li>Two classes that set them differently must not run in parallel. Module 5 replaces static defaults with
 *       reusable request specifications, which have neither problem.</li>
 * </ul>
 */
class StaticConfigurationTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApiAndSetDefaults() {
        api = AccountsApi.start();
        RestAssured.baseURI = api.baseUri();
        RestAssured.port = api.port();
        RestAssured.basePath = AccountsApi.BASE_PATH;
    }

    @AfterAll
    static void resetDefaultsAndStopApi() {
        RestAssured.reset();            // back to http://localhost:8080 with an empty base path
        api.close();
    }

    @Test
    void requestsUseTheStaticDefaults() {
        get("/accounts/acc-1002").then().statusCode(200).body("type", equalTo("SAVINGS"));
    }

    @Test
    void aValueOnTheRequestOverridesTheDefault() {
        RestAssured.given()
            .basePath("/api")                           // replaces "/api/v1" for this request only
        .when()
            .get("/health")
        .then()
            .statusCode(200);
    }
}
