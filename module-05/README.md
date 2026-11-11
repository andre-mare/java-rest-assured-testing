# Module 5: Reusable Specifications and Configuration

This module covers removing repetition from a suite and configuring REST Assured safely: request and response specifications, `RestAssuredConfig`, keeping secrets out of logs, and a JUnit 6 extension that replaces static configuration.

## Table of Contents

- [Module 5: Reusable Specifications and Configuration](#module-5-reusable-specifications-and-configuration)
  - [Table of Contents](#table-of-contents)
  - [The Domain: an Open Banking Accounts API](#the-domain-an-open-banking-accounts-api)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Request Specifications](#section-1-request-specifications)
    - [Section 2: Response Specifications](#section-2-response-specifications)
    - [Section 3: Client Configuration](#section-3-client-configuration)
    - [Section 4: Secrets in Logs](#section-4-secrets-in-logs)
    - [Section 5: A JUnit Extension Instead of Static State](#section-5-a-junit-extension-instead-of-static-state)

## The Domain: an Open Banking Accounts API

[`OpenBankingApi`](src/main/java/com/example/openbanking/OpenBankingApi.java) follows the style of the UK Open Banking (OBIE) standard, so every request needs the same set-up, which is what specifications are for.

- Base path `/open-banking/v4.0/aisp`; endpoints `GET /accounts`, `/accounts/{id}`, `/accounts/{id}/balances`
- Every request needs `Authorization: Bearer test-token` (else 401 with `WWW-Authenticate: Bearer realm="aisp"`), an `x-fapi-interaction-id` (else 400), which is echoed back, and an `Accept` that allows JSON (else 406)
- Bodies use OBIE's envelopes: `Data`, `Links`, `Meta`

Under `/test-support` (no rules) are endpoints that exercise client configuration: `GET /slow?millis=..`, `GET`/`POST /redirect` (302 to `/landing`), `/landing` (says which method arrived) and `/greeting` (UTF-8 text without a declared charset).

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-05` | 24 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Request Specifications | [Request Specifications in REST Assured: RequestSpecBuilder, Layered Specs and Overrides](../docs/module-05-section-01-rest-assured-request-specifications.md) |
| 2. Response Specifications | [Response Specifications in REST Assured: Shared Expectations, Error Shapes and given(request, response)](../docs/module-05-section-02-rest-assured-response-specifications.md) |
| 3. Client Configuration | [Client Configuration in REST Assured: Timeouts, Redirects and Charsets With RestAssuredConfig](../docs/module-05-section-03-rest-assured-client-configuration.md) |
| 4. Secrets in Logs | [Keeping Secrets Out of REST Assured Logs: blacklistHeader and Logging on Failure](../docs/module-05-section-04-rest-assured-secrets-in-logs.md) |
| 5. A JUnit Extension Instead of Static State | [A JUnit Extension for REST Assured: Injected Specifications Instead of Static State](../docs/module-05-section-05-rest-assured-junit-extension.md) |

## Sections

### Section 1: Request Specifications

**Guide:** [module-05-section-01-rest-assured-request-specifications.md](../docs/module-05-section-01-rest-assured-request-specifications.md)

| Class | What it tests |
| --- | --- |
| [`RequestSpecificationTest`](src/test/java/com/example/module_05/section_01/RequestSpecificationTest.java) | `given(spec)` and `spec(..)`, a specification built on another, a specification without credentials, overriding a header with `HeaderConfig`, overriding the base path, and inspecting a specification with `SpecificationQuerier` |

**REST Assured concepts**

- `RequestSpecBuilder` builds an immutable specification; `addRequestSpecification(..)` layers one on another.
- Headers merge: the request's `Authorization` would be sent next to the spec's. Overwrite with `headerConfig().overwriteHeadersWithName(..)`.
- Since REST Assured 6.2, sending straight from a built specification (`spec.get(..)`) throws; go through `given(spec)`.

### Section 2: Response Specifications

**Guide:** [module-05-section-02-rest-assured-response-specifications.md](../docs/module-05-section-02-rest-assured-response-specifications.md)

| Class | What it tests |
| --- | --- |
| [`ResponseSpecificationTest`](src/test/java/com/example/module_05/section_02/ResponseSpecificationTest.java) | `then().spec(..)`, `given(request, response)`, a shared error shape, and all failures of a specification reported at once |

**REST Assured concepts**

- A response specification is checked as a whole, so every failed expectation is reported in one message.
- `expectHeader(name, response -> matcher)` (REST Assured 6.1) checks a header against the response itself.
- `given(request, response)` returns a `RequestSender`: the request is complete, so anything per-request must already be in the specification, and you send directly without `when()`.

### Section 3: Client Configuration

**Guide:** [module-05-section-03-rest-assured-client-configuration.md](../docs/module-05-section-03-rest-assured-client-configuration.md)

| Class | What it tests |
| --- | --- |
| [`ClientConfigurationTest`](src/test/java/com/example/module_05/section_03/ClientConfigurationTest.java) | A read timeout, timeouts that are long enough, redirects followed for GET, not followed when turned off, not followed for POST by default, followed for all methods, and the default charset for decoding |

**REST Assured concepts**

- `config().httpClient(httpClientConfig().readTimeout(Duration))`: the `Duration` methods arrived in REST Assured 6.3 and replace the Apache HttpClient 4 parameters, which REST Assured 7 removes.
- `redirectConfig().followRedirectsForAllMethods(true)` (6.3) follows redirects of POST, PUT and DELETE too.
- Text without a declared charset is decoded with `DecoderConfig`'s default content charset, which is the JVM's default charset (UTF-8 since JDK 18, but it varies with `file.encoding` and older JVMs). Don't rely on it: `decoderConfig().defaultContentCharset("UTF-8")` makes it explicit.

### Section 4: Secrets in Logs

**Guide:** [module-05-section-04-rest-assured-secrets-in-logs.md](../docs/module-05-section-04-rest-assured-secrets-in-logs.md)

| Class | What it tests |
| --- | --- |
| [`SecretsInLogsTest`](src/test/java/com/example/module_05/section_04/SecretsInLogsTest.java) | Blacklisted headers printed as `[ BLACKLISTED ]`, masking when logging on failure, and nothing logged when a test passes |

**REST Assured concepts**

- `logConfig().blacklistHeader("Authorization", "Cookie")` masks headers in every log. `defaultStream(..)` sends the log elsewhere, here to memory so the test can read it.

### Section 5: A JUnit Extension Instead of Static State

**Guide:** [module-05-section-05-rest-assured-junit-extension.md](../docs/module-05-section-05-rest-assured-junit-extension.md)

| Class | What it tests |
| --- | --- |
| [`InjectedSpecificationTest`](src/test/java/com/example/module_05/section_05/InjectedSpecificationTest.java) | Test methods that receive ready-made request and response specifications, each with its own interaction id |
| [`OpenBankingApiExtension`](src/test/java/com/example/module_05/section_05/support/OpenBankingApiExtension.java) | Starts the API once per class in the extension store and resolves `RequestSpecification`, `ResponseSpecification` and `OpenBankingApi` parameters |
| [`OpenBankingApiTest`](src/test/java/com/example/module_05/section_05/support/OpenBankingApiTest.java) | A composed annotation: the extension plus a tag |

**REST Assured concepts**

- A `ParameterResolver` (JUnit course, Module 6) hands each test its own specification: no static state, so classes can run in parallel (Module 12).
- Values in the extension store that implement `AutoCloseable` are closed by JUnit, so the API stops without an `AfterAllCallback`. `Store.computeIfAbsent(key, creator, type)` is the JUnit 6 name; `getOrComputeIfAbsent` is deprecated.
