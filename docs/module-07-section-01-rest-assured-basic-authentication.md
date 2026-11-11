# Basic Authentication in REST Assured: Challenged Versus Preemptive

Basic Authentication in REST Assured: Test HTTP Basic authentication, covering the 401 challenge, challenged Basic with auth().basic(..), preemptive Basic with auth().preemptive().basic(..), a server that never challenges, and wrong credentials.

HTTP Basic authentication is the oldest scheme on the web: a user name and password, encoded in an `Authorization` header. REST Assured supports it in one line. But there are two ways to send those credentials, and the one that looks natural, `auth().basic(..)`, doesn't send them at first. Against most APIs that is fine. Against an endpoint that hides itself from strangers, the credentials are never sent at all, and the test fails with a status that seems to make no sense.

This post is the first in the series for [Module 7 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07), which covers testing protected APIs, including everything that must happen when credentials are missing, wrong or not enough. It compares challenged and preemptive Basic authentication. The examples are in [`BasicAuthenticationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_01/BasicAuthenticationTest.java).

## What is HTTP Basic Authentication?

A client sends `Authorization: Basic <base64 of user:password>`. The server can ask for it with a **challenge**: a `401 Unauthorized` response with a `WWW-Authenticate: Basic realm=".."` header. REST Assured can send the credentials in two ways:

1. **Challenged**, with `auth().basic(user, password)`. REST Assured sends the request without credentials first. Only when the server answers `401` with a `Basic` challenge does it send the request again, with credentials.
2. **Preemptive**, with `auth().preemptive().basic(user, password)`. REST Assured sends the `Authorization` header with the first request.

Challenged authentication costs an extra round trip, and it only works when the server challenges. Preemptive is what APIs usually need.

Base64 is an encoding, not encryption. Basic authentication must always travel over HTTPS, which [Section 5](module-07-section-05-rest-assured-tls-and-mutual-tls.md) covers.

## The API Under Test

Module 7 tests a protected bank: an OAuth 2.0 authorization server and the resource server it protects, in one process, built on the JDK only. This section uses its two admin endpoints, both protected with the user `admin` and the password `changeit`:

| Endpoint | Without credentials |
| --- | --- |
| `GET /admin/health` | `401` with `WWW-Authenticate: Basic realm="admin"`, a challenge |
| `GET /admin/report` | `404`, to hide that the endpoint exists |

Hiding a protected endpoint behind a `404` is a common practice: a stranger can't tell an admin page from a page that doesn't exist. It also means the endpoint never challenges.

## Challenged and Preemptive Basic

### Step 1: The Server Challenges

```java
@Test
void withoutCredentialsTheServerChallenges() {
    bank()
    .when()
        .get("/admin/health")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", "Basic realm=\"admin\"");
}
```

The body is an RFC 9457 problem: `{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Admin credentials are required",...}`. The `WWW-Authenticate` header is what makes it a challenge: it tells the client which scheme to use.

### Step 2: Challenged Basic Answers the Challenge

```java
@Test
void challengedBasicAnswersTheChallenge() {
    bank().auth().basic("admin", "changeit").when().get("/admin/health").then().statusCode(200);
}
```

Two requests go over the wire: one without credentials, which gets the challenge, and one with them, which gets `200`. The test sees only the second response.

### Step 3: Preemptive Basic Sends Credentials Straight Away

```java
@Test
void preemptiveBasicSendsCredentialsStraightAway() {
    bank().auth().preemptive().basic("admin", "changeit").when().get("/admin/health").then().statusCode(200);
}
```

One request, with the `Authorization` header from the start.

> **The code above, ready to run**
>
> All six tests in [`BasicAuthenticationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_01/BasicAuthenticationTest.java) pass with `mvn test -pl module-07 -Dtest=BasicAuthenticationTest`. You need JDK 26 and Maven 3.9, and nothing else.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

## A Server That Never Challenges

### Step 4: Challenged Basic Fails

```java
@Test
void challengedBasicFailsWhenTheServerNeverChallenges() {
    bank()
        .auth().basic("admin", "changeit")
    .when()
        .get("/admin/report")
    .then()
        .statusCode(404);                                        // the credentials were never sent
}
```

REST Assured sends the first request without credentials and waits for a challenge. The server answers `404`, which isn't a challenge, so REST Assured stops there. The credentials were correct, but they never left the client.

This is the confusing case: a test with the right password gets a `404`, and nothing in the test says why.

### Step 5: Preemptive Basic Works There

```java
@Test
void preemptiveBasicWorksThere() {
    bank()
        .auth().preemptive().basic("admin", "changeit")
    .when()
        .get("/admin/report")
    .then()
        .statusCode(200)
        .body("report", equalTo("daily"));
}
```

The credentials are in the first request, so the server never has to ask.

## Wrong Credentials

```java
@Test
void wrongCredentials() {
    bank().auth().preemptive().basic("admin", "guess").when().get("/admin/health").then().statusCode(401);
}
```

Always test the wrong password too. A test with the right credentials proves that the endpoint can be opened. Only a test with wrong ones proves that it is locked. [Section 3](module-07-section-03-rest-assured-negative-security-tests.md) takes that idea much further.

## Common Pitfalls

1. **Challenged Basic against an endpoint that hides itself.** The credentials are never sent, and the test gets a `404` or a `403`. Use `auth().preemptive().basic(..)`.
2. **Assuming `auth().basic(..)` sends one request.** It sends two whenever the server challenges. That matters for logs, rate limits and endpoints with side effects.
3. **Testing only the right credentials.** An endpoint that accepts every password passes that test. Add a test with a wrong one.
4. **Basic authentication over plain HTTP in a real environment.** The password is only base64-encoded. Test against HTTPS outside your machine.
5. **Credentials in logs.** A logged `Authorization: Basic ..` header gives away the password. Blacklist it, as [Module 5](module-05-section-04-rest-assured-secrets-in-logs.md) shows.

## Wrapping Up

HTTP Basic authentication sends a user name and password in the `Authorization` header. `auth().basic(..)` waits for a `401` challenge before it sends them, which costs a round trip and fails against servers that never challenge, such as endpoints that answer `404` to hide themselves. `auth().preemptive().basic(..)` sends them with the first request, which is what APIs usually need. Test the challenge, the right credentials and the wrong ones.
