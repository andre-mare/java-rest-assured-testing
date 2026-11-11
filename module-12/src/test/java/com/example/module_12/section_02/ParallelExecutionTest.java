package com.example.module_12.section_02;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import com.example.reference.ReferenceDataApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Module 12.2 – running API tests in parallel.
 * <p>
 * Every request to the reference data API costs about 200 ms, so these eight tests take about 1.6 seconds one after
 * another and roughly a quarter of that on four threads ({@code junit-platform.properties} in this module enables
 * parallel execution, and this class opts in with {@code @Execution(CONCURRENT)}). Compare the time Surefire reports
 * with and without the annotation.
 * <p>
 * The rules that make REST Assured tests safe in parallel:
 * <ul>
 *   <li>No static configuration: never set {@code RestAssured.baseURI}, {@code port}, {@code filters} or
 *       {@code requestSpecification} in tests that run concurrently. Build a {@link RequestSpecification} instead;
 *       specifications are immutable once built and can be shared between threads.</li>
 *   <li>No shared mutable state between tests: each test creates or reads its own data.</li>
 *   <li>The API under test must cope with concurrent requests (the JDK server here uses virtual threads).</li>
 * </ul>
 * REST Assured 6.2 also fixed races in its static configuration, but the safest static state is none.
 */
@Execution(ExecutionMode.CONCURRENT)
class ParallelExecutionTest {

    private static ReferenceDataApi api;
    private static RequestSpecification referenceData;
    private static final Set<String> THREADS = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void startApi() {
        api = ReferenceDataApi.start();
        referenceData = new RequestSpecBuilder()
            .setBaseUri(api.baseUri()).setPort(api.port()).setBasePath("/api/v1")
            .build();
    }

    @AfterAll
    static void stopApiAndReport() {
        api.close();
        System.out.println("ParallelExecutionTest ran on " + THREADS.size() + " thread(s): " + THREADS);
    }

    private static RequestSpecification request() {
        THREADS.add(Thread.currentThread().getName());
        return given(referenceData);
    }

    @Test
    void allCurrencies() {
        request().when().get("/currencies").then().statusCode(200).body("$", hasSize(4));
    }

    @Test
    void euro() {
        request().when().get("/currencies/{code}", "EUR").then().body("minorUnits", equalTo(2));
    }

    @Test
    void yenHasNoMinorUnits() {
        request().when().get("/currencies/{code}", "JPY").then().body("minorUnits", equalTo(0));
    }

    @Test
    void dinarHasThree() {
        request().when().get("/currencies/{code}", "KWD").then().body("minorUnits", equalTo(3));
    }

    @Test
    void unknownCurrency() {
        request().when().get("/currencies/{code}", "XXX").then().statusCode(404);
    }

    @Test
    void target2Holidays() {
        request().when().get("/holidays/{country}", "EU").then().body("dates", hasItem("2026-12-25"));
    }

    @Test
    void kingsDay() {
        request().when().get("/holidays/{country}", "NL").then().body("dates", hasItem("2026-04-27"));
    }

    @Test
    void liberationDay() {
        request().when().get("/holidays/{country}", "NL").then().body("dates", hasItem("2026-05-05"));
    }
}
