# A JUnit Extension for REST Assured: Injected Specifications Instead of Static State

A JUnit Extension for REST Assured: Replace static configuration and set-up code with a JUnit 6 extension that starts the API once per class, injects ready-made request and response specifications into test methods, and closes the API through the extension store.

Every test class so far starts the same way: a static field for the API, `@BeforeAll` to start it, `@AfterAll` to stop it, and a helper or specification to point requests at it. That is ten lines of set-up per class, copied into every class. The alternative many suites reach for, static defaults on `RestAssured`, removes the copying but adds global state, which breaks the moment classes run in parallel. A JUnit extension removes both.

This post follows [Section 5 of Module 5](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-05) of the REST Assured course. It builds an extension that starts the API and hands each test its own specifications as method parameters. The examples are in [`InjectedSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_05/InjectedSpecificationTest.java), [`OpenBankingApiExtension`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_05/support/OpenBankingApiExtension.java) and [`OpenBankingApiTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_05/support/OpenBankingApiTest.java). Extensions and parameter resolvers are covered in depth in Module 6 of the [JUnit course](https://github.com/andre-mare/java-unit-testing).

## What Does the Extension Do?

`OpenBankingApiExtension` implements two JUnit extension points:

1. **`BeforeAllCallback`**: before the first test of a class, it starts the API and keeps it in the class's extension store.
2. **`ParameterResolver`**: when a test method or constructor has a parameter of a supported type, it supplies the value.

It supports three parameter types:

| Parameter type | What the test gets |
| --- | --- |
| `RequestSpecification` | The API's address, a bearer token, JSON, a fresh `x-fapi-interaction-id` and request logging |
| `ResponseSpecification` | The expectations of a successful response: `200` and JSON |
| `OpenBankingApi` | The running API itself |

The test class itself has no fields, no lifecycle methods and no static state.

## The Test Class

```java
@OpenBankingApiTest
class InjectedSpecificationTest {

    @Test
    void listAccounts(RequestSpecification openBanking, ResponseSpecification ok) {
        given(openBanking)
        .when()
            .get("/accounts")
        .then()
            .spec(ok)
            .body("Data.Account", hasSize(3));
    }

    @Test
    void readOneAccount(RequestSpecification openBanking, ResponseSpecification ok) {
        given(openBanking)
        .when()
            .get("/accounts/{id}", "40114")
        .then()
            .spec(ok)
            .body("Data.Account[0].AccountType", equalTo("Business"));
    }
}
```

Compare this with [Module 1](module-01-section-01-your-first-rest-assured-request.md): the set-up has gone, and each test says only what it is about. The parameter names, `openBanking` and `ok`, are free to choose. The extension looks only at the types.

## Building the Extension

### Step 1: A Composed Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenBankingApiExtension.class)
@Tag("api")
public @interface OpenBankingApiTest {
}
```

`@OpenBankingApiTest` combines the extension and a tag in one annotation. A test class needs one line of set-up, and every API test can be selected or excluded by the `api` tag. Module 12 uses tags to split a build.

### Step 2: Start the API Once per Class

```java
private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(OpenBankingApiExtension.class);

@Override
public void beforeAll(ExtensionContext context) {
    context.getStore(NAMESPACE).computeIfAbsent(OpenBankingApi.class, key -> OpenBankingApi.start(), OpenBankingApi.class);
}
```

- **The store** belongs to the test class's context, so each class gets its own API.
- **`computeIfAbsent(key, creator, type)`** starts the API only if the store doesn't have one yet. It is the JUnit 6 name; `getOrComputeIfAbsent(..)` is deprecated.
- **There is no `AfterAllCallback`.** `OpenBankingApi` implements `AutoCloseable`, and JUnit closes `AutoCloseable` values in the store when the class's context is closed. The API stops by itself after the last test of the class.

### Step 3: Resolve the Parameters

```java
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
```

- **`supportsParameter(..)`** tells JUnit which parameters this extension can fill.
- **`resolveParameter(..)`** builds a new specification for every parameter it resolves. The store lookup finds the API that `beforeAll(..)` started, because a method's context can see the stores of its class.
- **A fresh interaction id** goes into every request specification, which [Section 1](module-05-section-01-rest-assured-request-specifications.md) couldn't do with one shared specification.

> **The code above, ready to run**
>
> All three tests in [`InjectedSpecificationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_05/InjectedSpecificationTest.java) pass with `mvn test -pl module-05 -Dtest=InjectedSpecificationTest`. Add a second test class with `@OpenBankingApiTest` and it gets its own API on its own port.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 5 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/README.md)

## Every Test Gets Its Own Specification

```java
@Test
void everyTestGetsItsOwnInteractionId(RequestSpecification first, RequestSpecification second) {
    String firstId = SpecificationQuerier.query(first).getHeaders().getValue("x-fapi-interaction-id");
    String secondId = SpecificationQuerier.query(second).getHeaders().getValue("x-fapi-interaction-id");

    assertNotEquals(firstId, secondId);
}
```

Each parameter is resolved separately, so even two parameters in one test get different specifications. `SpecificationQuerier` from Section 1 reads the header back.

Because nothing is static, test classes don't share any REST Assured state. That is what makes it safe to run them in parallel, which Module 12 does.

## About the Logging

`.log(LogDetail.ALL)` on a `RequestSpecBuilder` logs **every** request made with the specification, whether the test passes or fails, and that log includes the `Authorization` header with the token in full:

```text
Headers:		Accept=application/json, application/javascript, text/javascript, text/json
				Authorization=Bearer test-token
				x-fapi-interaction-id=7cbc9bbd-3741-4568-b5f9-7f4647759379
```

That is fine for a course API with a fixed test token. For a real suite, apply what [Section 4](module-05-section-04-rest-assured-secrets-in-logs.md) teaches: log only when validation fails, and blacklist the secret headers in the specification's config, so every test that uses the extension gets both.

## Common Pitfalls

1. **Sharing one specification between tests.** Per-request values such as the interaction id are then the same for every test. Build a new specification in `resolveParameter(..)`.
2. **Static fields in the extension.** They are shared by every class that uses it, and break parallel runs. Keep state in the extension store.
3. **Stopping the API in an `AfterAllCallback` as well.** The store already closes `AutoCloseable` values. Closing twice is harmless here, but the extra code isn't needed.
4. **`getOrComputeIfAbsent(..)`.** It still works, but it is deprecated in JUnit 6. Use `computeIfAbsent(key, creator, type)`.
5. **Logging everything from a shared specification.** Every test then logs its requests and tokens, even when it passes. Log on failure, and blacklist secrets.

## Wrapping Up

A JUnit extension replaces static REST Assured configuration and repeated set-up code. `BeforeAllCallback` starts the API once per class and keeps it in the extension store, which closes it automatically because it is `AutoCloseable`. `ParameterResolver` gives every test method its own request and response specifications, including per-request values such as an interaction id. A composed annotation turns it all into one line per test class, and because no state is static, test classes can run in parallel.
