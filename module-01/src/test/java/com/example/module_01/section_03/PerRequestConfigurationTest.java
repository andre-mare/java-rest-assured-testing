package com.example.module_01.section_03;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.accounts.AccountsApi;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 1.3 – where a request goes, set on the request itself.
 * <p>
 * REST Assured builds the URL from four parts: {@code baseUri} (scheme and host), {@code port},
 * {@code basePath} (a prefix shared by many endpoints) and the path given to {@code get(..)}.
 * <pre>
 *   http://localhost : 54321 /api/v1 /accounts/acc-1004
 *   └─── baseUri ──┘  └port┘ └base┘ └──── path ──────┘
 * </pre>
 * Setting them per request is verbose, but every test is independent and safe to run in parallel.
 */
class PerRequestConfigurationTest {

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
    void basePathIsPutInFrontOfEveryPath() {
        given()
            .baseUri(api.baseUri())
            .port(api.port())
            .basePath(AccountsApi.BASE_PATH)            // "/api/v1"
        .when()
            .get("/accounts/acc-1004")                  // goes to /api/v1/accounts/acc-1004
        .then()
            .statusCode(200)
            .body("owner", equalTo("Emily Clarke"));
    }

    @Test
    void thePortCanAlsoBePartOfTheBaseUri() {
        given()
            .baseUri("http://localhost:" + api.port())
        .when()
            .get("/api/health")
        .then()
            .statusCode(200);
    }

    @Test
    void anAbsoluteUrlWinsOverBaseUriAndPort() {
        given()
            .baseUri("http://localhost")
            .port(1)                                    // nothing listens on port 1 ...
        .when()
            .get("http://localhost:" + api.port() + "/api/health")   // ... but this URL is complete
        .then()
            .statusCode(200);
    }
}
