---
title: "Your First REST Assured Request: Testing a Real HTTP API with JUnit"
slug: your-first-rest-assured-request
module: 1
section: 1
date: 2026-11-10
author: André Maré
category: rest-assured-foundations
tags: [rest-assured, junit, java, api-testing, given-when-then]
description: "Write your first REST Assured test: start a real HTTP API with @BeforeAll, send a request with given/when/then, and check the status code and JSON body."
readingTime: 7 min read
---

Blog · **rest-assured-foundations**

# Your First REST Assured Request: Testing a Real HTTP API with JUnit

**André Maré** · November 10, 2026 · 7 min read

Your First REST Assured Request: Write your first REST Assured test, start a real HTTP API with `@BeforeAll`, send a request with given/when/then, and check the status code and JSON body.

A unit test calls a Java method and checks what it returns. But what about the code that sits behind an HTTP endpoint? How do you prove that `GET /api/health` really answers with `200` and a JSON body that says the service is up? You need to send a real request over a real socket and check the real response. REST Assured is a Java library built for that: you describe a request, send it and check the response in one readable chain.

This post is the first in a series that follows [Module 1 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01). It covers the smallest useful API test: starting a server for the test class, the given/when/then structure, and the first two checks you will write in almost every test, `statusCode(..)` and `body(..)`. Everything runs inside JUnit 6, so the lifecycle methods from the [JUnit course](https://github.com/andre-mare/java-unit-testing) apply unchanged.

## What is REST Assured?

REST Assured is a Java DSL for testing HTTP APIs. A test has three parts:

1. **The request specification**, started with `given()`. It holds where the request goes and what it carries: base URI, port, headers, parameters and body.
2. **The action**, started with `when()`. A method such as `get(..)`, `post(..)` or `delete(..)` sends the request and returns the response.
3. **The validation**, started with `then()`. Each call after it, such as `statusCode(..)` or `body(..)`, checks one part of the response and fails the test if it doesn't match.

Unlike a hand-written `HttpClient` test, you don't parse JSON, compare strings or build error messages yourself. REST Assured reads the body with a path language, checks it with Hamcrest matchers (which it brings with it) and writes a clear failure message when something is wrong.

## Test Flow Overview

Every test in this module follows the same four steps:

1. **Start the API.** `@BeforeAll` starts the Accounts API on a free port, once for the whole test class.
2. **Arrange.** `given()` describes the request: the base URI `http://localhost` and the port the API picked.
3. **Act.** `when().get("/api/health")` sends the request over HTTP.
4. **Assert.** `then()` checks the status code and the body of the response.

When the last test in the class has run, `@AfterAll` stops the server. Nothing has to be installed or started by hand, and no test depends on a port that might already be taken.

## The API Under Test

The module tests a small, read-only Accounts API. It runs on the JDK's built-in HTTP server and writes JSON with Jackson 3. This post uses only one of its endpoints:

```http
GET /api/health

HTTP/1.1 200 OK
Content-type: application/json; charset=utf-8

{"status":"UP"}
```

### Why a real server?

Mocking the HTTP layer would test your mock, not your API. A real server checks routing, status codes, headers and serialisation as a client sees them. Because the server starts in milliseconds and picks a free port, it costs almost nothing.

### Why a server per test class?

Starting the server in `@BeforeAll` and stopping it in `@AfterAll` gives each test class its own server. Classes don't share state, and you can run any class on its own from your IDE.

## Writing the Test

### Step 1: Start and Stop the API

The test class keeps the running API in a static field, because `@BeforeAll` and `@AfterAll` methods are static:

```java
class FirstRequestTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApi() {
        api = AccountsApi.start();      // picks a free port
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }
}
```

- **`AccountsApi.start()`** binds to a free port and returns a handle to the server.
- **`api.baseUri()`** returns `http://localhost`, and **`api.port()`** the port it picked.

### Step 2: Check the Status Code

The first test only checks that the endpoint answers with `200 OK`:

```java
@Test
void healthEndpointAnswersWith200() {
    given()                                 // Arrange: describe the request
        .baseUri(api.baseUri())             //   http://localhost
        .port(api.port())                   //   the port the API picked
    .when()                                 // Act: send it
        .get("/api/health")
    .then()                                 // Assert: check the response
        .statusCode(200);
}
```

Read it aloud and it is a sentence: *given* this base URI and port, *when* I get `/api/health`, *then* the status code is 200. That is Arrange, Act, Assert for HTTP.

### Step 3: Check the Body

The second test also checks what the endpoint says:

```java
@Test
void healthEndpointReportsThatItIsUp() {
    given()
        .baseUri(api.baseUri())
        .port(api.port())
    .when()
        .get("/api/health")
    .then()
        .statusCode(200)
        .body("status", equalTo("UP"));     // "status" is a path into the JSON body {"status":"UP"}
}
```

- **`"status"`** is a path into the JSON body. For `{"status":"UP"}` it returns the string `"UP"`.
- **`equalTo("UP")`** is a Hamcrest matcher, imported with `import static org.hamcrest.Matchers.equalTo;`.

You never parse the JSON yourself. REST Assured sees the `application/json` content type, parses the body and looks the path up for you.

> **The code above, ready to run**
>
> Every example in this post is a passing test in [`FirstRequestTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_01/FirstRequestTest.java). Clone the repository and run `mvn test -pl module-01 -Dtest=FirstRequestTest`. You need JDK 26 and Maven 3.9, and nothing else.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## Running the Test

Run the class from the repository root:

```bash
mvn test -pl module-01 -Dtest=FirstRequestTest
```

Both tests pass. Now change `equalTo("UP")` to `equalTo("DOWN")` and run it again. The test fails with a message that names the path, the expected value and the actual value. Reading those messages is the topic of [Section 6](module-01-section-06-reading-rest-assured-failures.md), but it is worth seeing one now.

## The Imports You Need

REST Assured is used through static imports, which keep the chain short:

```java
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
```

Hamcrest comes with REST Assured as a dependency, so you don't add it to the POM. Matchers such as `hasItems`, `containsString` and `lessThan` are always available.

## Common Pitfalls

1. **Forgetting the port.** Without `port(..)`, REST Assured sends the request to port 8080 on localhost. If nothing listens there, you get a `ConnectException` instead of an assertion failure.
2. **A non-static field for the API.** `@BeforeAll` methods are static by default, so the field they set must be static too.
3. **Forgetting to stop the server.** Without `@AfterAll`, every test class leaves a server running until the JVM exits.
4. **Checking only the status code.** A `200` with the wrong body is still a bug. Add at least one `body(..)` check that proves the response means what you expect.
5. **Mixing up the two `status` values.** `statusCode(..)` checks the HTTP status line. `body("status", ..)` reads a field that happens to be called `status` in the JSON body.

## Wrapping Up

A REST Assured test is a real HTTP round trip written as one sentence: given a request, when it is sent, then the response looks like this. Start the API under test in `@BeforeAll`, point `given()` at it with `baseUri(..)` and `port(..)`, and check both the status code and the body.

The next post, [The Anatomy of a REST Assured Test](module-01-section-02-anatomy-of-a-rest-assured-test.md), takes this chain apart. It shows the three types behind given/when/then and the shorter forms you will meet in other code bases.

> **The code above, ready to run**
>
> All twelve modules of the course are runnable tests against real HTTP APIs, from this first request to OAuth 2.0 tokens from Keycloak. [Start with Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) or [browse the full outline](https://github.com/andre-mare/java-rest-assured-testing/blob/main/REST%20Assured%20Outline.md).

---

*Filed under:* rest-assured-foundations · *Tags:* rest-assured, junit, java, api-testing, given-when-then
