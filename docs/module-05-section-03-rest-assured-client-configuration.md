# Client Configuration in REST Assured: Timeouts, Redirects and Charsets With RestAssuredConfig

Client Configuration in REST Assured: Configure how the HTTP client behaves with RestAssuredConfig, covering read and connect timeouts with the Duration methods of REST Assured 6.3, following redirects for GET, POST and all methods, and the default charset for decoding text.

How long should a test wait for a slow server? Should it follow a redirect, or check it? And when the server sends text without saying which charset it uses, how does the client decode it? The defaults answer these questions for you, and most of the time the answers are fine. But a test suite that hangs for minutes on a stuck service, or that passes on your laptop and fails on CI because of a charset, is a suite with a configuration problem.

This post follows [Section 3 of Module 5](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-05) of the REST Assured course. It shows the parts of `RestAssuredConfig` that control the HTTP client: timeouts, redirects and charsets. The examples are in [`ClientConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_03/ClientConfigurationTest.java), and the [configuration section of the usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#config) lists every option.

## What is RestAssuredConfig?

`RestAssuredConfig` groups many smaller configs, one per topic. You have already used some of them: `HeaderConfig` and `EncoderConfig` in [Module 2](module-02-section-03-rest-assured-headers-cookies-content-types.md), `JsonConfig` in [Module 3](module-03-section-03-rest-assured-numbers-and-money.md) and `ObjectMapperConfig` in [Module 4](module-04-section-04-rest-assured-object-mapper-configuration.md). This section adds three:

1. **`HttpClientConfig`**: timeouts.
2. **`RedirectConfig`**: whether, and for which methods, redirects are followed.
3. **`DecoderConfig`**: how the response is decoded, including the charset.

A config is immutable: each call such as `config().httpClient(..)` returns a new one. You can set it in three places:

- **Per request**, with `config(..)` on the request.
- **In a specification**, with `setConfig(..)` on a `RequestSpecBuilder`.
- **Globally**, in `RestAssured.config`, with the usual warnings about static state.

## The Endpoints Under Test

The Open Banking API has a few endpoints under `/test-support` that exist only to exercise client configuration. They have none of the rules of the real API:

| Request | Response |
| --- | --- |
| `GET /test-support/slow?millis=..` | Waits that many milliseconds, then answers `200` |
| `GET` or `POST /test-support/redirect` | `302 Found` with `Location: /test-support/landing` |
| `GET` or `POST /test-support/landing` | `{"method": ".."}`, the method that arrived |
| `GET /test-support/greeting` | `Grüße uit Zürich` as UTF-8, with the content type `text/plain` and no charset |

## Timeouts

### Step 1: A Read Timeout

```java
@Test
void aReadTimeoutStopsWaitingForASlowServer() {
    Exception exception = assertThrows(Exception.class, () ->
        testSupport()
            .config(config().httpClient(httpClientConfig().readTimeout(Duration.ofMillis(300))))
            .queryParam("millis", 2000)
        .when()
            .get("/slow"));

    assertCauseIs(SocketTimeoutException.class, exception);
}
```

The server waits two seconds, and the client gives up after 300 milliseconds with `java.net.SocketTimeoutException: Read timed out`. The test doesn't depend on whether the exception is thrown directly or wrapped: `assertCauseIs(..)` walks the chain of causes.

### Step 2: Timeouts That Are Long Enough

```java
@Test
void withinTheTimeoutAllIsWell() {
    testSupport()
        .config(config().httpClient(httpClientConfig()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))))
        .queryParam("millis", 100)
    .when()
        .get("/slow")
    .then()
        .statusCode(200);
}
```

- **`connectTimeout(..)`** limits how long the client waits to open the connection.
- **`readTimeout(..)`** limits how long it waits for data once connected.

The `Duration` methods arrived in REST Assured 6.3. They replace setting Apache HttpClient 4 parameters by name, which REST Assured 7 removes. Use them in new code, and move old code to them before you upgrade.

## Redirects

### Step 3: GET Redirects Are Followed

```java
@Test
void getRedirectsAreFollowedByDefault() {
    testSupport().when().get("/redirect").then().statusCode(200).body("method", equalTo("GET"));
}
```

The test sees the landing page, not the `302`.

### Step 4: Turn Following Off

```java
@Test
void followingCanBeTurnedOff() {
    testSupport()
        .redirects().follow(false)                              // a shortcut for redirectConfig().followRedirects(false)
    .when()
        .get("/redirect")
    .then()
        .statusCode(302)
        .header("Location", "/test-support/landing");
}
```

When the redirect itself is what you test, for example after a login or for a moved resource, turn following off and check the status and `Location`.

### Step 5: POST Redirects Are Not Followed by Default

```java
@Test
void postRedirectsAreNotFollowedByDefault() {
    testSupport().when().post("/redirect").then().statusCode(302);
}
```

### Step 6: Follow Redirects for All Methods

```java
@Test
void followRedirectsForAllMethods() {
    testSupport()
        .config(config().redirect(redirectConfig().followRedirectsForAllMethods(true)))
    .when()
        .post("/redirect")
    .then()
        .statusCode(200);
}
```

`followRedirectsForAllMethods(true)`, new in REST Assured 6.3, follows redirects of `POST`, `PUT` and `DELETE` too. A `302` after a `POST` is followed with a `GET`, as browsers do: the landing page answers `{"method":"GET"}`.

> **The code above, ready to run**
>
> All eight tests in [`ClientConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/src/test/java/com/example/module_05/section_03/ClientConfigurationTest.java) pass with `mvn test -pl module-05 -Dtest=ClientConfigurationTest`. The timeout test takes about two seconds, because the server finishes its sleep after the client has given up.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 5 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-05/README.md)

## Charsets

The greeting endpoint sends `Grüße uit Zürich` encoded as UTF-8, but its content type is only `text/plain`. The client has to guess the charset. REST Assured's guess is `DecoderConfig`'s default content charset, which is the JVM's default charset.

### What Happens With the Wrong Guess

```java
@Test
void withoutACharsetTextIsDecodedWithTheDefaultContentCharset() {
    testSupport()
        .config(config().decoderConfig(decoderConfig().defaultContentCharset("ISO-8859-1")))
    .when()
        .get("/greeting")
    .then()
        .body(not(equalTo("Grüße uit Zürich")))
        .body(equalTo(new String("Grüße uit Zürich".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)));
}
```

Decoded as ISO-8859-1, HTTP's old default for text, each two-byte UTF-8 character becomes two characters, and the body reads `GrÃ¼Ãe uit ZÃ¼rich` (with an invisible control character after the second `Ã`).

### Make the Charset Explicit

```java
@Test
void decoderConfigSetsTheDefaultCharset() {
    testSupport()
        .config(config().decoderConfig(decoderConfig().defaultContentCharset("UTF-8")))
    .when()
        .get("/greeting")
    .then()
        .body(equalTo("Grüße uit Zürich"));
}
```

Since JDK 18, the JVM's default charset is UTF-8, so the default guess is right on a stock JVM. That is luck, not design: an older JVM, a `-Dfile.encoding` setting on a CI agent, or a platform default can change it. Setting the charset in `DecoderConfig` makes the test independent of where it runs. The real fix is on the server: send `text/plain; charset=utf-8`.

## Common Pitfalls

1. **No timeouts at all.** A stuck service then hangs the whole build. Set a read timeout in a shared specification.
2. **Timeouts that are too tight.** A limit that passes on your laptop fails on a busy CI agent. Leave generous margins.
3. **Apache HttpClient parameters for timeouts.** They are on their way out in REST Assured 7. Use `connectTimeout(Duration)` and `readTimeout(Duration)`.
4. **Following a redirect you meant to test.** The test sees the final page and can't check the `302`. Use `redirects().follow(false)`.
5. **Relying on the JVM's default charset.** It differs between machines. Set `decoderConfig().defaultContentCharset(..)`, or have the server declare its charset.

## Wrapping Up

`RestAssuredConfig` controls how the HTTP client behaves, per request, in a specification or globally. `HttpClientConfig` sets connect and read timeouts, with `Duration` methods since REST Assured 6.3. `RedirectConfig` decides whether redirects are followed: `GET` by default, every method with `followRedirectsForAllMethods(true)`, and none with `redirects().follow(false)`. `DecoderConfig` sets the charset used when the server doesn't declare one, so tests don't depend on the JVM they run on.
