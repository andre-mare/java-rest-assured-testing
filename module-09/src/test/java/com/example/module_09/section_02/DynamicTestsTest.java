package com.example.module_09.section_02;

import static com.example.module_09.support.PaymentsClient.createPayment;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.example.lifecycle.PaymentLifecycleApi;

import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

/**
 * Module 9.2 – tests generated at run time: one security test per endpoint, from a list.
 * <p>
 * A {@code @TestFactory} returns dynamic tests (see Module 8 of the JUnit course). Add an endpoint to the list and it
 * gets every check below, so a new endpoint can't be forgotten. A {@code dynamicContainer} groups the tests per
 * endpoint in the report.
 */
class DynamicTestsTest {

    private record Endpoint(Method method, String path) {
        String label() {
            return method + " " + path;
        }
    }

    private static PaymentLifecycleApi api;
    private static List<Endpoint> endpoints;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
        String id = createPayment(api, "10.00");
        endpoints = List.of(
            new Endpoint(Method.GET, "/payments"),
            new Endpoint(Method.POST, "/payments"),
            new Endpoint(Method.GET, "/payments/" + id),
            new Endpoint(Method.PATCH, "/payments/" + id),
            new Endpoint(Method.POST, "/payments/" + id + "/authorisation"),
            new Endpoint(Method.POST, "/payments/" + id + "/execution"));
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification withoutDefaults() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentLifecycleApi.BASE_PATH);
    }

    @TestFactory
    Stream<DynamicNode> everyEndpointIsProtected() {
        return endpoints.stream().map(endpoint -> dynamicContainer(endpoint.label(), Stream.of(
            dynamicTest("rejects a request without a token", () ->
                withoutDefaults()
                    .when().request(endpoint.method(), endpoint.path())
                    .then().statusCode(401).header("WWW-Authenticate", "Bearer realm=\"payments\"")),
            dynamicTest("rejects a wrong token", () ->
                withoutDefaults()
                    .auth().oauth2("not-the-token")
                    .when().request(endpoint.method(), endpoint.path())
                    .then().statusCode(401)),
            dynamicTest("rejects Basic credentials", () ->
                withoutDefaults()
                    .auth().preemptive().basic("admin", "admin")
                    .when().request(endpoint.method(), endpoint.path())
                    .then().statusCode(401))
        )));
    }
}
