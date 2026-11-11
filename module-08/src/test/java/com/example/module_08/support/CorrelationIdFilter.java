package com.example.module_08.support;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.UUID;

/**
 * Adds an {@code X-Correlation-Id} to every request that doesn't have one, and fails the test when the response
 * doesn't echo it. One filter replaces a line in every test and a check that tests would forget.
 */
public final class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        if (!request.getHeaders().hasHeaderWithName(HEADER)) {
            request.header(HEADER, UUID.randomUUID().toString());
        }
        String sent = request.getHeaders().getValue(HEADER);

        Response response = context.next(request, responseSpec);     // send the request (and run later filters)

        if (!sent.equals(response.header(HEADER))) {
            throw new AssertionError("Expected the response to echo " + HEADER + " " + sent
                    + " but it was " + response.header(HEADER));
        }
        return response;
    }
}
