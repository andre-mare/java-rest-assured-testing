# Response Cookies in REST Assured: Values, Security Attributes and detailedCookie()

Response Cookies in REST Assured: Check the cookies a server sets, from their values to the security attributes of a session cookie (Secure, HttpOnly, SameSite, Path and Max-Age) with detailedCookie(), and read a Cookie object afterwards.

When a user signs in, the server answers with a session cookie. The value is a random token, so a test can't know it in advance. What a test can and should check is how the cookie is protected. Is it marked `Secure`, so it never travels over plain HTTP? `HttpOnly`, so JavaScript can't read it? `SameSite`, so the browser doesn't send it with requests from other sites? A session cookie without these attributes works perfectly, until it is stolen.

This post follows [Section 6 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. [Module 2](module-02-section-03-rest-assured-headers-cookies-content-types.md) sent cookies to the server. This section checks the cookies the server sends back. The examples are in [`CookiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_06/CookiesTest.java).

## What is in a Cookie?

A server sets a cookie with a `Set-Cookie` header. The header has a name and value, followed by attributes:

```http
Set-Cookie: SESSION=0fa9271c-54bd-4c41-9c79-d77a061c75e3; Path=/api; Max-Age=900; Secure; HttpOnly; SameSite=Strict
```

| Attribute | What it does |
| --- | --- |
| `Path=/api` | The browser sends the cookie only for paths under `/api` |
| `Max-Age=900` | The cookie expires after 900 seconds, 15 minutes |
| `Secure` | The cookie is sent only over HTTPS |
| `HttpOnly` | Scripts in the page can't read the cookie |
| `SameSite=Strict` | The cookie is not sent with requests that start on another site |

REST Assured has two ways to check a cookie after `then()`:

1. **`cookie(name)`** and **`cookie(name, value)`** check that the cookie exists, and its value.
2. **`cookie(name, detailedCookie()...)`** checks the value and every attribute.

## The Endpoint Under Test

`POST /api/v1/sessions` signs in and answers `201` with two cookies:

```http
HTTP/1.1 201 Created
Set-Cookie: SESSION=<uuid>; Path=/api; Max-Age=900; Secure; HttpOnly; SameSite=Strict
Set-Cookie: lang=nl; Path=/
```

The session id is a new UUID each time. The `lang` cookie is a preference, not a secret, so it has no security attributes.

A helper signs in and checks the status:

```java
private static ValidatableResponse signIn() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
        .when().post("/sessions")
        .then().statusCode(201);
}
```

## Checking Cookie Values

### Step 1: Value, Existence and Format

```java
private static final String UUID = "[0-9a-f-]{36}";

@Test
void cookieValues() {
    signIn()
        .cookie("lang", "nl")
        .cookie("SESSION")                                          // only that it exists
        .cookie("SESSION", matchesPattern(UUID));
}
```

- **`cookie("lang", "nl")`** checks an exact value.
- **`cookie("SESSION")`** checks only that the server set the cookie.
- **`cookie("SESSION", matchesPattern(UUID))`** checks the format of a value the server generates, with any Hamcrest matcher.

When a cookie is missing, the failure message lists the cookies that were set, attributes included:

```text
Cookie "SESSIONID" was not defined in the response. Cookies are:
SESSION=7f878156-1cf6-417c-b716-d5b751486102;Path=/api;Max-Age=900;Secure;HttpOnly;SameSite=Strict
lang=nl;Path=/
```

## Checking Security Attributes

### Step 2: Lock Down the Session Cookie

```java
@Test
void theSessionCookieIsLockedDown() {
    signIn()
        .cookie("SESSION", detailedCookie()
                .value(matchesPattern(UUID))
                .path("/api")
                .maxAge(900)
                .secured(true)
                .httpOnly(true)
                .sameSite("Strict"));
}
```

`detailedCookie()` is a static import from `io.restassured.matcher.RestAssuredMatchers`. Each call adds one check, and the cookie must pass all of them:

- **`value(..)`** takes a value or a matcher.
- **`path(..)`** and **`maxAge(..)`** check where and how long the cookie is valid.
- **`secured(true)`**, **`httpOnly(true)`** and **`sameSite(..)`** check the security attributes.

This test is a security test. If someone removes `HttpOnly` from the server code, every functional test still passes, but this one fails:

```text
Expected cookie "SESSION" was not (... hasProperty("sameSite", "Lax") ...) property 'sameSite' was "Strict".
```

> **The code above, ready to run**
>
> All three tests in [`CookiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_06/CookiesTest.java) pass with `mvn test -pl module-03 -Dtest=CookiesTest`. Change `sameSite("Strict")` to `sameSite("Lax")` and read the failure.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Reading a Cookie Afterwards

Sometimes you want the cookie itself, for example to send it with the next request, or to check it with JUnit assertions:

```java
@Test
void readingACookieAfterwards() {
    Cookie session = signIn().extract().detailedCookie("SESSION");

    assertTrue(session.isSecured());
    assertTrue(session.isHttpOnly());
    assertEquals("Strict", session.getSameSite());
}
```

- **`extract().detailedCookie(name)`** returns an `io.restassured.http.Cookie` with every attribute.
- **`extract().cookie(name)`** returns only the value, as a string.

A `Cookie` object can be passed straight to `cookie(..)` on the next request, as in Module 2. Module 4 covers `extract()` in depth.

## Common Pitfalls

1. **Checking only the value of a session cookie.** The value is random, and the attributes are what protect it. Check them with `detailedCookie()`.
2. **Hard-coding a generated value.** A session id changes on every request. Check its format with `matchesPattern(..)`.
3. **Expecting every cookie to be locked down.** A preference cookie such as `lang` doesn't need `HttpOnly`. Decide per cookie what the server must set.
4. **Confusing request and response cookies.** `cookie(..)` before `when()` sends a cookie. After `then()`, it checks one.
5. **Testing `Max-Age` but not `Path`.** A session cookie with `Path=/` is sent to every page on the site. Check where it is valid as well as how long.

## Wrapping Up

After `then()`, `cookie(name)` checks that a cookie exists, and `cookie(name, value)` checks its value with a string or a matcher. For a session cookie, the attributes are the security, so use `detailedCookie()` to check `Secure`, `HttpOnly`, `SameSite`, `Path` and `Max-Age` together. `extract().detailedCookie(..)` returns the whole cookie for JUnit assertions or for the next request.
