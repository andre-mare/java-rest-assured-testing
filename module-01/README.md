# Module 1: Foundations

This module covers the basics of REST Assured: how a test is put together, where its requests go, how to check a response, how to see what was sent and received, and how to read a failure.

## Table of Contents

- [Module 1: Foundations](#module-1-foundations)
  - [Table of Contents](#table-of-contents)
  - [The Domain: an Accounts API](#the-domain-an-accounts-api)
  - [Running the Module](#running-the-module)
    - [Prerequisites](#prerequisites)
    - [From the Command Line](#from-the-command-line)
    - [From Your IDE](#from-your-ide)
    - [Test Reports](#test-reports)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Your First Request](#section-1-your-first-request)
    - [Section 2: The Anatomy of a Test](#section-2-the-anatomy-of-a-test)
    - [Section 3: Where Requests Go](#section-3-where-requests-go)
    - [Section 4: First Assertions](#section-4-first-assertions)
    - [Section 5: Logging](#section-5-logging)
    - [Section 6: Reading Failures](#section-6-reading-failures)
  - [Deliberately Failing Tests](#deliberately-failing-tests)

## The Domain: an Accounts API

The API under test is [`AccountsApi`](src/main/java/com/example/accounts/AccountsApi.java), a small read-only REST API served by the JDK's built-in HTTP server and writing JSON with Jackson 3. Each test class starts it in `@BeforeAll` on a free port and stops it in `@AfterAll`, so there is nothing to install or start by hand.

| Request | Response |
| --- | --- |
| `GET /api/health` | 200, `{"status":"UP"}` |
| `GET /api/v1/accounts` | 200, `{"count":4,"accounts":[...]}`. The optional query parameters `status` and `currency` filter the list; an unknown status gives 400 |
| `GET /api/v1/accounts/{id}` | 200 with one account, or 404 for an unknown id |
| Any other method on these paths | 405 with an `Allow: GET` header |
| Any other path | 404 |

Every response has a `Cache-Control: no-store` header and a fresh `X-Request-Id`. An `X-Correlation-Id` request header is echoed back. Errors are RFC 9457 problem details with the content type `application/problem+json`.

The data never changes, so tests can assert exact values:

| id | IBAN | Owner | Type | Currency | Balance | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `acc-1001` | NL91ABNA0417164300 | Anna de Vries | CURRENT | EUR | 1250.50 | ACTIVE |
| `acc-1002` | NL69INGB0123456789 | Anna de Vries | SAVINGS | EUR | 15000.00 | ACTIVE |
| `acc-1003` | NL20INGB0001234567 | Jan Jansen | CURRENT | EUR | -42.10 | BLOCKED |
| `acc-1004` | GB33BUKB20201555555555 | Emily Clarke | CURRENT | GBP | 980.00 | ACTIVE |

A single account has these fields (JSON objects are unordered, so the order on the wire may differ):

```json
{"id":"acc-1001","iban":"NL91ABNA0417164300","owner":"Anna de Vries","type":"CURRENT",
 "currency":"EUR","balance":1250.50,"status":"ACTIVE","openedOn":"2019-04-01"}
```

## Running the Module

### Prerequisites

- **JDK 26 or later.** The build compiles with `--release 26`.
- **Maven 3.9 or later.** The project has no Maven Wrapper, so use an installed `mvn`.

Nothing else is needed. Each test class starts the Accounts API itself on a free port of `localhost`, so there is no server, database or Docker to set up. See [Getting Started](../README.md#getting-started) in the main README for cloning the repository.

### From the Command Line

Run these commands from the repository root.

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-01` | 36 tests pass; `DeliberatelyFailingTest` is excluded |
| Run one section | `mvn test -pl module-01 -Dtest='com/example/module_01/section_03/*'` | 5 tests pass |
| Run one class | `mvn test -pl module-01 -Dtest=FirstAssertionsTest` | 14 tests pass |
| Run one test method | `mvn test -pl module-01 -Dtest=FirstAssertionsTest#contentType` | 1 test passes |
| Read the logging output | `mvn test -pl module-01 -Dtest=LoggingTest` | 5 tests pass, with requests and responses in the console |
| Run the failure demo | `mvn test -pl module-01 -Dtest=DeliberatelyFailingTest -DexcludedGroups=none` | `BUILD FAILURE` with 5 failed tests, as intended |

You can also run the same commands from inside the `module-01` folder by leaving out `-pl module-01`, for example `mvn test -Dtest=FirstRequestTest`.

The build should end with `BUILD SUCCESS`. Some tests log requests and responses on purpose, so JSON in the console is expected and is not a sign of failure.

### From Your IDE

Open the repository root (not just `module-01`) as a Maven project in IntelliJ IDEA, Eclipse or VS Code with the Java extensions, so the parent POM is picked up. Then run any test class or method with the run icon next to it. The IDE runs every class you choose, including `DeliberatelyFailingTest`, because the tag exclusion only applies to Maven's Surefire plugin.

### Test Reports

After a Maven run, Surefire writes a report per test class to `module-01/target/surefire-reports/`: a `.txt` summary and an `.xml` file, which also holds the console output of each test.

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Your First Request | [Your First REST Assured Request: Testing a Real HTTP API with JUnit](../docs/module-01-section-01-your-first-rest-assured-request.md) |
| 2. The Anatomy of a Test | [The Anatomy of a REST Assured Test: RequestSpecification, Response and ValidatableResponse](../docs/module-01-section-02-anatomy-of-a-rest-assured-test.md) |
| 3. Where Requests Go | [Where REST Assured Requests Go: baseUri, port, basePath and Static Defaults](../docs/module-01-section-03-where-rest-assured-requests-go.md) |
| 4. First Assertions | [First REST Assured Assertions: Status, Content Type, Headers, Body Paths and Response Time](../docs/module-01-section-04-first-rest-assured-assertions.md) |
| 5. Logging | [Logging in REST Assured: See What Was Sent and What Came Back](../docs/module-01-section-05-rest-assured-logging.md) |
| 6. Reading Failures | [Reading REST Assured Failures: Failure Messages and Eager Validation](../docs/module-01-section-06-reading-rest-assured-failures.md) |

## Sections

Each section has its own package under [`src/test/java/com/example/module_01`](src/test/java/com/example/module_01).

### Section 1: Your First Request

**Guide:** [module-01-section-01-your-first-rest-assured-request.md](../docs/module-01-section-01-your-first-rest-assured-request.md)

**What it tests:** that the health endpoint answers, and what it says.

| Class | What it tests |
| --- | --- |
| [`FirstRequestTest`](src/test/java/com/example/module_01/section_01/FirstRequestTest.java) | The status code of `GET /api/health`, then the `status` field of its JSON body |

**REST Assured concepts**

- **A real server per test class.** `AccountsApi.start()` in `@BeforeAll` picks a free port, and `close()` in `@AfterAll` stops it. These are the lifecycle methods from Module 1 of the JUnit course.
- **given / when / then** describes the request, sends it and checks the response. It is Arrange, Act, Assert for HTTP.
- **`statusCode(200)`** fails the test if the response has another status.
- **`body("status", equalTo("UP"))`** reads the value at the path `status` in the JSON body and checks it with a Hamcrest matcher. REST Assured brings Hamcrest with it.

### Section 2: The Anatomy of a Test

**Guide:** [module-01-section-02-anatomy-of-a-rest-assured-test.md](../docs/module-01-section-02-anatomy-of-a-rest-assured-test.md)

**What it tests:** the same request written in each of the forms you will find in real code bases.

| Class | What it tests |
| --- | --- |
| [`GivenWhenThenTest`](src/test/java/com/example/module_01/section_02/GivenWhenThenTest.java) | The full form, leaving out `given()`, the one-line `get(url).then()`, a stored `RequestSpecification`, a stored `Response`, and a path parameter |

**REST Assured concepts**

- **Three types.** `given()` returns a `RequestSpecification`, sending it with `get(..)` returns a `Response`, and `then()` returns a `ValidatableResponse`.
- **Syntactic sugar.** `when()` and `and()` change nothing; they make the chain read like a sentence.
- **Shorter forms.** Without set-up, `when().get(url)` or just `get(url)` is enough.
- **A response is an ordinary object.** Keep it in a variable, check it later with `then()`, or read values with `response.path(..)` and use any assertion library.
- **Path parameters.** `get("/api/v1/accounts/{id}", "acc-1001")` replaces `{id}` with the value. Module 2 covers parameters fully.

### Section 3: Where Requests Go

**Guide:** [module-01-section-03-where-rest-assured-requests-go.md](../docs/module-01-section-03-where-rest-assured-requests-go.md)

**What it tests:** how REST Assured builds the URL, set per request or as static defaults.

| Class | What it tests |
| --- | --- |
| [`PerRequestConfigurationTest`](src/test/java/com/example/module_01/section_03/PerRequestConfigurationTest.java) | `basePath`, a port inside `baseUri`, and an absolute URL that wins over `baseUri` and `port` |
| [`StaticConfigurationTest`](src/test/java/com/example/module_01/section_03/StaticConfigurationTest.java) | `RestAssured.baseURI`, `port` and `basePath` as defaults, overriding one per request, and undoing them with `RestAssured.reset()` |

**REST Assured concepts**

- **Four parts make the URL:** `baseUri` (scheme and host), `port`, `basePath` (a shared prefix such as `/api/v1`) and the path given to `get(..)`.
- **A complete URL wins.** A path that starts with `http://` and has a port is used as it is.
- **Without a port, REST Assured uses 8080** for `localhost`, which is a common surprise.
- **Static defaults are global state.** They apply to every request in the JVM. Undo them with `RestAssured.reset()`, and don't use them in classes that run in parallel. Module 5 replaces them with specifications.

### Section 4: First Assertions

**Guide:** [module-01-section-04-first-rest-assured-assertions.md](../docs/module-01-section-04-first-rest-assured-assertions.md)

**What it tests:** the status, content type, headers, body and response time of the Accounts API.

| Class | What it tests |
| --- | --- |
| [`FirstAssertionsTest`](src/test/java/com/example/module_01/section_04/FirstAssertionsTest.java) | Status codes and lines, content types, headers (including case-insensitive names and an echoed header), single values, several paths at once, lists, the default number type, a query parameter, problem-details errors, 405 with `Allow`, and response time |

**REST Assured concepts**

- **Values or matchers.** Every check takes a plain value or a Hamcrest matcher: `statusCode(200)` or `statusCode(lessThan(300))`.
- **Content type.** `contentType(ContentType.JSON)` ignores parameters such as `; charset=utf-8` and treats any `+json` type, such as `application/problem+json`, as JSON. A string such as `"application/json"` must be how the actual value starts.
- **Headers.** HTTP header names are case-insensitive, and so is REST Assured: `cache-control` matches `Cache-control`.
- **Body paths** use GPath, the path language of Groovy:
  - `"owner"` is a field, `"accounts[0].id"` an element of a list and `"accounts[-1].id"` the last element
  - `"accounts.id"` collects the `id` of every element into a list, which `hasItems(..)` and `everyItem(..)` check
  - `"accounts.size()"` calls a method on the list
  - `body(path, matcher, path, matcher, ..)` checks several paths in one call
- **Numbers.** A JSON decimal such as `1250.50` is read as a `Float`, so the expected value is `1250.50f`. Module 3 reads money as `BigDecimal`.
- **Response time** is measured on the client and includes the network, so it is only a sanity check.

### Section 5: Logging

**Guide:** [module-01-section-05-rest-assured-logging.md](../docs/module-01-section-05-rest-assured-logging.md)

**What it tests:** nothing new about the API; it shows what REST Assured can print.

| Class | What it tests |
| --- | --- |
| [`LoggingTest`](src/test/java/com/example/module_01/section_05/LoggingTest.java) | Logging everything, logging parts, logging only when validation fails, only for error statuses, and only for one status |

**REST Assured concepts**

- **`log()` before `when()`** logs the request; **`log()` after `then()`** logs the response.
- **Parts.** `all()`, `method()`, `uri()`, `headers()`, `cookies()`, `body()` for the request; `all()`, `status()`, `headers()`, `cookies()`, `body()` for the response.
- **Conditional logging.** `ifValidationFails()` prints only when a check fails, which is what most suites use. `ifError()` prints responses with a status of 400 or more, and `ifStatusCodeIsEqualTo(..)` one status.
- **Logs can contain secrets**, such as `Authorization` headers. Module 5 shows how to hide them.

### Section 6: Reading Failures

**Guide:** [module-01-section-06-reading-rest-assured-failures.md](../docs/module-01-section-06-reading-rest-assured-failures.md)

**What it tests:** what REST Assured reports when a check fails.

| Class | What it tests |
| --- | --- |
| [`FailureMessagesTest`](src/test/java/com/example/module_01/section_06/FailureMessagesTest.java) | Catches the `AssertionError` of a wrong status, a wrong body value, a `then()` chain that stops at its first failure, and one `body(..)` call that reports all its failures, and checks each message |
| [`DeliberatelyFailingTest`](src/test/java/com/example/module_01/section_06/DeliberatelyFailingTest.java) | The same kinds of failure as real red tests (excluded by default, see below) |

**REST Assured concepts**

- **A failure is an `AssertionError`**, so JUnit reports it like any other failed assertion.
- **The message** starts with how many expectations failed, then explains each one, for example `Expected status code <404> but was <200>.` or `JSON path count doesn't match. Expected: <5> Actual: <4>`.
- **Eager validation.** After `then()`, every call is checked as soon as it is made, so a chain such as `.statusCode(..).header(..).body(..)` stops at the first call that fails. The paths inside one `body(path, matcher, path, matcher, ..)` call are checked together, so all of their mismatches are reported at once. Module 5 shows the same for a response specification.

## Deliberately Failing Tests

[`DeliberatelyFailingTest`](src/test/java/com/example/module_01/section_06/DeliberatelyFailingTest.java) fails on purpose, so you can read REST Assured's real failure output in the console and in your IDE.

**How it is excluded.** The class is tagged `expected-failure-tests`, and the parent POM sets the property `excludedGroups` to that tag, so Surefire skips it.

**How to run it.** Override the property on the command line:

```bash
mvn test -pl module-01 -Dtest=DeliberatelyFailingTest -DexcludedGroups=none
```

**What to expect.** The build ends with `BUILD FAILURE` and five failed tests: a wrong status code, a wrong body value, a missing list item, a wrong content type, and one `body(..)` call with two failing paths, printed together with the logged request and response.
