package com.example.module_08.section_02;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.module_08.support.CorrelationIdFilter;
import com.example.module_08.support.HttpMessageSignatureFilter;
import com.example.module_08.support.MaskingLogFilter;
import com.example.signing.SignedPaymentsApi;

import io.restassured.builder.ResponseBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 8.2 – filters worth having in a real suite: timing, logs without personal data, and repairing a response.
 */
class UsefulFiltersTest {

    private static final String PAYMENT = """
        {"debtorName": "Anna de Vries", "debtorIban": "NL91ABNA0417164300",
         "creditorName": "Jan Jansen", "creditorIban": "NL69INGB0123456789", "amount": "42.00"}""";

    private static SignedPaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = SignedPaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification signedApi() {
        return given().baseUri(api.baseUri()).port(api.port())
            .filter(new CorrelationIdFilter())
            .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET));
    }

    @Test
    void timeEveryExchange() {
        List<Duration> timings = new ArrayList<>();
        Filter timer = (request, responseSpec, context) -> {
            long start = System.nanoTime();
            Response response = context.next(request, responseSpec);
            timings.add(Duration.ofNanos(System.nanoTime() - start));
            return response;
        };

        signedApi().filter(timer).contentType(ContentType.JSON).body(PAYMENT).when().post("/api/v1/payments")
            .then().statusCode(201);

        assertEquals(1, timings.size());
        assertTrue(timings.getFirst().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void logWithPersonalDataMasked() {
        ByteArrayOutputStream log = new ByteArrayOutputStream();

        signedApi()
            .filter(new MaskingLogFilter(new PrintStream(log, true, StandardCharsets.UTF_8)))
            .contentType(ContentType.JSON)
            .body(PAYMENT)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(201);

        String logged = log.toString(StandardCharsets.UTF_8);
        assertTrue(logged.contains("NL91**********4300"), logged);
        assertFalse(logged.contains("NL91ABNA0417164300"), logged);
        assertFalse(logged.contains("Anna de Vries"), logged);
        assertTrue(logged.contains("\"amount\""), logged);           // everything else stays readable
    }

    @Test
    void theBuiltInLoggingFiltersWriteWhereYouWant() {
        // RequestLoggingFilter and ResponseLoggingFilter are what log() uses; as filters they can go in a spec.
        ByteArrayOutputStream log = new ByteArrayOutputStream();

        given().baseUri(api.baseUri()).port(api.port())
            .filter(new RequestLoggingFilter(LogDetail.METHOD, new PrintStream(log, true, StandardCharsets.UTF_8)))
        .when()
            .get("/api/v1/legacy/fx-rates");

        assertTrue(log.toString(StandardCharsets.UTF_8).contains("GET"));
    }

    @Test
    void repairAResponseWithResponseBuilder() {
        // The legacy endpoint sends JSON as text/plain, so body paths can't read it. A filter can hand REST Assured
        // a corrected copy of the response. Section 5 shows the lighter fix for this case: a parser.
        Filter fixContentType = (request, responseSpec, context) -> {
            Response response = context.next(request, responseSpec);
            return new ResponseBuilder().clone(response).setContentType(ContentType.JSON).build();
        };

        given().baseUri(api.baseUri()).port(api.port())
            .filter(fixContentType)
        .when()
            .get("/api/v1/legacy/fx-rates")
        .then()
            .contentType(ContentType.JSON)
            .body("base", equalTo("EUR"))
            .body("rates.GBP", equalTo(0.85f));
    }
}
