# REST Assured Course Guides

This folder holds the step-by-step guides for the [REST Assured course](../README.md). Each guide follows one section of a module. It explains the concepts behind the section's tests, walks through the code and points out common mistakes.

## Table of Contents

- [REST Assured Course Guides](#rest-assured-course-guides)
  - [Table of Contents](#table-of-contents)
  - [How to Use the Guides](#how-to-use-the-guides)
  - [Module 1: Foundations](#module-1-foundations)
  - [Module 2: Building Requests](#module-2-building-requests)
  - [Module 3: Validating Responses in Depth](#module-3-validating-responses-in-depth)
  - [Module 4: Extracting Values and Mapping Objects](#module-4-extracting-values-and-mapping-objects)
  - [Module 5: Reusable Specifications and Configuration](#module-5-reusable-specifications-and-configuration)
  - [Module 6: Schema and Contract Validation](#module-6-schema-and-contract-validation)
  - [Module 7: Authentication and Transport Security](#module-7-authentication-and-transport-security)

## How to Use the Guides

For each section:

1. Read the guide. It explains the concepts and shows the tests one step at a time.
2. Run the section's test class with the command near the end of the guide, such as `mvn test -pl module-01 -Dtest=FirstRequestTest`.
3. Change the code or the tests, predict what will happen, and read the output.

The guides follow the order of the course, but each one can be read on its own. Guide files are named `module-NN-section-NN-<topic>.md`, so they sort in course order. The [learning outline](../REST%20Assured%20Outline.md) lists every topic in the course and which module teaches it.

## Module 1: Foundations

How a REST Assured test is put together, where its requests go, how to check a response, and how to read what happens when a check fails. The tests run against an Accounts API. See the [Module 1 README](../module-01/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Your First REST Assured Request: Testing a Real HTTP API with JUnit](module-01-section-01-your-first-rest-assured-request.md) | Start a real HTTP API with `@BeforeAll`, send a request with given/when/then, and check the status code and JSON body |
| 2 | [The Anatomy of a REST Assured Test: RequestSpecification, Response and ValidatableResponse](module-01-section-02-anatomy-of-a-rest-assured-test.md) | The three types behind given/when/then, the syntactic sugar, the shorter forms, stored responses and a first path parameter |
| 3 | [Where REST Assured Requests Go: baseUri, port, basePath and Static Defaults](module-01-section-03-where-rest-assured-requests-go.md) | How a URL is built from `baseUri`, `port`, `basePath` and the path, when an absolute URL wins, and why static defaults are global state |
| 4 | [First REST Assured Assertions: Status, Content Type, Headers, Body Paths and Response Time](module-01-section-04-first-rest-assured-assertions.md) | Status codes and lines, content types, case-insensitive headers, GPath body paths, lists, `Float` numbers, problem details and response time |
| 5 | [Logging in REST Assured: See What Was Sent and What Came Back](module-01-section-05-rest-assured-logging.md) | Logging everything, single parts, only when validation fails, only for errors, or only for one status code |
| 6 | [Reading REST Assured Failures: Failure Messages and Eager Validation](module-01-section-06-reading-rest-assured-failures.md) | The shape of a failure message, why a `then()` chain stops at its first failure, and how one `body(..)` call reports every mismatch |

## Module 2: Building Requests

Every way to put data into a request, and how to send every HTTP method. The tests run against a Payments API with echo endpoints that show exactly what was sent. See the [Module 2 README](../module-02/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Path Parameters in REST Assured: Named, Unnamed and URL-Encoded Values](module-02-section-01-rest-assured-path-parameters.md) | Named and unnamed path parameters, mixing the two, URL encoding, and finding a resource through its `Location` header |
| 2 | [Query, Form and Plain Parameters in REST Assured: queryParam, formParam and param](module-02-section-02-rest-assured-query-and-form-parameters.md) | Multi-value and empty query parameters, `java.time` values, form bodies, turning URL encoding off, and where `param(..)` puts its value |
| 3 | [Headers, Cookies and Content Types in REST Assured: What Is Merged, What Is Overwritten](module-02-section-03-rest-assured-headers-cookies-content-types.md) | Headers from maps and objects, merging versus overwriting, `accept(..)`, the charset added to text types, and cookies |
| 4 | [Request Bodies in REST Assured: Strings, Maps, Records, Lists, Bytes and Files](module-02-section-04-rest-assured-request-bodies.md) | Which bodies are sent as they are and which are serialized, records as typed bodies, and testing `422` and `415` rejections |
| 5 | [Every HTTP Method in REST Assured: GET, PUT, PATCH, DELETE, HEAD, OPTIONS and QUERY](module-02-section-05-rest-assured-http-methods.md) | Replacing, patching and cancelling a resource, `HEAD`, `OPTIONS`, the new `QUERY` method, custom methods and `405` |
| 6 | [Multipart Uploads in REST Assured: Files, Bytes and MultiPartSpecBuilder](module-02-section-06-rest-assured-multipart-uploads.md) | Uploading a file with a text field from a `File`, from bytes and with `MultiPartSpecBuilder`, and testing a missing part |

## Module 3: Validating Responses in Depth

How to check any part of a response precisely: GPath for JSON, root paths, numbers and money, the Hamcrest matchers you will use most, response-aware matchers, cookies and their security attributes, and XML and HTML. The tests run against a Transactions API with one account and a month of transactions. See the [Module 3 README](../module-03/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [GPath for JSON in REST Assured: find, findAll, collect, Ranges and Root Arrays](module-03-section-01-rest-assured-gpath-for-json.md) | Filtering and transforming lists with Groovy closures, `any` and `every`, `max` and `min`, ranges, `flatten()`, keys with dashes and a JSON array at the root |
| 2 | [Root Paths and Path Arguments in REST Assured: rootPath, appendRootPath and withArgs](module-03-section-02-rest-assured-root-paths-and-path-arguments.md) | Removing the repeated start of a path with `rootPath(..)`, moving it during a chain, and reusing one path for many values with `withArgs(..)` |
| 3 | [Numbers and Money in REST Assured: Float, BigDecimal and comparesEqualTo](module-03-section-03-rest-assured-numbers-and-money.md) | How JSON numbers are read, why `Float` can't hold money, `BigDecimal` with `JsonConfig`, `comparesEqualTo` versus `equalTo`, exact sums and `closeTo` |
| 4 | [The Hamcrest Toolbox for REST Assured: Collection, Map, String and Combined Matchers](module-03-section-04-rest-assured-hamcrest-matchers.md) | The difference between `hasItems`, `contains` and `containsInAnyOrder`, map and string matchers, and combining matchers with `allOf`, `anyOf`, `oneOf` and `not` |
| 5 | [Response-Aware Matchers in REST Assured: Using the Response to Check Itself](module-03-section-05-rest-assured-response-aware-matchers.md) | Checking values the server chooses, such as a new id in the `Location` header, with a lambda or `endsWithPath(..)`, and one body value against another |
| 6 | [Response Cookies in REST Assured: Values, Security Attributes and detailedCookie()](module-03-section-06-rest-assured-response-cookies.md) | Checking cookie values and the `Secure`, `HttpOnly`, `SameSite`, `Path` and `Max-Age` attributes of a session cookie, and reading a `Cookie` object |
| 7 | [XML and HTML in REST Assured: Elements, Attributes, Depth-First Search and Namespaces](module-03-section-07-rest-assured-xml-and-html.md) | XML paths and attributes, `find` and `findAll` on elements, depth-first search with `**`, namespaces with `declareNamespace(..)`, and lenient HTML |

## Module 4: Extracting Values and Mapping Objects

How to get data out of responses, as single values or as Java records, how to configure the object mapper, and how to chain requests in which each step uses what the previous one returned. The tests run against a Customers and Consents API modelled on open banking account access consents. See the [Module 4 README](../module-04/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Extracting Values in REST Assured: extract(), path(..) and ExtractableResponse](module-04-section-01-rest-assured-extracting-values.md) | Extracting a value, a list, headers and the whole response, using a value in the next request, and why you validate before you extract |
| 2 | [JsonPath and XmlPath in REST Assured: Typed Getters, Maps, Objects and JSON From Anywhere](module-04-section-02-rest-assured-jsonpath-and-xmlpath.md) | Typed getters that convert values, maps and mapped objects, and reading JSON and XML that didn't come from an HTTP call |
| 3 | [Mapping Responses to Java Records in REST Assured: as(..), TypeRef and Unknown Fields](module-04-section-03-rest-assured-mapping-to-java-records.md) | Mapping bodies to records with `as(..)`, generic types with `TypeRef`, unknown fields in Jackson 3, and AssertJ assertions on records |
| 4 | [Configuring the Object Mapper in REST Assured: Jackson 3, ObjectMapperType and a Strict JsonMapper](module-04-section-04-rest-assured-object-mapper-configuration.md) | Choosing a mapper with `ObjectMapperType`, the lenient default, a strict `JsonMapper`, and the 6.3 factory interfaces |
| 5 | [Chaining Requests in REST Assured: Location Headers, Hypermedia Links and a Consent Flow](module-04-section-05-rest-assured-chaining-requests.md) | Following `Location` headers and `_links`, the full consent flow from customer to balances, and testing what a consent must refuse |

## Module 5: Reusable Specifications and Configuration

How to remove repetition from a suite and configure REST Assured safely: request and response specifications, `RestAssuredConfig`, keeping secrets out of logs, and a JUnit 6 extension that replaces static configuration. The tests run against an account information API in the style of the UK Open Banking standard. See the [Module 5 README](../module-05/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Request Specifications in REST Assured: RequestSpecBuilder, Layered Specs and Overrides](module-05-section-01-rest-assured-request-specifications.md) | Building and layering request specifications, testing without credentials, overriding a header with `HeaderConfig`, and inspecting a specification |
| 2 | [Response Specifications in REST Assured: Shared Expectations, Error Shapes and given(request, response)](module-05-section-02-rest-assured-response-specifications.md) | Shared expectations for success and errors, `then().spec(..)` and `given(request, response)`, and why a specification reports every failure at once |
| 3 | [Client Configuration in REST Assured: Timeouts, Redirects and Charsets With RestAssuredConfig](module-05-section-03-rest-assured-client-configuration.md) | Connect and read timeouts with `Duration`, following redirects for GET, POST and all methods, and setting the default charset for decoding |
| 4 | [Keeping Secrets Out of REST Assured Logs: blacklistHeader and Logging on Failure](module-05-section-04-rest-assured-secrets-in-logs.md) | Masking `Authorization` and `Cookie` headers with `blacklistHeader(..)`, masking when logging on failure, and testing what was logged |
| 5 | [A JUnit Extension for REST Assured: Injected Specifications Instead of Static State](module-05-section-05-rest-assured-junit-extension.md) | A JUnit 6 extension that starts the API per class, injects specifications as parameters, and closes the API through the extension store |

## Module 6: Schema and Contract Validation

How to check the shape of responses instead of single values: JSON Schema 2020-12, a shared contract for errors, XML Schema for ISO 20022 messages, and checking every response against an OpenAPI description. The tests run against a payment initiation API in the style of PSD2. See the [Module 6 README](../module-06/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [JSON Schema Validation in REST Assured: matchesJsonSchemaInClasspath and Draft 2020-12](module-06-section-01-rest-assured-json-schema-validation.md) | Validating whole responses against a draft 2020-12 schema, what a schema catches that value checks miss, inline schemas, and validating JSON without HTTP |
| 2 | [An Error Contract in REST Assured: RFC 9457 Problems With JSON Schema if/then/else](module-06-section-02-rest-assured-error-contract.md) | One problem schema for every error, `if`/`then`/`else` so a 422 must list its field errors, and testing the schema itself |
| 3 | [XML Schema Validation in REST Assured: XSDs for ISO 20022 pain.001 and pain.002](module-06-section-03-rest-assured-xml-schema-iso-20022.md) | Validating payment files and status reports with `matchesXsdInClasspath(..)`, checking test data, and handling the `SAXParseException` of an invalid document |
| 4 | [An OpenAPI Contract Filter for REST Assured: Every Response Checked Against the Spec](module-06-section-04-rest-assured-openapi-contract-filter.md) | A filter that checks every operation, status and body against an OpenAPI 3.1 description, and the difference between contract and functional tests |

## Module 7: Authentication and Transport Security

How to test protected APIs, including everything that must happen when credentials are missing, wrong or not enough: Basic authentication, OAuth 2.0 bearer tokens, the error responses of RFC 6749 and RFC 6750, sessions with CSRF, TLS and mutual TLS, and credentials on redirects. The tests run against an OAuth 2.0 authorization server and the resource server it protects. See the [Module 7 README](../module-07/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Basic Authentication in REST Assured: Challenged Versus Preemptive](module-07-section-01-rest-assured-basic-authentication.md) | The 401 challenge, challenged and preemptive Basic, why challenged Basic fails against a server that never challenges, and wrong credentials |
| 2 | [OAuth 2.0 Bearer Tokens in REST Assured: The Client Credentials Grant and auth().oauth2(..)](module-07-section-02-rest-assured-oauth2-bearer-tokens.md) | Getting a token with the client credentials grant, `client_secret_basic` and `client_secret_post`, sending it with `auth().oauth2(..)`, and reading JWT claims |
| 3 | [Negative Security Tests in REST Assured: Missing, Invalid, Expired and Insufficient Tokens](module-07-section-03-rest-assured-negative-security-tests.md) | Token endpoint errors, `WWW-Authenticate` responses for missing, expired, wrong-audience and tampered tokens, `401` versus `403`, and a missing scope |
| 4 | [Sessions and CSRF in REST Assured: SessionFilter, htmlPath and a Form Login](module-07-section-04-rest-assured-sessions-and-csrf.md) | Reading a CSRF token with `htmlPath()`, keeping a session with a `SessionFilter`, setting the session id explicitly, and a missing or reused CSRF token |
| 5 | [TLS and Mutual TLS in REST Assured: Trust Stores, Key Stores and an SSLContext](module-07-section-05-rest-assured-tls-and-mutual-tls.md) | Why `relaxedHTTPSValidation()` is the wrong fix, trusting a certificate with a trust store, presenting a client certificate, and passing a JDK `SSLContext` |
| 6 | [Credentials on Redirects in REST Assured: Sensitive Headers Stripped Across Origins](module-07-section-06-rest-assured-credentials-on-redirects.md) | How REST Assured 6.1 drops `Authorization` and `Cookie` on cross-origin redirects, testing what a redirect target receives, and the opt-out |
