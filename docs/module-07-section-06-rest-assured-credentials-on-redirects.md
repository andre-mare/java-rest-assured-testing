# Credentials on Redirects in REST Assured: Sensitive Headers Stripped Across Origins

Credentials on Redirects in REST Assured: See how REST Assured 6.1 and later drop the Authorization and Cookie headers when a redirect leads to another origin, test that a token doesn't leak to a partner server, and turn the old behaviour back on when you must.

An API answers a request with a redirect to another server: a partner, a file store, a payment page. The client follows it, as clients do. But what does it send to that other server? Until REST Assured 6.1, the answer was: everything the original request had, including the `Authorization` header with the bearer token and the session cookie. Whoever ran the other server received a valid credential for your API. Browsers stopped doing that long ago, and REST Assured now behaves the same way.

This post follows [Section 6 of Module 7](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07) of the REST Assured course. It follows a redirect to another server and checks what that server received. The examples are in [`CredentialsOnRedirectTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_06/CredentialsOnRedirectTest.java).

## What Counts as Another Origin?

An origin is the combination of scheme, host and port: `https://bank.example:443`. Two URLs have the same origin only if all three match. So these are all cross-origin redirects:

| From | To | What changed |
| --- | --- | --- |
| `http://localhost:8080` | `http://localhost:9090` | The port |
| `https://bank.example` | `https://partner.example` | The host |
| `https://bank.example` | `http://bank.example` | The scheme |

Since REST Assured 6.1, when a followed redirect crosses to another origin, the `Authorization` and `Cookie` headers are dropped. `RedirectConfig.stripSensitiveHeadersOnCrossHostRedirect(..)` controls it, and it is `true` by default.

## The Endpoints Under Test

The bank's API runs on one port, and a partner server on another:

| Request | Response |
| --- | --- |
| `GET /api/v1/partner-redirect` on the bank | `302` with a `Location` on the partner server |
| `GET /partner/echo-credentials` on the partner | `{"authorizationReceived":..,"cookieReceived":..}` |

The partner reports whether the credentials arrived, so a test can check what crossed the redirect.

The tests send a token and a session cookie:

```java
private static RequestSpecification withCredentials() {
    return given().baseUri(api.baseUri()).port(api.port())
        .auth().oauth2("a-secret-token")
        .cookie("SESSION", "a-secret-session");
}
```

## Testing the Redirect

### Step 1: Credentials Are Dropped on a Cross-Origin Redirect

```java
@Test
void credentialsAreDroppedOnACrossOriginRedirect() {
    withCredentials()
    .when()
        .get("/api/v1/partner-redirect")
    .then()
        .statusCode(200)
        .body("authorizationReceived", equalTo(false))
        .body("cookieReceived", equalTo(false));
}
```

REST Assured follows the `302`, as it does for every `GET`. The `200` comes from the partner, and the partner says that neither the token nor the cookie arrived. The bank's credentials stayed with the bank.

> **The code above, ready to run**
>
> Both tests in [`CredentialsOnRedirectTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_06/CredentialsOnRedirectTest.java) pass with `mvn test -pl module-07 -Dtest=CredentialsOnRedirectTest`. Add `.log().all()` after `then()` to see the partner's response.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

### Step 2: The Old Behaviour Can Be Turned Back On

```java
@Test
void theOldBehaviourCanBeTurnedBackOn() {
    withCredentials()
        .config(config().redirect(redirectConfig().stripSensitiveHeadersOnCrossHostRedirect(false)))
    .when()
        .get("/api/v1/partner-redirect")
    .then()
        .statusCode(200)
        .body("authorizationReceived", equalTo(true));
}
```

With stripping turned off, the token arrives at the partner. Turn it off only for a partner you trust with the credentials, for example two services of your own on different ports that share a token. The default is the safe choice.

## What This Means for Your Tests

1. **After upgrading to REST Assured 6.1 or later**, a test that relied on credentials following a redirect starts failing with a `401` from the second server. That is the new protection at work, not a bug.
2. **To test the redirect itself**, turn following off with `redirects().follow(false)`, as in [Module 5](module-05-section-03-rest-assured-client-configuration.md), and check the status and `Location`.
3. **To test what your own API does with a redirect**, use an endpoint that reports what it received, like the partner here.

This protects the test suite's own credentials. Your API's clients need the same protection, so check that the HTTP client your application uses strips credentials on cross-origin redirects too.

## Common Pitfalls

1. **Turning stripping off globally.** Every redirect in the suite then sends credentials anywhere. Set it per request, for the one partner that needs it.
2. **Mistaking a stripped header for a server bug.** A `401` from the redirect target after an upgrade usually means the token was dropped on purpose.
3. **Assuming only host changes count.** A different port or scheme is a different origin too, so a redirect from `http` to `https` on the same host also drops the credentials.
4. **Expecting other secret headers to be stripped.** Only `Authorization` and `Cookie` are dropped. A custom header such as `X-API-Key` still follows the redirect.
5. **Testing only that the redirect works.** A test that checks the final `200` passes whether the token leaked or not. Check what the target received.

## Wrapping Up

When a followed redirect leads to another origin, a different scheme, host or port, REST Assured 6.1 and later drop the `Authorization` and `Cookie` headers, as browsers do. Before 6.1 they were sent along, leaking the credentials to whatever server the redirect pointed at. `RedirectConfig.stripSensitiveHeadersOnCrossHostRedirect(false)` restores the old behaviour, for a trusted partner only. Test what a redirect target receives, not just that the redirect works.
