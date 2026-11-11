# Writing Filters in REST Assured: Filter, FilterContext and OrderedFilter

Writing Filters in REST Assured: Change every request and see every response in one place, covering a filter class, a lambda filter, a filter that records responses, a filter that fails a test, OrderedFilter, filters in a specification, and global filters.

Some things must happen on every request: a correlation id, a signature, a log line, a check that the response echoes what you sent. Writing them into each test means fifty copies, and the one test that forgets is the one that matters. Specifications from [Module 5](module-05-section-01-rest-assured-request-specifications.md) hold fixed values, but they can't compute a new value per request, or look at the response. A filter can do both.

This post is the first in the series for [Module 8 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-08), which covers changing every request and response in one place. It writes filters, orders them, and puts them where every request gets them. The examples are in [`WritingFiltersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_01/WritingFiltersTest.java) and [`CorrelationIdFilter`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/support/CorrelationIdFilter.java).

## What is a Filter?

A `Filter` is an interface with one method:

```java
Response filter(FilterableRequestSpecification request,
                FilterableResponseSpecification responseSpec,
                FilterContext context);
```

1. **`request`** is the request about to be sent. The filter can read it and change it: headers, body, parameters.
2. **`context.next(request, responseSpec)`** runs the remaining filters and sends the request. It returns the response.
3. **The return value** is the response REST Assured goes on with. Usually that is the response from `context.next(..)`, but a filter may return a different one.

So code **before** `context.next(..)` changes the request, and code **after** it sees the response. Filters can be added in three places:

| Where | How | Applies to |
| --- | --- | --- |
| One request | `given().filter(..)` | That request |
| A specification | `RequestSpecBuilder.addFilter(..)` | Every request that uses the specification |
| Globally | `RestAssured.filters(..)` | Every request, until `RestAssured.reset()` |

## The API Under Test

Module 8 tests a signed payments API. This section uses its partner endpoint and its legacy endpoint:

| Request | Rules |
| --- | --- |
| `GET /api/v1/partner/accounts` | Needs `X-API-Key: partner-key-123` and an `X-Correlation-Id`, which it echoes in the response |
| `GET /api/v1/legacy/fx-rates` | No rules, and it doesn't echo the correlation id |

Without a correlation id, the partner endpoint refuses:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"The header X-Correlation-Id is required","instance":"/api/v1/partner/accounts"}
```

With one, it returns `[{"id":"acc-1001","iban":"NL91ABNA0417164300"}]`.

## A Correlation Id Filter

```java
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
```

The filter does two jobs that every test would otherwise repeat:

1. **Before sending**, it adds a fresh correlation id, unless the test set one.
2. **After the response**, it checks that the API echoed it, and fails the test if not.

## Using Filters

### Step 1: Without the Filter, With the Filter

```java
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
```

`partnerApi()` sets the base URI, port and API key. The filter adds the rest.

### Step 2: A Filter Can Be a Lambda

```java
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
```

`Filter` is a functional interface, so a short filter fits in a lambda.

### Step 3: A Filter Sees the Response

```java
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
```

The same filter instance records the status of both requests. That is the basis of the timing and logging filters in [Section 2](module-08-section-02-rest-assured-useful-filters.md).

### Step 4: A Filter Can Fail the Test

```java
@Test
void aFilterCanFailTheTest() {
    AssertionError error = assertThrows(AssertionError.class, () ->
        given().baseUri(api.baseUri()).port(api.port())
            .filter(new CorrelationIdFilter())
        .when()
            .get("/api/v1/legacy/fx-rates"));
    assertTrue(error.getMessage().contains("to echo X-Correlation-Id"), error.getMessage());
}
```

The legacy endpoint doesn't echo the correlation id, so the filter's own check fails. The error is thrown inside `get(..)`, before any `then()`. A filter is the right place for a check that must hold for every response, and that no test should have to remember.

> **The code above, ready to run**
>
> All eight tests in [`WritingFiltersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_01/WritingFiltersTest.java) pass with `mvn test -pl module-08 -Dtest=WritingFiltersTest`. You need JDK 26 and Maven 3.9, and nothing else.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 8 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/README.md)

## The Order of Filters

Ordinary filters run in the order they were added. When the order matters, implement `OrderedFilter` and return an order from `getOrder()`: **lower values run first**, and ordinary filters count as `1000`.

```java
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
```

Order matters most for a filter that must see the final request, such as a signer. [Section 3](module-08-section-03-rest-assured-signing-requests.md) gives it an order near `OrderedFilter.LOWEST_PRECEDENCE`, so it runs last.

## Where to Put Filters

### In a Specification

```java
@Test
void filtersInASpecification() {
    RequestSpecification spec = new RequestSpecBuilder()
        .setBaseUri(api.baseUri()).setPort(api.port())
        .addHeader("X-API-Key", SignedPaymentsApi.PARTNER_API_KEY)
        .addFilter(new CorrelationIdFilter())
        .build();

    given(spec).when().get("/api/v1/partner/accounts").then().statusCode(200);
}
```

This is usually the right place: every test that uses the specification gets the filter, and nothing is global.

### Globally

```java
@Test
void globalFiltersApplyToEveryRequest() {
    RestAssured.filters(new CorrelationIdFilter());
    try {
        partnerApi().when().get("/api/v1/partner/accounts").then().statusCode(200);
    } finally {
        RestAssured.reset();                                     // global state: always undo it
    }
}
```

`RestAssured.filters(..)` adds to a static list that every request uses. It is global state, with the same risks as the static defaults in [Module 1](module-01-section-03-where-rest-assured-requests-go.md): undo it with `RestAssured.reset()` in a `finally` block.

## Common Pitfalls

1. **Forgetting `context.next(..)`.** The request is never sent, and the filter returns `null`, so the test fails with a `NullPointerException` on `then()`.
2. **Changing the request after `context.next(..)`.** It has already been sent. Change it before.
3. **Global filters that pile up.** Each `RestAssured.filters(..)` call adds to the list, so calling it twice runs the filter twice. Prefer specifications, and always `reset()` global filters.
4. **Relying on the order of ordinary filters across a specification and a request.** When the order matters, make it explicit with `OrderedFilter`.
5. **Checks in a filter that some responses can't pass.** A filter in a shared specification runs for every endpoint, like the legacy one here. Apply it only to endpoints that keep the rule.

## Wrapping Up

A filter sees every request before it is sent and every response after it arrives. Code before `context.next(request, responseSpec)` changes the request, and code after it sees the response, and may fail the test. Filters can be classes or lambdas, added per request, in a specification or globally. `OrderedFilter` makes the order explicit: lower values run first, and ordinary filters count as 1000. Put shared filters in a specification, and undo global ones with `RestAssured.reset()`.
