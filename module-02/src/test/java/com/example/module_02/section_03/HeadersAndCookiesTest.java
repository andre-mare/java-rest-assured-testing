package com.example.module_02.section_03;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.HeaderConfig.headerConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;

import com.example.payments.PaymentsApi;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 2.3 – headers and cookies on the request.
 * <p>
 * The echo endpoint lowercases header names, so {@code headers.'x-request-id'} reads the values of
 * {@code X-Request-ID}. The quotes are needed because of the dashes in the name.
 */
class HeadersAndCookiesTest {

    private static PaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification paymentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH);
    }

    // ------------------------------------------------------------------------------------------- headers

    @Test
    void oneHeader() {
        paymentsApi()
            .header("X-Request-ID", "req-1")
        .when()
            .get("/echo")
        .then()
            .body("headers.'x-request-id'", contains("req-1"))
            .header("X-Request-ID", "req-1");                          // the API echoes it in the response
    }

    @Test
    void severalHeadersFromAMapOrAsObjects() {
        paymentsApi()
            .headers(Map.of("X-Request-ID", "req-2", "X-Channel", "mobile"))
            .header(new Header("X-Device", "android"))
        .when()
            .get("/echo")
        .then()
            .body("headers.'x-channel'", contains("mobile"))
            .body("headers.'x-device'", contains("android"));
    }

    @Test
    void aHeaderWithSeveralValues() {
        paymentsApi()
            .header("X-Tag", "salary", "monthly")                      // sent as two X-Tag headers
        .when()
            .get("/echo")
        .then()
            .body("headers.'x-tag'", contains("salary", "monthly"));
    }

    @Test
    void theSameHeaderTwiceIsMerged() {
        paymentsApi()
            .header("X-Tag", "salary")
            .header("X-Tag", "monthly")
        .when()
            .get("/echo")
        .then()
            .body("headers.'x-tag'", contains("salary", "monthly"));
    }

    @Test
    void acceptAndContentTypeAreOverwrittenNotMerged() {
        paymentsApi()
            .header("Accept", "text/plain")
            .header("Accept", "application/json")                      // replaces the first value
        .when()
            .get("/echo")
        .then()
            .body("headers.accept", contains("application/json"));
    }

    @Test
    void headerConfigDecidesWhichHeadersAreOverwritten() {
        paymentsApi()
            .config(config().headerConfig(headerConfig().overwriteHeadersWithName("X-Tag")))
            .header("X-Tag", "salary")
            .header("X-Tag", "monthly")
        .when()
            .get("/echo")
        .then()
            .body("headers.'x-tag'", contains("monthly"));
    }

    @Test
    void acceptHasItsOwnMethod() {
        paymentsApi()
            .accept(ContentType.JSON)
        .when()
            .get("/echo")
        .then()
            .body("headers.accept[0]", equalTo("application/json, application/javascript, text/javascript, text/json"));
    }

    // ------------------------------------------------------------------------------------------- content type

    @Test
    void jsonGetsNoCharsetAdded() {
        paymentsApi()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/echo")
        .then()
            .body("contentType", equalTo("application/json"));
    }

    @Test
    void textGetsTheDefaultCharsetAdded() {
        // A surprise for many: text content types get "; charset=ISO-8859-1", the HTTP/1.1 default of old.
        paymentsApi()
            .contentType("text/plain")
            .body("hello")
        .when()
            .post("/echo")
        .then()
            .body("contentType", equalTo("text/plain; charset=ISO-8859-1"));
    }

    @Test
    void addingTheCharsetCanBeTurnedOff() {
        paymentsApi()
            .config(config().encoderConfig(encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
            .contentType("text/plain")
            .body("hello")
        .when()
            .post("/echo")
        .then()
            .body("contentType", equalTo("text/plain"));
    }

    // ------------------------------------------------------------------------------------------- cookies

    @Test
    void oneCookie() {
        paymentsApi()
            .cookie("theme", "dark")
        .when()
            .get("/echo")
        .then()
            .body("cookies", hasEntry("theme", "dark"));
    }

    @Test
    void severalCookiesShareOneCookieHeader() {
        paymentsApi()
            .cookies(Map.of("theme", "dark"))
            .cookie(new Cookie.Builder("lang", "nl").build())
        .when()
            .get("/echo")
        .then()
            .body("cookies.theme", equalTo("dark"))
            .body("cookies.lang", equalTo("nl"))
            .body("headers.cookie.size()", equalTo(1));
    }
}
