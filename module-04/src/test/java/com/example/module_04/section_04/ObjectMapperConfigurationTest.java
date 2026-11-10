package com.example.module_04.section_04;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.consents.ConsentsApi;
import com.example.module_04.support.Models.Customer;

import io.restassured.common.mapper.factory.Jackson3ObjectMapperFactory;
import io.restassured.config.RestAssuredConfig;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Module 4.4 – choosing and configuring the object mapper.
 * <p>
 * REST Assured 6 supports Jackson 3 ({@code tools.jackson}) and Jackson 2 ({@code com.fasterxml.jackson}) side by
 * side, plus Gson, JSON-B and JAXB. With several on the classpath it prefers Jackson 3; {@link ObjectMapperType}
 * picks one explicitly.
 * <p>
 * To use your own {@link JsonMapper}, give REST Assured a {@link Jackson3ObjectMapperFactory}. Since REST Assured 6.3
 * that interface lives in {@code io.restassured.common.mapper.factory}; the old one in
 * {@code io.restassured.path.json.mapper.factory} is deprecated. Because {@code jackson3ObjectMapperFactory(..)}
 * accepts both, a bare lambda would be ambiguous, so the factory is assigned to a typed variable first.
 */
class ObjectMapperConfigurationTest {

    private static ConsentsApi api;

    @BeforeAll
    static void startApi() {
        api = ConsentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification consentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(ConsentsApi.BASE_PATH);
    }

    /** Like the record the API should return, but without "email": the strict mapper will notice. */
    record CustomerWithoutEmail(String id, String name, String segment, java.time.Instant createdAt) {
    }

    @Test
    void chooseTheMapperExplicitly() {
        Customer customer = consentsApi()
            .when().get("/customers/{id}", "cust-1")
            .then().extract().as(Customer.class, ObjectMapperType.JACKSON_3);

        assertThat(customer.name()).isEqualTo("Anna de Vries");
    }

    @Test
    void aLenientMapperIgnoresFieldsTheRecordLacks() {
        CustomerWithoutEmail customer = consentsApi()
            .when().get("/customers/{id}", "cust-1")
            .then().extract().as(CustomerWithoutEmail.class);

        assertThat(customer.name()).isEqualTo("Anna de Vries");
    }

    @Test
    void aStrictMapperFailsOnFieldsTheRecordLacks() {
        JsonMapper strict = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        Jackson3ObjectMapperFactory factory = (type, charset) -> strict;
        RestAssuredConfig strictConfig = config().objectMapperConfig(objectMapperConfig().jackson3ObjectMapperFactory(factory));

        assertThatThrownBy(() -> consentsApi()
                .config(strictConfig)
                .when().get("/customers/{id}", "cust-1")
                .then().extract().as(CustomerWithoutEmail.class))
            .hasStackTraceContaining("email");                           // the unknown property is named
    }
}
