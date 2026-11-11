# Keeping Secrets Out of REST Assured Logs: blacklistHeader and Logging on Failure

Keeping Secrets Out of REST Assured Logs: Mask Authorization and Cookie headers in every log with LogConfig.blacklistHeader(..), check that masking also applies when logging on failure, and test what was logged by sending the log to memory.

[Module 1](module-01-section-05-rest-assured-logging.md) showed how useful `log()` is when a test fails. It prints everything: the URL, the body and every header. Every header includes `Authorization: Bearer ...`. CI logs are kept for months, copied into tickets and read by many people. A token in a log is a leaked credential, and a test suite that logs one every time it fails is leaking credentials on a schedule.

This post follows [Section 4 of Module 5](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-05) of the REST Assured course. It masks secret headers in REST Assured's logs, and tests the masking by capturing the log in memory. The examples are in [`SecretsInLogsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_04/SecretsInLogsTest.java).

## What is a Blacklisted Header?

`LogConfig` controls how REST Assured logs. Two of its settings matter here:

1. **`blacklistHeader(names..)`** prints the value of the named headers as `[ BLACKLISTED ]`, in every request and response log. The names are case-insensitive, like header names.
2. **`defaultStream(printStream)`** sends the log to a stream of your choice instead of standard out.

Blacklisting changes only the log. The real header is still sent, so the request works as before.

## The Set-Up

The tests send requests with a bearer token to the Open Banking API from [Section 1](module-05-section-01-rest-assured-request-specifications.md). Before each test, a fresh in-memory stream catches the log:

```java
private static final String TOKEN = "test-token";

private ByteArrayOutputStream log;
private RestAssuredConfig loggingToMemory;

@BeforeEach
void captureTheLog() {
    log = new ByteArrayOutputStream();
    loggingToMemory = config().logConfig(logConfig()
            .defaultStream(new PrintStream(log, true, StandardCharsets.UTF_8))
            .blacklistHeader("Authorization", "Cookie"));
}

private RequestSpecification openBanking() {
    return given()
        .config(loggingToMemory)
        .baseUri(api.baseUri()).port(api.port()).basePath(OpenBankingApi.BASE_PATH)
        .header("Authorization", "Bearer " + TOKEN)
        .header("x-fapi-interaction-id", UUID.randomUUID().toString());
}

private String logged() {
    return log.toString(StandardCharsets.UTF_8);
}
```

Capturing the log in memory is only for these tests: it lets them assert on what would have been printed. In your own suite, set `blacklistHeader(..)` and leave the stream alone.

## Masking Secrets

### Step 1: Blacklisted Headers Are Masked

```java
@Test
void blacklistedHeadersAreMasked() {
    openBanking()
        .log().headers()
    .when()
        .get("/accounts")
    .then()
        .statusCode(200);

    assertTrue(logged().contains("Authorization=[ BLACKLISTED ]"), logged());
    assertFalse(logged().contains(TOKEN), logged());
    assertTrue(logged().contains("x-fapi-interaction-id="), logged());   // other headers are still there
}
```

The log looks like this:

```text
Headers:		Accept=*/*
				Authorization=[ BLACKLISTED ]
				x-fapi-interaction-id=b5ce988b-f7ba-499f-80dd-4351b1db2149
```

The three assertions each check something different:

1. **The header is still in the log**, so a reader knows it was sent.
2. **The token is nowhere in the log.** This is the real security check.
3. **Other headers are logged as usual**, so the log stays useful.

The `200` status proves that the real header still reached the server.

### Step 2: Masking Also Applies When Logging on Failure

The most common logging set-up is `ifValidationFails()`, so that is where masking matters most:

```java
@Test
void maskingAlsoAppliesWhenLoggingOnFailure() {
    assertThrows(AssertionError.class, () ->
        openBanking()
            .log().ifValidationFails(LogDetail.HEADERS)
        .when()
            .get("/accounts")
        .then()
            .body("Data.Account[0].Nickname", equalTo("not the nickname")));

    assertTrue(logged().contains("[ BLACKLISTED ]"), logged());
    assertFalse(logged().contains(TOKEN), logged());
}
```

The check fails on purpose, so the request headers are logged, and the token is still masked.

### Step 3: Nothing Is Logged When the Test Passes

```java
@Test
void nothingIsLoggedWhenTheTestPasses() {
    openBanking()
        .log().ifValidationFails()
    .when()
        .get("/accounts")
    .then()
        .log().ifValidationFails()
        .statusCode(200);

    assertTrue(logged().isEmpty(), logged());
}
```

`ifValidationFails()` on both the request and the response keeps a passing suite's log empty. Fewer logs means less to leak, even with masking.

> **The code above, ready to run**
>
> All three tests in [`SecretsInLogsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_04/SecretsInLogsTest.java) pass with `mvn test -pl module-05 -Dtest=SecretsInLogsTest`. Remove `"Authorization"` from `blacklistHeader(..)` and watch the first test fail with the token in its message.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 5 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/README.md)

## Making Masking the Default

Masking only helps if every request uses it. Put the `LogConfig` where every request gets it:

1. **In a shared request specification**, with `setConfig(..)` on the `RequestSpecBuilder`.
2. **In a JUnit extension** that builds specifications, as in [Section 5](module-05-section-05-rest-assured-junit-extension.md).
3. **In `RestAssured.config`**, if your suite already sets global defaults.

Which headers to blacklist depends on the API: `Authorization` and `Cookie` almost always, plus any API key header such as `X-API-Key`. `Set-Cookie` in responses can carry session ids too.

## Common Pitfalls

1. **Blacklisting only in some tests.** A test without the config logs the token in full. Put the `LogConfig` in a shared specification.
2. **Secrets outside headers.** `blacklistHeader(..)` masks headers only. A token in a query parameter, such as `?access_token=..`, is printed in the request URI, and a password in a body is printed with the body. Module 8 masks those with a filter.
3. **Forgetting the response.** `Set-Cookie` on a sign-in response carries a session id. Blacklist it too when you log responses.
4. **`log().all()` in committed tests.** It logs every request, even when the test passes. Use `ifValidationFails()`.
5. **Trusting masking without a test.** A refactoring that drops the config goes unnoticed. A test like the ones above proves the token never reaches the log.

## Wrapping Up

A token in a log is a leaked credential. `LogConfig.blacklistHeader(..)` prints the named headers as `[ BLACKLISTED ]` in every request and response log, while the real headers are still sent. It also applies when logging on failure, which is when logs are most often read and shared. Combine it with `ifValidationFails()` so a passing suite logs nothing, put the config in a shared specification so every request gets it, and test the masking by capturing the log with `defaultStream(..)`.
