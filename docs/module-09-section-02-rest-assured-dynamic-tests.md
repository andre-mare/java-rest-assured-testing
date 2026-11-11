# Dynamic API Tests With REST Assured: @TestFactory and dynamicContainer

Dynamic API Tests With REST Assured: Generate tests at run time with JUnit's @TestFactory, so every endpoint in a list gets the same security checks, and group them per endpoint with dynamicContainer.

[Module 7](module-07-section-03-rest-assured-negative-security-tests.md) tested that every endpoint rejects a missing token, with a parameterized test and a row per endpoint. Now add a second check, a wrong token, and a third, Basic credentials instead of a bearer token. That is three parameterized tests with the same six rows, or one test that does three checks and stops at the first failure. Neither is right. What you want is a list of endpoints and a list of checks, and every combination as its own test, named and grouped in the report.

This post follows [Section 2 of Module 9](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-09) of the REST Assured course. It generates the security tests for every endpoint at run time. The example is in [`DynamicTestsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_02/DynamicTestsTest.java). Dynamic tests are covered in depth in Module 8 of the [JUnit course](https://github.com/andre-mare/java-unit-testing).

## What is a Dynamic Test?

A `@TestFactory` method doesn't test anything itself. It returns tests:

1. **`dynamicTest(name, executable)`** creates one test: a display name and a lambda.
2. **`dynamicContainer(name, children)`** groups tests, or other containers, under a name.
3. **The factory** returns a `Stream`, `Collection` or `Iterable` of these nodes, built from any data, at run time.

A parameterized test has a fixed shape: one method, many arguments. A test factory can build any tree of tests from any data, such as endpoints × checks.

## The Endpoints Under Test

The payment lifecycle API from [Section 1](module-09-section-01-rest-assured-parameterized-api-tests.md) has six operations, and all of them need `Authorization: Bearer test-token`. Without it they answer `401` with `WWW-Authenticate: Bearer realm="payments"`.

The test describes each endpoint with a small record:

```java
private record Endpoint(Method method, String path) {
    String label() {
        return method + " " + path;
    }
}
```

Some paths need an existing payment, so `@BeforeAll` creates one and builds the list:

```java
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
```

The requests must not carry the module's default token, so they start from a bare request:

```java
private static RequestSpecification withoutDefaults() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentLifecycleApi.BASE_PATH);
}
```

## Generating the Tests

```java
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
```

- **One container per endpoint**, named `GET /payments`, `POST /payments` and so on.
- **Three tests per container**, one per check.
- **`request(method, path)`**, from [Module 2](module-02-section-05-rest-assured-http-methods.md), sends whichever method the endpoint has.

Six endpoints and three checks make 18 tests. In the IDE, the report reads like a checklist:

```text
everyEndpointIsProtected()
├─ GET /payments
│  ├─ rejects a request without a token
│  ├─ rejects a wrong token
│  └─ rejects Basic credentials
├─ POST /payments
│  ├─ ...
```

When one fails, its name says which endpoint and which check. The other 17 still run.

> **The code above, ready to run**
>
> All 18 generated tests in [`DynamicTestsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_02/DynamicTestsTest.java) pass with `mvn test -pl module-09 -Dtest=DynamicTestsTest`. Run the class in your IDE to see the tree of containers.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 9 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/README.md)

## Why Generate Tests?

The list of endpoints is data. Add an endpoint to it and it gets every check, so a new endpoint can't be forgotten. Add a check and every endpoint gets it. The test code never changes shape.

In a larger suite, the list doesn't have to be written by hand. It can come from the OpenAPI description used in [Module 6](module-06-section-04-rest-assured-openapi-contract-filter.md), so every documented operation is tested for authentication automatically.

## Parameterized or Dynamic?

| | `@ParameterizedTest` | `@TestFactory` |
| --- | --- | --- |
| Shape | One method, many arguments | Any tree of tests |
| Data | Annotations, fields or methods | Any code, at run time |
| Lifecycle | `@BeforeEach` runs per invocation | `@BeforeEach` runs once for the whole factory |
| Best for | A table of inputs for one check | Combinations, or tests built from external data |

## Common Pitfalls

1. **Expecting `@BeforeEach` per dynamic test.** It runs once, for the factory method. Set up what each dynamic test needs inside its lambda.
2. **Default credentials in the request.** A shared specification with a token would make every "without a token" test send one. Start from a bare request.
3. **Endpoints that need data.** A path such as `/payments/{id}/execution` needs a real id. Without it, the test may get `404` before authentication is even checked, which proves nothing. Create the data first.
4. **One test with several checks.** It stops at the first failure and hides the rest. Generate one test per check.
5. **A hand-written list that drifts.** A new endpoint added to the API but not to the list isn't tested. Generate the list from the API's description, or check that it covers every route.

## Wrapping Up

A `@TestFactory` returns tests instead of running checks: `dynamicTest(..)` for each test, and `dynamicContainer(..)` to group them. Built from a list of endpoints and a list of checks, it gives every endpoint every security test, each with its own name in the report. Add an endpoint and it is covered automatically. Start such tests from a bare request, create the data that paths need, and remember that `@BeforeEach` runs once per factory, not per dynamic test.
