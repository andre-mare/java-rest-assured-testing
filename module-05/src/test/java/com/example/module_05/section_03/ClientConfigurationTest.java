package com.example.module_05.section_03;

import static io.restassured.RestAssured.given;
import static io.restassured.config.DecoderConfig.decoderConfig;
import static io.restassured.config.HttpClientConfig.httpClientConfig;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.openbanking.OpenBankingApi;

import io.restassured.specification.RequestSpecification;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 5.3 – {@code RestAssuredConfig}: how the HTTP client behaves.
 * <p>
 * A config is immutable: each {@code xxxConfig(..)} call returns a new one. Set it per request with
 * {@code config(..)}, in a {@code RequestSpecBuilder} with {@code setConfig(..)}, or (globally, with the usual
 * warnings) in {@code RestAssured.config}.
 * <ul>
 *   <li>{@code HttpClientConfig}: timeouts. Since REST Assured 6.3, {@code connectTimeout(Duration)} and
 *       {@code readTimeout(Duration)} replace the Apache HttpClient 4 parameters, which REST Assured 7 removes.</li>
 *   <li>{@code RedirectConfig}: whether and how redirects are followed.</li>
 *   <li>{@code DecoderConfig} and {@code EncoderConfig}: charsets.</li>
 * </ul>
 */
class ClientConfigurationTest {

    private static OpenBankingApi api;

    @BeforeAll
    static void startApi() {
        api = OpenBankingApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification testSupport() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath("/test-support");
    }

    // ------------------------------------------------------------------------------------------- timeouts

    @Test
    void aReadTimeoutStopsWaitingForASlowServer() {
        Exception exception = assertThrows(Exception.class, () ->
            testSupport()
                .config(config().httpClient(httpClientConfig().readTimeout(Duration.ofMillis(300))))
                .queryParam("millis", 2000)
            .when()
                .get("/slow"));

        assertCauseIs(SocketTimeoutException.class, exception);
    }

    @Test
    void withinTheTimeoutAllIsWell() {
        testSupport()
            .config(config().httpClient(httpClientConfig()
                    .connectTimeout(Duration.ofSeconds(2))
                    .readTimeout(Duration.ofSeconds(5))))
            .queryParam("millis", 100)
        .when()
            .get("/slow")
        .then()
            .statusCode(200);
    }

    // ------------------------------------------------------------------------------------------- redirects

    @Test
    void getRedirectsAreFollowedByDefault() {
        testSupport().when().get("/redirect").then().statusCode(200).body("method", equalTo("GET"));
    }

    @Test
    void followingCanBeTurnedOff() {
        testSupport()
            .redirects().follow(false)                              // a shortcut for redirectConfig().followRedirects(false)
        .when()
            .get("/redirect")
        .then()
            .statusCode(302)
            .header("Location", "/test-support/landing");
    }

    @Test
    void postRedirectsAreNotFollowedByDefault() {
        testSupport().when().post("/redirect").then().statusCode(302);
    }

    @Test
    void followRedirectsForAllMethods() {
        // REST Assured 6.3: follow redirects of POST, PUT and DELETE too. A 302 after a POST is followed with a GET,
        // as browsers do.
        testSupport()
            .config(config().redirect(redirectConfig().followRedirectsForAllMethods(true)))
        .when()
            .post("/redirect")
        .then()
            .statusCode(200);
    }

    // ------------------------------------------------------------------------------------------- charsets

    @Test
    void withoutACharsetTextIsDecodedWithTheDefaultContentCharset() {
        // The server sends UTF-8 but says only "text/plain", so the client must guess. REST Assured's guess is
        // DecoderConfig's defaultContentCharset, which is the JVM's default charset (UTF-8 since JDK 18), so on
        // a stock JVM this works by luck. Guess ISO-8859-1, HTTP's old default for text, and the umlauts break.
        testSupport()
            .config(config().decoderConfig(decoderConfig().defaultContentCharset("ISO-8859-1")))
        .when()
            .get("/greeting")
        .then()
            .body(not(equalTo("Grüße uit Zürich")))
            .body(equalTo(new String("Grüße uit Zürich".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
    }

    @Test
    void decoderConfigSetsTheDefaultCharset() {
        testSupport()
            .config(config().decoderConfig(decoderConfig().defaultContentCharset("UTF-8")))
        .when()
            .get("/greeting")
        .then()
            .body(equalTo("Grüße uit Zürich"));
    }

    private static void assertCauseIs(Class<? extends Throwable> expected, Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (expected.isInstance(t)) {
                return;
            }
        }
        throw new AssertionError("Expected a " + expected.getSimpleName() + " in the causes of " + thrown, thrown);
    }
}
