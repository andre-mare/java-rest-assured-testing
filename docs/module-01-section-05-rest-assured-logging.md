---
title: "Logging in REST Assured: See What Was Sent and What Came Back"
slug: rest-assured-logging
module: 1
section: 5
date: 2026-11-10
author: André Maré
category: rest-assured-foundations
tags: [rest-assured, logging, debugging, java, api-testing]
description: "Use REST Assured's log() to print requests and responses: everything, single parts, only when validation fails, only for errors, or only for one status code."
readingTime: 7 min read
---

Blog · **rest-assured-foundations**

# Logging in REST Assured: See What Was Sent and What Came Back

**André Maré** · November 10, 2026 · 7 min read

Logging in REST Assured: Use REST Assured's log() to print requests and responses, either everything, single parts, only when validation fails, only for errors, or only for one status code.

A failing API test tells you what it expected and what it got. Often that is not enough. Did the request go to the right URL? Was the query parameter really sent? What else was in the body? Did the server set the header at all, or set it with a different value? When you can see the exact request and response, most of these questions answer themselves.

This post follows [Section 5 of Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) of the REST Assured course. It tests nothing new about the API. Instead it shows what REST Assured's `log()` can print and when to use each option. The examples are in [`LoggingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_05/LoggingTest.java), and the [logging section of the usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#logging) has the full reference.

## What is log()?

`log()` returns a logging specification that prints to standard out. Where you call it decides what it logs:

1. **Before `when()`**, `log()` logs the **request**, as REST Assured is about to send it.
2. **After `then()`**, `log()` logs the **response**, as it came back.

Surefire shows standard out in the console and in the test report, and your IDE shows it in the test runner. You don't need to configure anything.

## The Logging Options

The options fall into three groups:

1. **Everything**: `log().all()` prints the whole request or response.
2. **Parts**: `method()`, `uri()`, `headers()`, `cookies()`, `body()` and others print one part.
3. **Conditional**: `ifValidationFails()`, `ifError()` and `ifStatusCodeIsEqualTo(..)` print only when something specific happens.

Run the test class on its own and read the output next to the code:

```bash
mvn test -pl module-01 -Dtest=LoggingTest
```

## Logging Everything

### Step 1: Log the Whole Request and Response

```java
@Test
void logTheWholeRequestAndResponse() {
    accountsApi()
        .log().all()                                // method, URI, parameters, headers, cookies and body
    .when()
        .get("/accounts/{id}", "acc-1001")
    .then()
        .log().all()                                // status line, headers, cookies and body
        .statusCode(200);
}
```

The request log shows every part REST Assured knows about, including the ones that are empty:

```text
Request method:	GET
Request URI:	http://localhost:62481/api/v1/accounts/acc-1001
Proxy:			<none>
Request params:	<none>
Query params:	<none>
Form params:	<none>
Path params:	<none>
Headers:		Accept=*/*
Cookies:		<none>
Multiparts:		<none>
Body:			<none>
```

The response log shows the status line, the headers and a pretty-printed body (the port, date and request id change on every run):

```text
HTTP/1.1 200 OK
Date: Tue, 10 Nov 2026 17:37:12 GMT
Content-type: application/json; charset=utf-8
X-request-id: ac53b79b-a1d4-4067-9ed3-621a23ad71b0
Cache-control: no-store

{
    "id": "acc-1001",
    "iban": "NL91ABNA0417164300",
    "owner": "Anna de Vries",
    "type": "CURRENT",
    "currency": "EUR",
    "balance": 1250.50,
    "status": "ACTIVE",
    "openedOn": "2019-04-01"
}
```

The `Request URI` line is the final URL, built from `baseUri`, `port`, `basePath` and the path. It is the quickest way to check the URL-building rules from [Section 3](module-01-section-03-where-rest-assured-requests-go.md).

### Step 2: Log Only Some Parts

Printing everything for every request makes a large suite unreadable. Pick only the parts you need:

```java
@Test
void logOnlyParts() {
    accountsApi()
        .queryParam("status", "ACTIVE")
        .log().method()
        .log().uri()                                // the final URL, with the query string
    .when()
        .get("/accounts")
    .then()
        .log().status()
        .log().headers()
        .log().body()                               // JSON is pretty-printed by default
        .statusCode(200);
}
```

- **Request parts:** `all()`, `method()`, `uri()`, `params()`, `headers()`, `cookies()` and `body()`.
- **Response parts:** `all()`, `status()`, `headers()`, `cookies()` and `body()`.

> **The code above, ready to run**
>
> The five tests in [`LoggingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_05/LoggingTest.java) pass and print their requests and responses to the console. Run them with `mvn test -pl module-01 -Dtest=LoggingTest`.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## Conditional Logging

### Log Only When Validation Fails

This is the option most real suites use. Nothing is printed while the test passes. When a check fails, the request and the response are printed together with the failure:

```java
@Test
void logOnlyWhenSomethingFails() {
    accountsApi()
        .log().ifValidationFails()
    .when()
        .get("/accounts/{id}", "acc-1001")
    .then()
        .log().ifValidationFails(LogDetail.ALL)
        .body("id", equalTo("acc-1001"));
}
```

Nothing is printed for this test, because every expectation holds. Change the expected id to `"acc-1002"` and run it again: the request and the response appear in the output, right above the assertion error.

`LogDetail` chooses how much to print. `LogDetail.ALL` is the default, and values such as `LogDetail.BODY` or `LogDetail.HEADERS` narrow it down.

### Log Only Error Responses

`ifError()` prints the response when its status code is 400 or higher:

```java
@Test
void logTheResponseOnlyForErrorStatusCodes() {
    accountsApi()
    .when()
        .get("/accounts/{id}", "acc-9999")
    .then()
        .log().ifError()                            // only for status codes of 400 and above
        .statusCode(404);
}
```

This test prints the problem-details body of the `404`, even though the test passes, because the test expects an error.

### Log Only One Status Code

`ifStatusCodeIsEqualTo(..)` prints only for one status. Use it to catch a status you never expect, such as a `500`:

```java
@Test
void logTheResponseOnlyForOneStatusCode() {
    accountsApi()
    .when()
        .get("/accounts")
    .then()
        .log().ifStatusCodeIsEqualTo(500)           // not printed: the status is 200
        .statusCode(200);
}
```

## Logging Considerations

1. **Log only on failure by default.** `log().ifValidationFails()` on both the request and the response gives you everything you need to debug a failure, and keeps the output of a green build quiet.
2. **Logs can contain secrets.** Request logs include headers such as `Authorization` and cookies with session ids. Build output is often kept for a long time and shared widely. Module 5 shows how to hide these headers.
3. **`log()` goes to standard out.** It doesn't use SLF4J or your logging framework, so log levels don't filter it.
4. **Put `log()` before the checks.** After `then()`, each check runs straight away. A `log()` placed after a failing check is never reached. `ifValidationFails()` placed first doesn't have this problem.
5. **Body logs are pretty-printed.** The order of fields and the whitespace you see may differ from what was sent on the wire.

## Wrapping Up

`log()` before `when()` prints the request, and `log()` after `then()` prints the response. Use `all()` while you explore, use single parts such as `uri()` or `body()` to keep output short, and use `ifValidationFails()` in the suites you keep. `ifError()` and `ifStatusCodeIsEqualTo(..)` cover the cases in between. Be careful with logs that contain credentials.

The next and last post of Module 1, [Reading REST Assured Failures](module-01-section-06-reading-rest-assured-failures.md), looks at the other half of debugging: the failure messages themselves, and why some chains report one failure while others report all of them.

> **The code above, ready to run**
>
> All twelve modules of the course are runnable tests against real HTTP APIs. [Start with Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) or [browse the full outline](https://github.com/andre-mare/java-rest-assured-testing/blob/main/REST%20Assured%20Outline.md).

---

*Filed under:* rest-assured-foundations · *Tags:* rest-assured, logging, debugging, java, api-testing
