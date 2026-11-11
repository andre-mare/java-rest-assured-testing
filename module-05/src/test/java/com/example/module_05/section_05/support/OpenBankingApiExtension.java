package com.example.module_05.section_05.support;

import com.example.openbanking.OpenBankingApi;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.UUID;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * A JUnit 6 extension that starts the Open Banking API once per test class and injects ready-made specifications into
 * test methods and constructors (see the JUnit course, Module 6, on {@link ParameterResolver}).
 * <ul>
 *   <li>A {@link RequestSpecification} parameter gets the API's address, a bearer token, JSON, a fresh
 *       {@code x-fapi-interaction-id} for every test, and logging when validation fails.</li>
 *   <li>A {@link ResponseSpecification} parameter gets the expectations of a successful response.</li>
 *   <li>An {@link OpenBankingApi} parameter gets the running API itself.</li>
 * </ul>
 * The API is kept in the class's {@link ExtensionContext.Store}. It implements {@code AutoCloseable}, so JUnit closes
 * it when the class's context is closed, without an {@code AfterAllCallback}.
 */
public class OpenBankingApiExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(OpenBankingApiExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).computeIfAbsent(OpenBankingApi.class, key -> OpenBankingApi.start(), OpenBankingApi.class);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == RequestSpecification.class || type == ResponseSpecification.class || type == OpenBankingApi.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        OpenBankingApi api = context.getStore(NAMESPACE).get(OpenBankingApi.class, OpenBankingApi.class);
        Class<?> type = parameter.getParameter().getType();
        if (type == OpenBankingApi.class) {
            return api;
        }
        if (type == ResponseSpecification.class) {
            return new ResponseSpecBuilder()
                    .expectStatusCode(200)
                    .expectContentType(ContentType.JSON)
                    .build();
        }
        return new RequestSpecBuilder()
                .setBaseUri(api.baseUri())
                .setPort(api.port())
                .setBasePath(OpenBankingApi.BASE_PATH)
                .setAccept(ContentType.JSON)
                .addHeader("Authorization", "Bearer test-token")
                .addHeader("x-fapi-interaction-id", UUID.randomUUID().toString())
                .log(LogDetail.ALL)
                .build();
    }
}
