package com.example.module_08.section_01;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.module_08.support.CorrelationIdFilter;
import com.example.signing.SignedPaymentsApi;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.RequestSpecification;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 8.1 – a {@link Filter} sees, and may change, every request before it is sent and every response after it
 * arrives.
 * <p>
 * A filter gets the request, the response specification and a {@link FilterContext}. Calling
 * {@code context.next(request, responseSpec)} runs the remaining filters and sends the request; code before that call
 * changes the request, code after it sees the response. Filters can be added per request with {@code filter(..)}, in
 * a specification with {@code addFilter(..)}, or globally with {@code RestAssured.filters(..)}.
 */
class WritingFiltersTest {

    private static SignedPaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = SignedPaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification partnerApi() {
        return given().baseUri(api.baseUri()).port(api.port()).header("X-API-Key", SignedPaymentsApi.PARTNER_API_KEY);
    }

    @Test
    void withoutACorrelationIdTheApiRefuses() {
        partnerApi().when().get("/api/v1/partner/accounts").then().statusCode(400);
    }

    @Test
    void aFilterAddsIt() {
        partnerApi()
            .filter(new CorrelationIdFilter())
        .when()
            .get("/api/v1/partner/accounts")
        .then()
            .statusCode(200)
            .body("$", hasSize(1));
    }

    @Test
    void aFilterCanBeALambda() {
        partnerApi()
            .filter((request, responseSpec, context) -> {
                request.header("X-Correlation-Id", "from-a-lambda");
                return context.next(request, responseSpec);
            })
        .when()
            .get("/api/v1/partner/accounts")
        .then()
            .header("X-Correlation-Id", equalTo("from-a-lambda"));
    }

    @Test
    void aFilterSeesTheResponse() {
        List<Integer> statuses = new ArrayList<>();
        Filter recorder = (request, responseSpec, context) -> {
            Response response = context.next(request, responseSpec);
            statuses.add(response.statusCode());
            return response;
        };

        partnerApi().filter(recorder).when().get("/api/v1/partner/accounts");
        partnerApi().filter(recorder).header("X-Correlation-Id", "x").when().get("/api/v1/partner/accounts");

        assertEquals(List.of(400, 200), statuses);
    }

    @Test
    void aFilterCanFailTheTest() {
        // The legacy endpoint doesn't echo the correlation id, so CorrelationIdFilter's own check fails.
        AssertionError error = assertThrows(AssertionError.class, () ->
            given().baseUri(api.baseUri()).port(api.port())
                .filter(new CorrelationIdFilter())
            .when()
                .get("/api/v1/legacy/fx-rates"));

        assertTrue(error.getMessage().contains("to echo X-Correlation-Id"), error.getMessage());
    }

    @Test
    void orderedFiltersRunInOrderOfTheirValue() {
        List<String> calls = new ArrayList<>();

        partnerApi()
            .filter(new Named("second", 20, calls))                 // added first ...
            .filter(new Named("first", 10, calls))                  // ... but the lower order runs first
            .filter(new CorrelationIdFilter())
        .when()
            .get("/api/v1/partner/accounts");

        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void filtersInASpecification() {
        RequestSpecification spec = new RequestSpecBuilder()
            .setBaseUri(api.baseUri()).setPort(api.port())
            .addHeader("X-API-Key", SignedPaymentsApi.PARTNER_API_KEY)
            .addFilter(new CorrelationIdFilter())
            .build();

        given(spec).when().get("/api/v1/partner/accounts").then().statusCode(200);
    }

    @Test
    void globalFiltersApplyToEveryRequest() {
        RestAssured.filters(new CorrelationIdFilter());
        try {
            partnerApi().when().get("/api/v1/partner/accounts").then().statusCode(200);
        } finally {
            RestAssured.reset();                                     // global state: always undo it
        }
    }

    /** Records its name when it runs. */
    private record Named(String name, int order, List<String> calls) implements OrderedFilter {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                               FilterContext context) {
            calls.add(name);
            return context.next(request, responseSpec);
        }
    }
}
