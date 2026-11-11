# An OpenAPI Contract Filter for REST Assured: Every Response Checked Against the Spec

An OpenAPI Contract Filter for REST Assured: Check every response against an OpenAPI 3.1 description with a REST Assured filter, covering documented operations, statuses and content types, body schemas with $ref, and the difference between contract tests and functional tests.

Sections 1 and 2 checked responses against schemas, one `body(matcher)` at a time. Most APIs already have a complete contract: their OpenAPI description, which lists every path, every method, every status and the schema of every body. Writing a schema check in each test duplicates that work, and a test that forgets one checks nothing. What if every response were checked against the OpenAPI description automatically, without a single schema check in the tests?

This post follows [Section 4 of Module 6](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-06) of the REST Assured course. It puts a contract filter in a request specification, so every request the tests send is checked against the OpenAPI file. The examples are in [`OpenApiContractTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_04/OpenApiContractTest.java) and [`OpenApiContractFilter`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/support/OpenApiContractFilter.java).

## What is a Contract Filter?

A REST Assured `Filter` runs around every request it is added to. It can change the request, look at the response, or fail the test. Module 8 covers filters in depth. This one checks each response in three steps:

1. **The operation**: the request's method and path must be in the OpenAPI description.
2. **The status**: the response status must be documented for that operation, or a `default` response must exist.
3. **The body**: the response content type must be documented for that status, and the body must match its schema.

OpenAPI 3.1 schemas are JSON Schema 2020-12, so the filter validates bodies with the same `matchesJsonSchema(..)` as [Section 1](module-06-section-01-rest-assured-json-schema-validation.md).

## The OpenAPI Description

[`payment-initiation.openapi.json`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/resources/openapi/payment-initiation.openapi.json) is an OpenAPI 3.1 description of the payment initiation API. Its server URL is `/pisp/v1`, and it documents two operations:

| Operation | Documented responses |
| --- | --- |
| `POST /domestic-payments` | `201`, `400`, `415`, `422` |
| `GET /domestic-payments/{paymentId}` | `200`, `404` |

The schemas live under `components/schemas`: `PaymentRequest`, `Payment`, `Party`, `Amount` and `Problem`. Responses refer to them with `$ref`, such as `#/components/schemas/Payment`.

## How the Filter Works

The filter's `filter(..)` method sends the request first, then checks what came back (shortened):

```java
@Override
public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                       FilterContext context) {
    Response response = context.next(request, responseSpec);
    String method = request.getMethod().toLowerCase(Locale.ROOT);
    String apiPath = URI.create(request.getURI()).getPath().substring(serverPath.length());
    String template = findTemplate(apiPath);

    Map<String, Object> operation = map(map(map(document.get("paths")).get(template)).get(method));
    if (operation.isEmpty()) {
        throw new AssertionError(method.toUpperCase(Locale.ROOT) + " " + template + " is not in the OpenAPI description");
    }

    Map<String, Object> responses = map(operation.get("responses"));
    Object documented = responses.getOrDefault(String.valueOf(response.statusCode()), responses.get("default"));
    if (documented == null) {
        throw new AssertionError("Status " + response.statusCode() + " is not documented for ...");
    }

    ...

    Map<String, Object> schema = new LinkedHashMap<>(map(media.get("schema")));
    schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.put("components", document.get("components"));
    assertThat(method.toUpperCase(Locale.ROOT) + " " + template + " " + response.statusCode() + " body",
            response.asString(), matchesJsonSchema(MAPPER.writeValueAsString(schema)));
    return response;
}
```

- **`context.next(..)`** sends the request and returns the response. Everything after it is a check.
- **`findTemplate(..)`** matches the real path, such as `/domestic-payments/pmt-1`, to a template, such as `/domestic-payments/{paymentId}`.
- **`resolve(..)`**, left out here, follows a `$ref` such as `#/components/responses/Problem`.
- **Copying `components`** into the validated schema is what keeps a `$ref` such as `#/components/schemas/Party` working: the reference points into the same document.
- **The reason** passed to `assertThat(..)`, such as `GET /domestic-payments/{paymentId} 200 body`, starts the failure message, so you know which operation broke its contract.

This is a teaching version that handles the parts of OpenAPI this module uses. For real projects, `com.atlassian.oai:openapi-request-validator-restassured` (formerly `swagger-request-validator-restassured`) is a complete filter of this kind, which also checks requests. Check that the version you choose supports REST Assured 6.

## Using the Filter

### Step 1: Put It in the Specification

```java
@BeforeAll
static void startApi() {
    api = PaymentInitiationApi.start();
    pisp = new RequestSpecBuilder()
        .setBaseUri(api.baseUri()).setPort(api.port()).setBasePath(PaymentInitiationApi.BASE_PATH)
        .addFilter(OpenApiContractFilter.fromClasspath("openapi/payment-initiation.openapi.json"))
        .build();
}
```

Every request sent with `given(pisp)` now passes through the filter.

### Step 2: Normal Flows Pass

```java
@Test
void initiateAndReadAPayment() {
    String id = given(pisp)
        .contentType(ContentType.JSON).body(domesticPayment("INV-10", "99.95"))
        .when().post("/domestic-payments")
        .then().statusCode(201)
        .extract().path("paymentId");

    given(pisp).when().get("/domestic-payments/{id}", id).then().statusCode(200);
}
```

The test checks only behaviour: the payment can be created and read back. It contains no schema check, yet both responses were validated against the `Payment` schema by the filter.

### Step 3: Documented Errors Pass Too

```java
@Test
void documentedErrorsPassToo() {
    given(pisp).when().get("/domestic-payments/{id}", "pmt-404").then().statusCode(404);
    given(pisp).contentType(ContentType.JSON).body("{}").when().post("/domestic-payments").then().statusCode(422);
}
```

A `404` and a `422` are part of the contract, so the filter checks their bodies against the `Problem` schema and lets them through.

> **The code above, ready to run**
>
> All four tests in [`OpenApiContractTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_04/OpenApiContractTest.java) pass with `mvn test -pl module-06 -Dtest=OpenApiContractTest`. Remove `"404"` from the OpenAPI file and run `documentedErrorsPassToo` to see an undocumented status fail.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 6 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/README.md)

## What the Filter Catches

### A Body That Breaks the Contract

```java
@Test
void aBodyThatBreaksTheContractFailsTheTest() {
    String id = given(pisp)
        .contentType(ContentType.JSON).body(domesticPayment("INV-11", "1.00"))
        .when().post("/domestic-payments")
        .then().extract().path("paymentId");

    AssertionError error = assertThrows(AssertionError.class, () ->
        given(pisp).queryParam("format", "v0").when().get("/domestic-payments/{id}", id));
    assertThat(error.getMessage(), containsString("GET /domestic-payments/{paymentId} 200 body"));
}
```

The request has no `then()` at all, and it still fails. The filter runs while the request is sent, so `get(..)` itself throws:

```text
GET /domestic-payments/{paymentId} 200 body
Expected: The content to match the given JSON schema.
$.status: does not have a value in the enumeration ["RCVD", "PDNG", "ACSP", "ACSC", "RJCT"]
$.amount: number found, object expected
$: required property 'instructionId' not found
...
```

### An Undocumented Operation

```java
@Test
void anUndocumentedOperationFailsTheTest() {
    AssertionError error = assertThrows(AssertionError.class, () ->
        given(pisp).when().get("/domestic-payments"));            // listing payments isn't in the contract
    assertThat(error.getMessage(), containsString("GET /domestic-payments is not in the OpenAPI description"));
}
```

The path exists, but only for `POST`. Whatever the API answers, a client that relies on `GET /domestic-payments` relies on something the contract never promised. The filter turns that into a failing test.

## Contract Tests and Functional Tests

The two kinds of test answer different questions:

| | Contract test | Functional test |
| --- | --- | --- |
| Question | Does the API keep its promises? | Does it do the right thing? |
| Checks | Operations, statuses, content types, shapes | Values, state changes, business rules |
| Example | The payment has every required field | The amount of the payment is the amount sent |

A response can match its schema and still hold the wrong amount, and a correct amount can arrive in the wrong shape. A good suite has both. The filter makes the contract checks free, so the tests themselves can focus on behaviour.

## Common Pitfalls

1. **Expecting the failure in `then()`.** The filter fails inside `get(..)` or `post(..)`. Wrap the request itself in `assertThrows(..)`, as these tests do.
2. **Endpoints that aren't in the description.** `POST /payment-files` isn't in this OpenAPI file, so the filter fails it with `/payment-files is not a path in the OpenAPI description`. Add the filter only to specifications for documented endpoints, or document them.
3. **Breaking `$ref`s by validating one schema alone.** A `Payment` schema that refers to `#/components/schemas/Party` can't be resolved on its own. Keep `components` in the validated schema.
4. **An OpenAPI file that drifts from the real one.** A copy in `src/test/resources` can get out of date. Load the file the API publishes, or generate both from one source.
5. **Contract checks only.** A filter proves shapes, not correctness. Keep the functional checks in the tests.

## Wrapping Up

A contract filter checks every response against the API's OpenAPI description: the operation must exist, the status must be documented, and the body must match its schema. Because OpenAPI 3.1 schemas are JSON Schema 2020-12, REST Assured's `matchesJsonSchema(..)` does the body check, and copying `components` keeps `$ref`s working. Put the filter in a request specification, and every test gets contract checks for free, while the tests themselves check behaviour. For real projects, use a complete filter such as Atlassian's `openapi-request-validator-restassured`.
