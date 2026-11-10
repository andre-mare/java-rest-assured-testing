---
title: "Where REST Assured Requests Go: baseUri, port, basePath and Static Defaults"
slug: where-rest-assured-requests-go
module: 1
section: 3
date: 2026-11-10
author: André Maré
category: rest-assured-foundations
tags: [rest-assured, junit, java, api-testing, configuration]
description: "Learn how REST Assured builds a URL from baseUri, port, basePath and the path, when an absolute URL wins, and why static defaults on RestAssured are global state."
readingTime: 7 min read
---

Blog · **rest-assured-foundations**

# Where REST Assured Requests Go: baseUri, port, basePath and Static Defaults

**André Maré** · November 10, 2026 · 7 min read

Where REST Assured Requests Go: Learn how REST Assured builds a URL from baseUri, port, basePath and the path, when an absolute URL wins, and why static defaults on RestAssured are global state.

A test that sends its request to the wrong place rarely fails with a useful message. It might get `Connection refused` because the port is wrong, or a `404` because the `/api/v1` prefix is missing. Worse, it might pass against a server that a previous test class left configured. Where does a REST Assured request actually go, and who decides?

This post follows [Section 3 of Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) of the REST Assured course. It shows the four parts REST Assured combines into a URL, how to set them on each request or as static defaults, and why those defaults must always be undone with `RestAssured.reset()`. The examples are in [`PerRequestConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_03/PerRequestConfigurationTest.java) and [`StaticConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_03/StaticConfigurationTest.java).

## What Makes a REST Assured URL?

REST Assured builds the URL of every request from four parts:

1. **`baseUri`**: the scheme and host, such as `http://localhost`.
2. **`port`**: the TCP port, such as the free port the test API picked.
3. **`basePath`**: a prefix shared by many endpoints, such as `/api/v1`.
4. **The path**: the argument to `get(..)`, `post(..)` and the other methods, such as `/accounts/acc-1004`.

```text
  http://localhost : 54321 /api/v1 /accounts/acc-1004
  └─── baseUri ──┘  └port┘ └base┘ └──── path ──────┘
```

Each part can be set on a single request or as a static default on the `RestAssured` class. A value on the request always wins over the default.

## How a URL is Resolved

When you call `get(path)`, REST Assured goes through these steps:

1. **Absolute URL?** If the path is a complete URL with a scheme, host and port, it is used as it is.
2. **Request values.** Otherwise it takes `baseUri`, `port` and `basePath` from the request, if they are set there.
3. **Static defaults.** Any part not set on the request comes from `RestAssured.baseURI`, `RestAssured.port` and `RestAssured.basePath`.
4. **Built-in defaults.** Without either, REST Assured uses `http://localhost`, port `8080` and an empty base path.

That last step is a common surprise. A test that forgets the port does not fail with a clear message. It sends the request to port 8080, and fails with a connection error or, worse, reaches another service that happens to be running there.

## Setting Values per Request

### Step 1: Use a Base Path

`basePath(..)` puts a prefix in front of every path, so the tests only need the part that changes:

```java
@Test
void basePathIsPutInFrontOfEveryPath() {
    given()
        .baseUri(api.baseUri())
        .port(api.port())
        .basePath(AccountsApi.BASE_PATH)            // "/api/v1"
    .when()
        .get("/accounts/acc-1004")                  // goes to /api/v1/accounts/acc-1004
    .then()
        .statusCode(200)
        .body("owner", equalTo("Emily Clarke"));
}
```

### Step 2: Put the Port in the Base URI

You can also put the port in the base URI itself:

```java
@Test
void thePortCanAlsoBePartOfTheBaseUri() {
    given()
        .baseUri("http://localhost:" + api.port())
    .when()
        .get("/api/health")
    .then()
        .statusCode(200);
}
```

This is convenient when the address comes from configuration as one string, such as `http://localhost:54321`.

### Step 3: Use an Absolute URL

A complete URL wins over everything else. To prove it, this test sets a port that nothing listens on, and the request still succeeds:

```java
@Test
void anAbsoluteUrlWinsOverBaseUriAndPort() {
    given()
        .baseUri("http://localhost")
        .port(1)                                    // nothing listens on port 1 ...
    .when()
        .get("http://localhost:" + api.port() + "/api/health")   // ... but this URL is complete
    .then()
        .statusCode(200);
}
```

Setting every value on every request is verbose, but each test is independent and safe to run in parallel.

> **The code above, ready to run**
>
> Both test classes pass with `mvn test -pl module-01 -Dtest='PerRequestConfigurationTest,StaticConfigurationTest'`. To see the 8080 default for yourself, remove `.port(api.port())` from a test and read the error.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## Static Defaults on RestAssured

`RestAssured` has public static fields for the same settings. Set them once, and every request in the JVM that doesn't set its own value uses them:

```java
class StaticConfigurationTest {

    private static AccountsApi api;

    @BeforeAll
    static void startApiAndSetDefaults() {
        api = AccountsApi.start();
        RestAssured.baseURI = api.baseUri();
        RestAssured.port = api.port();
        RestAssured.basePath = AccountsApi.BASE_PATH;
    }

    @AfterAll
    static void resetDefaultsAndStopApi() {
        RestAssured.reset();            // back to http://localhost:8080 with an empty base path
        api.close();
    }
}
```

The tests become very short:

```java
@Test
void requestsUseTheStaticDefaults() {
    get("/accounts/acc-1002").then().statusCode(200).body("type", equalTo("SAVINGS"));
}
```

A value on the request still overrides the default, for that request only:

```java
@Test
void aValueOnTheRequestOverridesTheDefault() {
    RestAssured.given()
        .basePath("/api")                           // replaces "/api/v1" for this request only
    .when()
        .get("/health")
    .then()
        .statusCode(200);
}
```

## The Cost of Global State

Static defaults are short, but they apply to the whole JVM. Keep these points in mind:

1. **Always reset them.** Call `RestAssured.reset()` in `@AfterAll`. Otherwise every test class that runs later inherits a port from a server that has already stopped.
2. **Don't run them in parallel.** Two classes that set different defaults at the same time overwrite each other's values. The failures that follow are intermittent and hard to trace.
3. **Watch for hidden coupling.** A test that passes only because an earlier class set `RestAssured.port` fails when you run it on its own.
4. **Prefer specifications in larger suites.** Module 5 replaces static defaults with reusable `RequestSpecification` objects. They are just as short, and they have none of these problems.

## Wrapping Up

REST Assured builds every URL from `baseUri`, `port`, `basePath` and the path, and a complete URL in `get(..)` wins over all of them. Values set on the request are explicit and safe to run in parallel. Static defaults on `RestAssured` are convenient, but they are global state that must be undone with `RestAssured.reset()`. Without a port, REST Assured uses 8080, so set the port explicitly.

The next post, [First REST Assured Assertions](module-01-section-04-first-rest-assured-assertions.md), checks everything a response carries: the status line, content type, headers, body paths, lists, numbers and response time.

> **The code above, ready to run**
>
> All twelve modules of the course are runnable tests against real HTTP APIs. [Start with Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) or [browse the full outline](https://github.com/andre-mare/java-rest-assured-testing/blob/main/REST%20Assured%20Outline.md).

---

*Filed under:* rest-assured-foundations · *Tags:* rest-assured, junit, java, api-testing, configuration
