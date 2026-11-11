package com.example.module_05.section_04;

import static io.restassured.RestAssured.given;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.openbanking.OpenBankingApi;

import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Module 5.4 – keeping tokens out of logs.
 * <p>
 * CI logs are kept for a long time and read by many people. A logged {@code Authorization} header is a leaked
 * credential. {@code LogConfig.blacklistHeader(..)} prints such headers as {@code [ BLACKLISTED ]}. These tests send
 * the log to an in-memory stream with {@code defaultStream(..)}, so they can check what would have been printed.
 */
class SecretsInLogsTest {

    private static final String TOKEN = "test-token";

    private static OpenBankingApi api;
    private ByteArrayOutputStream log;
    private RestAssuredConfig loggingToMemory;

    @BeforeAll
    static void startApi() {
        api = OpenBankingApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @BeforeEach
    void captureTheLog() {
        log = new ByteArrayOutputStream();
        loggingToMemory = config().logConfig(logConfig()
                .defaultStream(new PrintStream(log, true, StandardCharsets.UTF_8))
                .blacklistHeader("Authorization", "Cookie"));
    }

    private RequestSpecification openBanking() {
        return given()
            .config(loggingToMemory)
            .baseUri(api.baseUri()).port(api.port()).basePath(OpenBankingApi.BASE_PATH)
            .header("Authorization", "Bearer " + TOKEN)
            .header("x-fapi-interaction-id", UUID.randomUUID().toString());
    }

    private String logged() {
        return log.toString(StandardCharsets.UTF_8);
    }

    @Test
    void blacklistedHeadersAreMasked() {
        openBanking()
            .log().headers()
        .when()
            .get("/accounts")
        .then()
            .statusCode(200);

        assertTrue(logged().contains("Authorization=[ BLACKLISTED ]"), logged());
        assertFalse(logged().contains(TOKEN), logged());
        assertTrue(logged().contains("x-fapi-interaction-id="), logged());   // other headers are still there
    }

    @Test
    void maskingAlsoAppliesWhenLoggingOnFailure() {
        assertThrows(AssertionError.class, () ->
            openBanking()
                .log().ifValidationFails(LogDetail.HEADERS)
            .when()
                .get("/accounts")
            .then()
                .body("Data.Account[0].Nickname", equalTo("not the nickname")));

        assertTrue(logged().contains("[ BLACKLISTED ]"), logged());
        assertFalse(logged().contains(TOKEN), logged());
    }

    @Test
    void nothingIsLoggedWhenTheTestPasses() {
        openBanking()
            .log().ifValidationFails()
        .when()
            .get("/accounts")
        .then()
            .log().ifValidationFails()
            .statusCode(200);

        assertTrue(logged().isEmpty(), logged());
    }
}
