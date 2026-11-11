# Sessions and CSRF in REST Assured: SessionFilter, htmlPath and a Form Login

Sessions and CSRF in REST Assured: Test a browser-style login, covering reading a CSRF token from an HTML form with htmlPath(), keeping JSESSIONID across requests with a SessionFilter, setting the session id explicitly, and a missing or reused CSRF token.

Not every protected application uses tokens. Online banking portals, admin consoles and many older web applications log users in with an HTML form and keep them logged in with a session cookie. The form carries a hidden CSRF token, which proves that the login came from the bank's own page and not from a malicious site. To test such an application, a test must behave like a browser: load the form, read the token, post the credentials, and send the session cookie with every request after that.

This post follows [Section 4 of Module 7](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07) of the REST Assured course. It logs in through a form with a CSRF token, uses the session, and tests what happens without one. The examples are in [`SessionAndCsrfTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_04/SessionAndCsrfTest.java).

## What are Sessions and CSRF Tokens?

1. **A session** is state the server keeps for a logged-in user. The server gives the browser a session id in a cookie, usually `JSESSIONID`, and the browser sends it with every request.
2. **A CSRF token** protects against cross-site request forgery: a malicious page that makes the user's browser post a form to the bank. The bank puts a random token in its own form, and rejects posts without it. Another site can't read the token, so it can't forge the post.

REST Assured has two tools for this:

- **`SessionFilter`** remembers the session id from a response and sends it with every later request that uses the same filter.
- **`htmlPath()`** reads values from an HTML response, such as the CSRF token in a hidden input.

## The Portal Under Test

The bank's portal has three endpoints:

| Request | Response |
| --- | --- |
| `GET /portal/login` | An HTML login form with a hidden `_csrf` token |
| `POST /portal/login` | `302` to `/portal/home` with `Set-Cookie: JSESSIONID=..; Path=/portal; HttpOnly; SameSite=Lax`, or `403` for a missing or reused CSRF token |
| `GET /portal/home` | `{"user":"anna"}` with a valid session, or `302` to `/portal/login` |

The login form looks like this:

```html
<html>
  <head><title>Sign in</title></head>
  <body>
    <form id="login" method="post" action="/portal/login">
      <input type="hidden" name="_csrf" value="c600134e-8107-4c83-a9ee-0cd20a1355b0"/>
      <input type="text" name="username"/>
      <input type="password" name="password"/>
      <input type="submit" value="Sign in"/>
    </form>
  </body>
</html>
```

Each CSRF token can be used once. The user is `anna` with the password `correct horse`.

## A Fresh Session per Test

```java
private SessionFilter session;

@BeforeEach
void newSession() {
    session = new SessionFilter();
}

private RequestSpecification portal() {
    return given().baseUri(api.baseUri()).port(api.port()).filter(session);
}
```

The filter is an instance field, created before each test. Every request a test sends through `portal()` shares that test's session, and no test sees another test's login.

## Logging In

### Step 1: Read the CSRF Token

```java
private String csrfTokenFromTheLoginPage() {
    return portal()
        .when().get("/portal/login")
        .then().statusCode(200)
        .extract().htmlPath().getString("**.find { it.@name == '_csrf' }.@value");
}
```

`htmlPath()` parses the page leniently, like `XmlPath` on HTML in [Module 3](module-03-section-07-rest-assured-xml-and-html.md). The depth-first search `**.find { it.@name == '_csrf' }` finds the input by its name, wherever it is on the page, and `.@value` reads its value.

### Step 2: Post the Form

```java
private void logIn(String csrf) {
    portal()
        .formParam("_csrf", csrf)
        .formParam("username", "anna")
        .formParam("password", "correct horse")
    .when()
        .post("/portal/login")
    .then()
        .statusCode(302)                                         // POST redirects aren't followed by default
        .header("Location", "/portal/home")
        .cookie("JSESSIONID", notNullValue());
}
```

The login answers with a redirect, which REST Assured doesn't follow for a `POST`, as [Module 5](module-05-section-03-rest-assured-client-configuration.md) showed. That is useful here: the test can check the redirect target and the new session cookie. The `SessionFilter` sees the `JSESSIONID` cookie and remembers it.

### Step 3: Use the Session

```java
@Test
void logInAndUseTheSession() {
    logIn(csrfTokenFromTheLoginPage());
    assertNotNull(session.getSessionId());

    portal()
    .when()
        .get("/portal/home")
    .then()
        .statusCode(200)
        .body("user", equalTo("anna"));
}
```

The request to `/portal/home` carries no cookie in the test code, but the filter adds `JSESSIONID`. `session.getSessionId()` returns the id the filter remembered.

> **The code above, ready to run**
>
> All five tests in [`SessionAndCsrfTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_04/SessionAndCsrfTest.java) pass with `mvn test -pl module-07 -Dtest=SessionAndCsrfTest`. Remove `.filter(session)` from `portal()` and watch the logged-in tests fail.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

## Without and Around the Filter

### Without a Session You Are Sent to the Login Page

```java
@Test
void withoutASessionYouAreSentToTheLoginPage() {
    given().baseUri(api.baseUri()).port(api.port())
        .redirects().follow(false)
    .when()
        .get("/portal/home")
    .then()
        .statusCode(302)
        .header("Location", "/portal/login");
}
```

`redirects().follow(false)` keeps the `302` visible. Following it would show the login page with a `200`, which hides what really happened.

### Setting the Session Id Explicitly

```java
@Test
void theSessionIdCanBeSetExplicitly() {
    logIn(csrfTokenFromTheLoginPage());

    given().baseUri(api.baseUri()).port(api.port())
        .sessionId(session.getSessionId())                       // sends JSESSIONID=<id>
    .when()
        .get("/portal/home")
    .then()
        .body("user", equalTo("anna"));
}
```

`sessionId(..)` sends the session cookie without a filter. It is useful when the session id comes from somewhere else, such as a login done once for a whole class.

## CSRF Protection

### A Missing CSRF Token Is Rejected

```java
@Test
void aMissingCsrfTokenIsRejected() {
    portal()
        .formParam("username", "anna")
        .formParam("password", "correct horse")
    .when()
        .post("/portal/login")
    .then()
        .statusCode(403);
}
```

The credentials are right, but the post doesn't prove it came from the bank's form.

### A CSRF Token Works Only Once

```java
@Test
void aCsrfTokenWorksOnlyOnce() {
    String csrf = csrfTokenFromTheLoginPage();
    logIn(csrf);

    portal()
        .formParam("_csrf", csrf)
        .formParam("username", "anna")
        .formParam("password", "correct horse")
    .when()
        .post("/portal/login")
    .then()
        .statusCode(403);
}
```

A token that could be reused could be replayed. The test proves it can't.

## Doing It With auth().form(..)

REST Assured can do these steps for you with `auth().form(user, password, formAuthConfig)`. With a `FormAuthConfig` and a `CsrfConfig`, it loads the login page, finds the CSRF field, and posts the form. That is convenient for a standard login page. Doing the steps by hand, as this class does, shows what happens and works with any page, and it lets you test the CSRF protection itself.

## Common Pitfalls

1. **A static `SessionFilter`.** Every test then shares one session, and the result depends on the order the tests run in. Create a filter per test.
2. **Following the login redirect.** The test then sees the home page or the login page, and can't tell which step failed. Check the `302` and its `Location`.
3. **Reusing a CSRF token across tests.** Each token works once. Read a new one before each login.
4. **A session cookie with a different name.** `SessionFilter` and `sessionId(..)` use `JSESSIONID` by default. For another name, set it with `SessionConfig`.
5. **Testing only a successful login.** A login that works without a CSRF token is a vulnerability. Test the missing and the reused token.

## Wrapping Up

A browser-style login needs three steps in a test: read the CSRF token from the form with `htmlPath()`, post the form with the token and the credentials, and keep the session cookie for the requests after that. A `SessionFilter`, created per test, remembers `JSESSIONID` and sends it automatically, and `sessionId(..)` sends it without a filter. Test what must fail too: no session, a missing CSRF token and a reused one.
