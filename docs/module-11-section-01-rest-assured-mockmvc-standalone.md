# RestAssuredMockMvc in Standalone Mode: Testing a Spring Controller Without a Server

RestAssuredMockMvc in Standalone Mode: Test a Spring MVC controller with REST Assured's syntax and no server, no socket and no Spring context, using RestAssuredMockMvc's standaloneSetup(..), and learn what that mode leaves out.

Every API in the course so far ran on a real HTTP server, started by the test. A Spring Boot application can be tested that way too, and [Section 3](module-11-section-03-rest-assured-spring-boot-random-port.md) does. But starting a whole application takes seconds, and for a test that only checks what a controller returns, most of that start-up is wasted. Spring's `MockMvc` calls the controller through Spring MVC without any network at all. REST Assured's `spring-mock-mvc` module puts the familiar `given().when().then()` on top of it.

This post is the first in the series for [Module 11 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-11), which covers the ways to test a Spring Boot API with REST Assured. It tests a controller in standalone mode, the fastest and the most limited of them. The examples are in [`StandaloneMockMvcTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_01/StandaloneMockMvcTest.java).

## What is RestAssuredMockMvc?

`RestAssuredMockMvc` is REST Assured for Spring MVC's `MockMvc`:

1. **The same syntax**: `given()`, `when()`, `then()`, body paths and matchers, all as before. Only the static import changes, to `io.restassured.module.mockmvc.RestAssuredMockMvc.given`.
2. **No HTTP**: `MockMvc` calls Spring's `DispatcherServlet` directly, with mock request and response objects. No server starts and nothing goes over a socket.
3. **Three ways to set it up**: `standaloneSetup(controller)` with no Spring context, `webAppContextSetup(..)` with a context, or `mockMvc(..)` with a `MockMvc` that Spring built, as in [Section 2](module-11-section-02-rest-assured-webmvctest-spring-security.md).

In **standalone** mode, you build the controller yourself, with `new`. Spring MVC handles routing, JSON conversion and status codes, but nothing else from the application runs: no security, no configuration properties, no Spring Boot error handling.

## The Application Under Test

Module 11 tests a Spring Boot 4.1 accounts service with one controller:

| Request | Answer |
| --- | --- |
| `GET /api/v1/accounts` | The accounts, as a JSON array |
| `GET /api/v1/accounts/{id}` | One account, or `404` |
| `POST /api/v1/accounts` | `201` with a `Location`, or `422` for a missing IBAN, owner or currency |

The service starts with two accounts:

```json
[{"id":"acc-1001","iban":"NL91ABNA0417164300","owner":"Anna de Vries","currency":"EUR","balance":"1250.50"},
 {"id":"acc-1002","iban":"NL69INGB0123456789","owner":"Jan Jansen","currency":"EUR","balance":"15000.00"}]
```

The controller gets its `AccountService` through its constructor, which is what makes standalone mode easy:

```java
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }
    ...
}
```

## Testing the Controller

Every test starts from the same specification:

```java
private static MockMvcRequestSpecification accounts() {
    return given().standaloneSetup(new AccountController(new AccountService()));
}
```

Each call builds a new controller with a new service, so every test starts with the same two accounts.

### Step 1: List the Accounts

```java
@Test
void listAccounts() {
    accounts()
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("$", hasSize(2))
        .body("owner[0]", equalTo("Anna de Vries"));               // "owner" collects the owner of every element
}
```

The body is a JSON array at the root, so `$` is the list, and `owner[0]` takes the first owner from the collected list, as in [Module 3](module-03-section-01-rest-assured-gpath-for-json.md).

### Step 2: Read One Account

```java
@Test
void readOneAccount() {
    accounts()
    .when()
        .get("/api/v1/accounts/{id}", "acc-1002")
    .then()
        .statusCode(200)
        .body("balance", equalTo("15000.00"));
}
```

Path parameters work exactly as in [Module 2](module-02-section-01-rest-assured-path-parameters.md).

### Step 3: An Unknown Account

```java
@Test
void anUnknownAccount() {
    accounts().when().get("/api/v1/accounts/{id}", "acc-9999").then().statusCode(404);
}
```

The test checks only the status, and that is all it can check here. In standalone mode, the controller's `ResponseStatusException` becomes a `404` with an **empty body and no content type**. The reason, `Account acc-9999 does not exist`, exists only as the servlet's error message. The RFC 9457 problem details that the real application returns come from Spring Boot, which standalone mode doesn't start.

> **The code above, ready to run**
>
> All five tests in [`StandaloneMockMvcTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_01/StandaloneMockMvcTest.java) pass with `mvn test -pl module-11 -Dtest=StandaloneMockMvcTest`. They need no port and no Spring context, so they run in a fraction of a second.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 11 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/README.md)

## Opening Accounts

### Step 4: Open an Account

```java
@Test
void openAnAccount() {
    accounts()
        .contentType(ContentType.JSON)
        .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Sanne Smit\", \"currency\": \"EUR\"}")
    .when()
        .post("/api/v1/accounts")
    .then()
        .statusCode(201)
        .header("Location", "/api/v1/accounts/acc-1003")
        .body("balance", equalTo("0.00"));
}
```

Because every test has its own service, the next id is predictable, and the test can check the exact `Location`.

### Step 5: An Invalid Account

```java
@Test
void anInvalidAccount() {
    accounts()
        .contentType(ContentType.JSON)
        .body("{\"owner\": \"No IBAN\"}")
    .when()
        .post("/api/v1/accounts")
    .then()
        .statusCode(422);
}
```

As with the `404`, the body is empty in standalone mode.

## What Standalone Mode Leaves Out

| Part of the application | Standalone | [Web slice](module-11-section-02-rest-assured-webmvctest-spring-security.md) | [Random port](module-11-section-03-rest-assured-spring-boot-random-port.md) |
| --- | --- | --- | --- |
| Routing, JSON conversion, status codes | Yes | Yes | Yes |
| Spring Security | No | Yes | Yes |
| Spring Boot problem details | No | Yes | Yes |
| Real HTTP, servlet container, token validation | No | No | Yes |
| Speed | Fastest | Fast | Slowest |

Standalone mode tests the controller's own logic. Use it for many quick tests of mapping, validation and status codes, and leave security and error formats to the other two styles.

## Common Pitfalls

1. **The wrong static import.** `io.restassured.RestAssured.given` sends real HTTP and needs a server. Import `io.restassured.module.mockmvc.RestAssuredMockMvc.given`.
2. **Checking problem details in standalone mode.** The body of an error is empty. Check error formats in a web slice or against the real server.
3. **Concluding that an endpoint is open.** Without Spring Security, every request succeeds. A passing standalone test says nothing about who may call the endpoint.
4. **A controller that can't be built with `new`.** Field injection with `@Autowired` makes standalone mode awkward. Constructor injection, as here, makes it trivial.
5. **Sharing a controller between tests.** State in the service leaks from one test into the next. Build a new one per test.

## Wrapping Up

`RestAssuredMockMvc` gives REST Assured's syntax on top of Spring's `MockMvc`, which calls the `DispatcherServlet` directly, with no server and no socket. In standalone mode, `standaloneSetup(..)` takes a controller you build yourself, so not even a Spring context starts. That makes these the fastest web tests, for routing, JSON, validation and status codes. It also leaves out everything the context adds, such as security and problem details, which the next two sections test.
