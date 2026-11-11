# REST Assured Learning Outline

This outline shows everything you will learn in this repository and which module teaches it. There are twelve modules, from a first `given().when().then()` to request signing, WireMock, Spring Boot and real OAuth 2.0 tokens from Keycloak. Each topic is a runnable test against a real HTTP API, so you can learn at your own pace.

REST Assured is a Java library for testing HTTP APIs. You describe a request, send it, and check the response in one readable chain. It runs inside JUnit, so everything from the [JUnit 6 course](https://github.com/andre-mare/java-unit-testing) (lifecycle methods, nested tests, parameterized tests, extensions, tags) applies here too.

## Table of Contents

- [How to use this outline](#how-to-use-this-outline)
- [The modules at a glance](#the-modules-at-a-glance)
- [Beginner](#beginner)
  - [Module 1: Foundations](#module-1-foundations)
  - [Module 2: Building Requests](#module-2-building-requests)
  - [Module 3: Validating Responses in Depth](#module-3-validating-responses-in-depth)
- [Intermediate](#intermediate)
  - [Module 4: Extracting Values and Mapping Objects](#module-4-extracting-values-and-mapping-objects)
  - [Module 5: Reusable Specifications and Configuration](#module-5-reusable-specifications-and-configuration)
  - [Module 6: Schema and Contract Validation](#module-6-schema-and-contract-validation)
  - [Module 7: Authentication and Transport Security](#module-7-authentication-and-transport-security)
- [Advanced](#advanced)
  - [Module 8: Filters and Extensibility](#module-8-filters-and-extensibility)
  - [Module 9: Data-Driven Tests and API Workflows](#module-9-data-driven-tests-and-api-workflows)
  - [Module 10: Stubbing Dependencies with WireMock](#module-10-stubbing-dependencies-with-wiremock)
  - [Module 11: Spring Boot Integration](#module-11-spring-boot-integration)
  - [Module 12: Real Infrastructure with Testcontainers](#module-12-real-infrastructure-with-testcontainers)
- [Appendix A: Cheat sheet](#appendix-a-cheat-sheet)
- [Appendix B: Common pitfalls](#appendix-b-common-pitfalls)
- [Appendix C: REST Assured 6 and the road to 7](#appendix-c-rest-assured-6-and-the-road-to-7)
- [Resources](#resources)

## How to use this outline

Work through the modules in order, or go straight to the topic you need. The modules build on each other conceptually, but they share no code, so any of them can be run on its own.

For each section:

1. Read the section in the module's README, which explains its concepts and test classes.
2. Read the test classes, and run them with `mvn test` or from your IDE.
3. Change the code or the tests, predict what will happen, and read the output. REST Assured's failure messages and `log()` output are part of what you are learning.

**Every module tests a real HTTP server.** Each module has a small API under `src/main/java`, written for that module and started by the tests on a free port. Beginner modules use the JDK's built-in HTTP server, so there is nothing to install. Later modules use WireMock, Spring Boot and Docker (through Testcontainers) where the topic needs them.

**What you need**

- JDK 26 or later, and Maven 3.9 or later
- JUnit 6.1.3, REST Assured 6.3.0 and Jackson 3, which the build already sets up
- Docker, for Module 12 only

## The modules at a glance

| Module | Level | What you will learn | Practised on |
| --- | --- | --- | --- |
| [1. Foundations](module-01/README.md) | Beginner | given/when/then, where requests go, first assertions, logging, reading failures | An accounts API |
| [2. Building Requests](module-02/README.md) | Beginner | Path, query and form parameters, headers, cookies, bodies, every HTTP method, multipart uploads | A payments API |
| [3. Validating Responses in Depth](module-03/README.md) | Beginner to intermediate | GPath for JSON and XML, Hamcrest matchers, root paths, numbers and money, cookies, response-aware matchers | Transactions and bank statements |
| [4. Extracting Values and Mapping Objects](module-04/README.md) | Intermediate | `extract()`, `JsonPath`, mapping to records, generics with `TypeRef`, Jackson 3, chaining requests | Customers and consents |
| [5. Reusable Specifications and Configuration](module-05/README.md) | Intermediate | Request and response specifications, `RestAssuredConfig`, timeouts, redirects, hiding secrets in logs, a JUnit extension for your API | An open banking accounts API |
| [6. Schema and Contract Validation](module-06/README.md) | Intermediate | JSON Schema (draft 2020-12), XSD, RFC 9457 errors, OpenAPI validation | Payment initiation |
| [7. Authentication and Transport Security](module-07/README.md) | Intermediate to advanced | Basic, bearer tokens, the client credentials grant, negative security tests, TLS, mutual TLS | An OAuth 2.0 resource server |
| [8. Filters and Extensibility](module-08/README.md) | Advanced | Custom filters, request signing, masking logs, custom authentication schemes, parsers and failure listeners | Signed payment requests |
| [9. Data-Driven Tests and API Workflows](module-09/README.md) | Advanced | Parameterized API tests, dynamic tests, multi-step workflows, idempotency, ETags, pagination, polling | The payment lifecycle |
| [10. Stubbing Dependencies with WireMock](module-10/README.md) | Advanced | Stubbing third-party APIs, fault injection, verifying outgoing calls | A payment service that calls an FX rate provider |
| [11. Spring Boot Integration](module-11/README.md) | Advanced | `RestAssuredMockMvc`, Spring Security test support, full server tests on a random port | A Spring Boot accounts service |
| [12. Real Infrastructure with Testcontainers](module-12/README.md) | Advanced | Real tokens from Keycloak, parallel execution, tags and Maven profiles | An OAuth-protected API end to end |

---

# Beginner

## Module 1: Foundations

You will learn how a REST Assured test is put together, where its requests go, how to check a response, and how to read what happens when a check fails.

The smallest useful test needs a running API, `given()` to describe the request, `when()` to send it and `then()` to check the response:

```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class FirstRequestTest {
    @Test
    void healthEndpointReportsThatItIsUp() {
        given()
            .baseUri("http://localhost")
            .port(port)
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}
```

1. **1.1 Your first request** ([guide](https://github.com/rest-assured/rest-assured/wiki/GettingStarted))
   - Starting and stopping an API under test with `@BeforeAll` and `@AfterAll`
   - given / when / then as Arrange / Act / Assert
   - `statusCode(..)` and a first `body(..)` check
2. **1.2 The anatomy of a test** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#note-on-syntax))
   - `RequestSpecification`, `Response` and `ValidatableResponse`
   - Syntactic sugar: `and()`, leaving out `given()`, the one-line `get(url).then()`
   - Keeping a `Response` and checking it later
   - A first look at path parameters: `get("/accounts/{id}", "acc-1001")`
3. **1.3 Where requests go** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#default-values))
   - `baseUri`, `port`, `basePath` and the path, and how they combine into a URL
   - Absolute URLs
   - Static defaults on `RestAssured`, why they are global state, and `RestAssured.reset()`
4. **1.4 First assertions** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#verifying-response-data))
   - Status code and status line, with values or Hamcrest matchers
   - `contentType(ContentType.JSON)` versus a content type string, and why `application/problem+json` counts as JSON
   - Headers, and why header names are case-insensitive
   - Body paths: single values, several paths in one `body(..)` call, list indexes (including negative ones), `size()`, collecting a field from every element
   - Numbers are read as `Float` by default
   - Response time, and why it is only a sanity check
5. **1.5 Logging** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#logging))
   - `log().all()`, and logging only the method, URI, status, headers or body
   - `log().ifValidationFails()` on the request and the response
   - `log().ifError()` and `log().ifStatusCodeIsEqualTo(..)`
6. **1.6 Reading failures**
   - The shape of a failure message: "N expectations failed", then one paragraph per failure
   - Eager validation: after `then()` each call is checked immediately, so a chain stops at its first failure, while all paths in one `body(..)` call are reported together
   - A class of deliberately failing tests, excluded from the default build with a tag

## Module 2: Building Requests

You will learn every way to put data into a request, and how to send every HTTP method.

```java
given()
    .pathParam("paymentId", "pay-1")
    .queryParam("include", "status-history")
    .header("X-Request-ID", UUID.randomUUID())
    .contentType(ContentType.JSON)
    .body(new PaymentRequest("NL91ABNA0417164300", "NL69INGB0123456789", "EUR", "25.00"))
.when()
    .put("/payments/{paymentId}")
.then()
    .statusCode(200);
```

1. **2.1 Path parameters** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#path-parameters))
   - Named (`pathParam`, `pathParams`) and unnamed (by position) parameters
   - Mixing both, and how values are URL-encoded
2. **2.2 Query, form and plain parameters** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#parameters))
   - `queryParam` versus `formParam` versus `param`, and what `param` does for GET and POST
   - Multi-value and no-value parameters
   - `java.time` values sent as ISO-8601 (REST Assured 6.1 and later)
   - URL encoding, and turning it off with `urlEncodingEnabled(false)` ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#url-encoding))
3. **2.3 Headers and cookies** ([headers](https://github.com/rest-assured/rest-assured/wiki/Usage#headers), [cookies](https://github.com/rest-assured/rest-assured/wiki/Usage#cookies))
   - `header`, `headers`, multi-value headers, and header merging and overwriting with `HeaderConfig`
   - `cookie`, `cookies` and `Cookie.Builder`
   - `Accept` and `Content-Type`, `accept(..)` and `contentType(..)`, and the charset REST Assured adds
4. **2.4 Request bodies** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#request-body))
   - A JSON string, a `Map`, a `List` and a Java record
   - How the content type chooses the serializer
   - Byte arrays, files and input streams
5. **2.5 Every HTTP method** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#invoking-http-resources))
   - `get`, `post`, `put`, `patch`, `delete`, `head` and `options`
   - The new `QUERY` method (a safe GET with a body) in REST Assured 6.1 and later
   - `request(Method, path)` and custom methods
6. **2.6 Multipart uploads** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#multi-part-form-data))
   - `multiPart(..)` with files, strings and byte arrays, and `MultiPartSpecBuilder`
   - Uploading a bulk payment file

## Module 3: Validating Responses in Depth

You will learn to check any part of a JSON or XML response precisely, including money.

```java
.then()
    .rootPath("transactions.findAll { it.type == 'DEBIT' }")
    .body("size()", equalTo(3))
    .body("amount.sum()", comparesEqualTo(new BigDecimal("-142.35")))
    .body("counterparty.name", hasItem("Albert Heijn"));
```

1. **3.1 GPath for JSON** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#example-3---complex-parsing-and-validation))
   - `find`, `findAll`, `collect`, `any`, `every`, `max`, `min`, `sum`, `size()`
   - Ranges (`[0..2]`), negative indexes, keys with dashes or colons
   - Anonymous root arrays (`$` and the empty path)
2. **3.2 Root paths and path arguments** ([root path](https://github.com/rest-assured/rest-assured/wiki/Usage#root-path), [path arguments](https://github.com/rest-assured/rest-assured/wiki/Usage#path-arguments))
   - `rootPath`, `appendRootPath`, `detachRootPath`, `noRootPath`
   - `withArgs(..)` for reusable paths with placeholders
3. **3.3 Numbers and money** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#note-on-floats-and-doubles))
   - `Float`, `Double`, `Integer` and `Long`, and when each is used
   - `JsonConfig.numberReturnType(BIG_DECIMAL)` and `comparesEqualTo` for amounts
   - `closeTo` for values that are approximate on purpose
4. **3.4 The Hamcrest toolbox**
   - Collections: `hasItem`, `hasItems`, `contains`, `containsInAnyOrder`, `everyItem`, `hasSize`, `empty`
   - Maps: `hasKey`, `hasEntry`; strings: `startsWith`, `matchesPattern`, `equalToIgnoringCase`
   - Combining: `allOf`, `anyOf`, `not`
5. **3.5 Using the response to check itself** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#use-the-response-to-verify-other-parts-of-the-response))
   - `ResponseAwareMatcher`, for example a `Location` header that must end with the id in the body
6. **3.6 Cookies, full bodies and response time** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#detailed-cookies))
   - `cookie(..)` and `detailedCookie(..)`: `Secure`, `HttpOnly`, `SameSite`, expiry
   - Matching the full body as a string
7. **3.7 XML and HTML responses** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#example-2---xml))
   - XmlPath on an ISO 20022 `camt.053` bank statement: attributes with `@`, namespaces, depth-first `**`
   - Reading HTML with XmlPath in HTML mode

---

# Intermediate

## Module 4: Extracting Values and Mapping Objects

You will learn to get data out of a response, as single values or as Java objects, and to feed it into the next request.

```java
String consentId = given().body(newConsent)
    .when().post("/consents")
    .then().statusCode(201)
    .extract().path("consentId");

List<Account> accounts = given().header("Consent-ID", consentId)
    .when().get("/accounts")
    .then().extract().as(new TypeRef<>() {});
```

1. **4.1 Extracting after validation** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#extracting-values-from-the-response-after-validation))
   - `extract().path(..)`, `extract().response()`, `extract().header(..)`, `extract().statusCode()`
   - Validate first, extract second, in one chain
2. **4.2 JsonPath and XmlPath on their own** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#json-using-jsonpath))
   - `response.jsonPath()`, `JsonPath.from(..)`, `getList`, `getMap`, `getObject`
   - Using JsonPath to parse JSON that didn't come from an HTTP call
3. **4.3 Mapping to Java objects** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#deserialization))
   - `as(Account.class)` with records
   - Generic types with `TypeRef` ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#deserialization-with-generics))
   - Asserting on mapped objects with AssertJ
4. **4.4 Choosing and configuring the object mapper** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#configuration))
   - Jackson 3 (`tools.jackson`) and Jackson 2, which REST Assured 6 supports side by side, and `ObjectMapperType`
   - Your own `JsonMapper` through `ObjectMapperConfig`
   - Unknown properties and why they matter for forward-compatible tests
5. **4.5 Chaining requests**
   - Create, then follow the `Location` header, then read back
   - Keeping test data independent between tests

## Module 5: Reusable Specifications and Configuration

You will learn to remove repetition from a test suite and to configure REST Assured safely, without static state.

```java
RequestSpecification openBankingApi = new RequestSpecBuilder()
    .setBaseUri(api.baseUri()).setPort(api.port())
    .setBasePath("/open-banking/v4.0/aisp")
    .addHeader("x-fapi-interaction-id", UUID.randomUUID().toString())
    .setContentType(ContentType.JSON)
    .log(LogDetail.ALL)
    .build();

ResponseSpecification problem = new ResponseSpecBuilder()
    .expectContentType("application/problem+json")
    .expectBody("type", notNullValue())
    .build();

given(openBankingApi).when().get("/accounts/unknown").then().statusCode(404).spec(problem);
```

1. **5.1 Request specifications** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#specification-re-use))
   - `RequestSpecBuilder`, `given(spec)` and `given().spec(spec)`
   - Merging specifications, and what changed in REST Assured 6.2 (values a spec never set are no longer reset)
   - Querying a specification with `SpecificationQuerier`
2. **5.2 Response specifications**
   - `ResponseSpecBuilder`, `then().spec(..)` and checking a whole spec at once
3. **5.3 RestAssuredConfig** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#detailed-configuration))
   - `HttpClientConfig` timeouts with `connectTimeout(Duration)` and `readTimeout(Duration)` (REST Assured 6.3)
   - `RedirectConfig`: following redirects, turning it off, and `followRedirectsForAllMethods` (6.3)
   - `DecoderConfig`: the default charset for responses that don't declare one
4. **5.4 Keeping secrets out of logs** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#blacklist-headers-from-logging))
   - `LogConfig.blacklistHeader("Authorization", "Cookie")`
   - Logging only on failure, to a stream other than standard out
5. **5.5 Static defaults versus specifications**
   - Why `RestAssured.baseURI` and `RestAssured.requestSpecification` break parallel tests
   - A JUnit 6 extension that starts the API and injects a ready `RequestSpecification` with a `ParameterResolver` (building on the JUnit course, Module 6)

## Module 6: Schema and Contract Validation

You will learn to check the shape of a response instead of only its values, so that a test catches every unexpected change.

```java
.then()
    .statusCode(201)
    .body(matchesJsonSchemaInClasspath("schemas/payment-created.schema.json"));
```

1. **6.1 JSON Schema** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#json-schema-validation))
   - The `json-schema-validator` module and `matchesJsonSchemaInClasspath`
   - Draft 2020-12 features (`$defs`, `const`, `if`/`then`/`else`, `prefixItems`), supported since REST Assured 6.1 ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#choosing-the-json-schema-version))
   - Strict schemas with `additionalProperties: false`, and when that is too strict
   - Validating JSON without an HTTP call
2. **6.2 Error contracts**
   - RFC 9457 problem details: a shared schema for every error response
   - Validation errors with field-level details
3. **6.3 XML schemas** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#schema-and-dtd-validation))
   - `matchesXsdInClasspath` on an ISO 20022 `pain.001` payment initiation
4. **6.4 Checking every response against an OpenAPI description**
   - A REST Assured filter that checks the operation, status, content type and body of every response against an OpenAPI 3.1 file, whose schemas are JSON Schema 2020-12
   - The Atlassian `openapi-request-validator-restassured` library as the complete, off-the-shelf version
   - Contract tests versus functional tests, and what each catches

## Module 7: Authentication and Transport Security

You will learn to test protected APIs, including what must happen when credentials are missing, wrong or insufficient.

```java
String token = given()
    .formParam("grant_type", "client_credentials")
    .formParam("scope", "accounts:read")
    .auth().preemptive().basic(CLIENT_ID, CLIENT_SECRET)
.when()
    .post(tokenEndpoint)
.then()
    .statusCode(200)
    .extract().path("access_token");

given().auth().oauth2(token).when().get("/accounts").then().statusCode(200);
```

1. **7.1 Basic authentication** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#basic-authentication))
   - Challenged versus preemptive, and why APIs usually need preemptive
2. **7.2 Bearer tokens** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#oauth-2))
   - `auth().oauth2(token)` versus a plain `Authorization` header
   - Getting a token with the client credentials grant, and reusing it per test class
3. **7.3 Negative security tests**
   - 401 with a `WWW-Authenticate: Bearer error="invalid_token"` header (RFC 6750) for missing, expired and tampered tokens
   - 403 with `error="insufficient_scope"` for a missing scope
   - Wrong audience, wrong issuer, a token from another client
4. **7.4 Sessions, form login and CSRF** ([sessions](https://github.com/rest-assured/rest-assured/wiki/Usage#session-support), [form auth](https://github.com/rest-assured/rest-assured/wiki/Usage#form-authentication))
   - `SessionFilter`, `auth().form(..)` and `CsrfConfig`
5. **7.5 TLS and mutual TLS** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#ssl))
   - Trust stores, and why `relaxedHTTPSValidation()` hides real problems
   - Client certificates with a key store, fixed for mutual TLS in REST Assured 6.2
   - `SSLConfig.sslContext(..)` with the JDK's own types (REST Assured 6.3)
6. **7.6 Redirects and credentials**
   - Sensitive headers are dropped on a cross-host redirect since REST Assured 6.1 (`RedirectConfig.stripSensitiveHeadersOnCrossHostRedirect`), and a test that proves it

---

# Advanced

## Module 8: Filters and Extensibility

You will learn to change every request and response in one place, and to extend REST Assured where it has no built-in feature.

```java
class CorrelationIdFilter implements Filter {
    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification response, FilterContext ctx) {
        request.header("X-Correlation-Id", UUID.randomUUID().toString());
        return ctx.next(request, response);
    }
}
```

1. **8.1 Writing filters** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#filters))
   - `Filter`, `FilterContext.next(..)`, and `OrderedFilter` for ordering
   - Adding filters per request, per specification and globally
2. **8.2 Useful filters**
   - Correlation ids, timing and metrics
   - A logging filter that masks tokens and personal data in bodies
   - Changing a response with `ResponseBuilder` ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#response-builder))
3. **8.3 Signing requests**
   - A filter that adds an HTTP Message Signature (RFC 9421) and a `Content-Digest` (RFC 9530), the kind of request signing open banking APIs require
   - Testing that the server rejects unsigned, tampered, wrongly signed and stale requests
4. **8.4 Custom authentication** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#custom-authentication))
   - `ContextAuthenticationScheme` (REST Assured 6.3), which doesn't depend on Apache HttpClient 4
5. **8.5 Parsers, mappers and listeners** ([guide](https://github.com/rest-assured/rest-assured/wiki/Usage#custom-parsers))
   - `registerParser(..)` and `defaultParser` for APIs with a wrong content type
   - A custom `ObjectMapper`
   - `ResponseValidationFailureListener`, to attach evidence to a failed test

## Module 9: Data-Driven Tests and API Workflows

You will learn to cover many inputs without copying tests, and to test flows that span several requests.

```java
@ParameterizedTest(name = "{0} -> {1}")
@CsvSource({
    "NL00ABNA0000000000, invalid IBAN check digits",
    "'',                  creditorIban must not be blank",
})
void rejectsInvalidCreditor(String iban, String expectedDetail) {
    given(api).body(paymentTo(iban))
        .when().post("/payments")
        .then().statusCode(422).body("errors.detail", hasItem(expectedDetail));
}
```

1. **9.1 Parameterized API tests**
   - `@CsvSource`, `@MethodSource`, `@FieldSource` and `@ParameterizedClass` with REST Assured (building on the JUnit course, Module 7)
   - Validation matrices for one endpoint
2. **9.2 Dynamic tests**
   - `@TestFactory` that generates a test for every endpoint, for example "every endpoint rejects a missing token"
3. **9.3 Multi-step workflows**
   - Initiate a payment, authorise it, execute it, read its status history
   - `@Nested` and `@Order` for readable flows, and why each test should still create its own data
4. **9.4 HTTP semantics that APIs get wrong**
   - Idempotency keys: the same request twice gives the same payment
   - ETags with `If-None-Match` (304) and `If-Match` (412)
   - Pagination with `Link` headers and cursors
5. **9.5 Asynchronous APIs**
   - 202 Accepted and polling a status resource with Awaitility

## Module 10: Stubbing Dependencies with WireMock

You will learn to test an API whose own dependencies you don't control, by replacing them with stubs.

```java
@RegisterExtension
static WireMockExtension fxProvider = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

@Test
void convertsWithTheProvidersRate() {
    fxProvider.stubFor(get("/rates/EUR/GBP").willReturn(okJson("{\"rate\": 0.85}")));

    given(paymentService).body(eurToGbp("100.00"))
        .when().post("/quotes")
        .then().statusCode(201).body("targetAmount", comparesEqualTo(new BigDecimal("85.00")));
}
```

1. **10.1 Why stub, and what**
   - Testing your API through REST Assured while WireMock plays the third party
2. **10.2 Stubbing with WireMock's JUnit extension**
   - Request matching, response templating, stateful scenarios
3. **10.3 Faults and slowness**
   - Delays, 5xx responses, connection resets, malformed bodies, and what your API must do with them
4. **10.4 Verifying outgoing calls**
   - Checking that your API sent the right request to the third party, with the right headers and body

## Module 11: Spring Boot Integration

You will learn the two ways to test a Spring Boot API with REST Assured, and when to use each.

```java
@WebMvcTest(AccountController.class)
class AccountControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void returnsAnAccount() {
        RestAssuredMockMvc.given().mockMvc(mockMvc)
            .when().get("/accounts/{id}", "acc-1001")
            .then().statusCode(200).body("owner", equalTo("Anna de Vries"));
    }
}
```

1. **11.1 RestAssuredMockMvc without a context**
   - The `spring-mock-mvc` module: the REST Assured syntax without starting a server
   - `standaloneSetup(..)`: a controller you build yourself, no Spring context
2. **11.2 A web slice with Spring Security**
   - `@WebMvcTest` with the real security configuration and an injected `MockMvc`
   - Spring Security's `jwt()` post-processor through `postProcessors(..)` or `auth().with(..)`
3. **11.3 A real server on a random port**
   - `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `@LocalServerPort` with plain REST Assured, and real signed tokens
4. **11.4 Keeping versions aligned**
   - Spring Boot 4.1 manages JUnit, Jackson and Hamcrest but not REST Assured; importing the course's BOMs before Spring Boot's keeps one version of each library family

## Module 12: Real Infrastructure with Testcontainers

You will learn to run REST Assured against real dependencies in Docker, and to make a large suite fast and organised.

1. **12.1 Real tokens from Keycloak**
   - A Keycloak container with an imported realm: confidential clients, client scopes and an audience mapper
   - OpenID Connect discovery, the client credentials grant, and an API that verifies tokens with the published JWKS
   - Skipping, not failing, where Docker is missing (building on the CIBA lab)
2. **12.2 Running in parallel**
   - JUnit 6 parallel execution with REST Assured, and the rules: no static configuration, an immutable specification per class, no shared mutable data
3. **12.3 Organising a suite**
   - Tags for smoke, security and regression tests, a composed `@SmokeTest`, and Maven profiles to run each slice

---

## Appendix A: Cheat sheet

| I want to... | Write |
| --- | --- |
| Send a GET | `given().when().get("/accounts")` |
| Fill a path placeholder | `get("/accounts/{id}", "acc-1001")` or `pathParam("id", "acc-1001")` |
| Add a query parameter | `queryParam("status", "ACTIVE")` |
| Send a JSON body | `contentType(ContentType.JSON).body(record)` |
| Add a header | `header("X-Request-ID", id)` |
| Use a bearer token | `auth().oauth2(token)` |
| Check the status | `then().statusCode(200)` |
| Check a JSON value | `body("owner", equalTo("Anna de Vries"))` |
| Check every element | `body("accounts.currency", everyItem(equalTo("EUR")))` |
| Find an element | `body("accounts.find { it.id == 'acc-1003' }.status", equalTo("BLOCKED"))` |
| Get a value out | `extract().path("id")` |
| Map to an object | `extract().as(Account.class)` |
| Log only on failure | `log().ifValidationFails()` on both sides |
| Validate a schema | `body(matchesJsonSchemaInClasspath("account.schema.json"))` |

## Appendix B: Common pitfalls

- **Decimal numbers are floats.** `body("balance", equalTo(1250.50))` fails, because the value is read as a `Float`. Use `1250.50f`, or switch to `BigDecimal` with `JsonConfig` and compare with `comparesEqualTo` (Module 3).
- **A `then()` chain stops at the first failure.** Each call validates immediately. Put related paths in one `body(..)` call, or use a response specification, to see every mismatch at once.
- **Static configuration leaks.** `RestAssured.baseURI` and friends are global. Reset them, or better, use specifications (Module 5).
- **`relaxedHTTPSValidation()` turns TLS checks off.** It makes a broken certificate set-up pass silently. Configure a trust store instead (Module 7).
- **Logs contain secrets.** `log().all()` prints `Authorization` headers and tokens. Blacklist them (Module 5).
- **Response time includes the network.** Use it as a generous upper bound, not as a performance test.
- **Tests that depend on each other.** A test that reads data created by another test fails when run alone. Create what each test needs (Module 9).
- **Testing through REST Assured what a unit test could check.** REST Assured tests are slower and coarser. Use them for the HTTP contract (status, headers, body shape, security), and unit tests for business rules.

## Appendix C: REST Assured 6 and the road to 7

This course uses REST Assured 6.3.0 (October 2026). Things worth knowing:

- REST Assured 6 needs Java 17 or later, supports Jackson 3 next to Jackson 2, and supports Spring 7 in its Spring modules.
- Since 6.2 the core `rest-assured` module is written in Java and no longer depends on Groovy directly. Groovy still arrives through `json-path` and `xml-path`, which use it for GPath.
- 6.3 deprecates the APIs that expose Apache HttpClient 4 types (such as `HttpClientConfig.setParam(..)` for timeouts and `sslSocketFactory(..)`) and adds replacements, because 7.0 removes them. The `rest-assured-openrewrite` module has a recipe, `io.restassured.MigrateRestAssured6Deprecations`, that rewrites code to the replacements.
- 6.3 publishes `json-path-gpath` and `xml-path-gpath`, the 7.0 names of `json-path` and `xml-path`.
- Jackson 1 support is deprecated and leaves in 7.0.

The course uses only the non-deprecated APIs, so its code should move to 7.0 with few changes.

## Resources

- [REST Assured usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage) and [getting started](https://github.com/rest-assured/rest-assured/wiki/GettingStarted)
- [REST Assured release notes](https://github.com/rest-assured/rest-assured/wiki/ReleaseNotes) and [change log](https://github.com/rest-assured/rest-assured/raw/master/changelog.txt)
- [REST Assured Javadoc](https://javadoc.io/doc/io.rest-assured/rest-assured/latest/index.html)
- [Hamcrest tutorial](https://hamcrest.org/JavaHamcrest/tutorial)
- [JUnit 6 user guide](https://docs.junit.org/6.1.3/)
- RFC 9457 (problem details), RFC 6750 (bearer tokens), RFC 9421 (HTTP message signatures), RFC 9110 (HTTP semantics)
