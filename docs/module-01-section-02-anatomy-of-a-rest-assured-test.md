# The Anatomy of a REST Assured Test: RequestSpecification, Response and ValidatableResponse

The Anatomy of a REST Assured Test: Take a REST Assured test apart, covering the three types behind given/when/then, the syntactic sugar, the shorter forms, stored responses and a first path parameter.

The given/when/then chain reads like a sentence, but it is plain Java. Each method returns an object, and the next method is called on it. What happens when another code base writes `get(url).then().statusCode(200)` on one line, or keeps the response in a variable and checks it with JUnit's `assertEquals`? Is that still REST Assured, and is it still correct? It is, and once you know the three types behind the chain, every form you meet will make sense.

This post follows [Section 2 of Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) of the REST Assured course. It writes the same health check six ways, from the full form down to one line, and ends with a first path parameter. All the examples are in [`GivenWhenThenTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_02/GivenWhenThenTest.java).

## What is Behind given / when / then?

A REST Assured chain passes through three types:

1. **`RequestSpecification`**, returned by `given()`. It is a builder for the request: base URI, port, base path, headers, parameters and body. `when()` returns the same object, so it changes nothing.
2. **`Response`**, returned by `get(..)`, `post(..)` and the other HTTP methods. The request has been sent, and the response is now an ordinary Java object with a status code, headers and a body.
3. **`ValidatableResponse`**, returned by `then()`. Each call on it, such as `statusCode(..)`, `header(..)` or `body(..)`, checks the response at once and throws an `AssertionError` if the check fails.

Once you see these types, the shorter forms are no longer magic. They skip a step you don't need, or keep one of these objects in a variable.

## The Forms at a Glance

The test class writes the same health check in these forms:

1. **The full form:** `given()`, `when()`, `then()`, with `and()` for readability.
2. **Without `given()`:** `when().get(url)`, for a request with no set-up.
3. **The shortest form:** `get(url).then()`, on one line.
4. **A stored `RequestSpecification`:** build the request first, send it later.
5. **A stored `Response`:** send first, check later, with REST Assured or any assertion library.
6. **A path parameter:** `get("/api/v1/accounts/{id}", "acc-1001")`.

## Syntactic Sugar

Some methods exist only to make the chain read like a sentence.

### `when()`

`when()` returns the `RequestSpecification` it was called on. Removing it changes nothing, but keeping it marks where Arrange ends and Act begins.

### `and()`

`and()` works the same way after `then()`. It returns the same `ValidatableResponse`:

```java
@Test
void fullForm() {
    given()
        .baseUri(api.baseUri())
        .port(api.port())
    .when()
        .get("/api/health")
    .then()
        .statusCode(200)
    .and()                                  // and() is syntactic sugar too: it changes nothing
        .body("status", equalTo("UP"));
}
```

## The Shorter Forms

### Step 1: Leave Out `given()`

When a request needs no set-up, start with `when()`. An absolute URL needs no base URI or port:

```java
@Test
void givenCanBeLeftOutWhenTheRequestNeedsNoSetUp() {
    when()
        .get(healthUrl)                     // an absolute URL needs no baseUri or port
    .then()
        .statusCode(200);
}
```

Here `healthUrl` is built once in `@BeforeAll` as `api.baseUri() + ":" + api.port() + "/api/health"`.

### Step 2: Write It on One Line

The static `get(..)` method on `RestAssured` sends a request straight away:

```java
@Test
void theShortestForm() {
    get(healthUrl).then().statusCode(200);
}
```

This form is common in quick checks and in older code bases. Use it when the request is trivial. As soon as the request has headers or parameters, the full form reads better.

### Step 3: Keep the Request in a Variable

`given()` returns an ordinary object, so you can build it in one place and send it in another:

```java
@Test
void theRequestSpecificationIsAnOrdinaryObject() {
    RequestSpecification request = given()
        .baseUri(api.baseUri())
        .port(api.port());

    request.when().get("/api/health").then().statusCode(200);
}
```

This is the first step towards the reusable specifications of Module 5. Later sections of this module already use a small helper method that returns a configured `RequestSpecification`.

### Step 4: Keep the Response and Check It Later

A `Response` is also an ordinary object. You can check it with REST Assured, read values from it and use any assertion library:

```java
@Test
void aResponseCanBeKeptAndCheckedLater() {
    Response response = given()
        .baseUri(api.baseUri())
        .port(api.port())
    .when()
        .get("/api/health");

    // Either hand it to REST Assured's own assertions ...
    response.then().statusCode(200);

    // ... or read values from it and use any assertion library.
    assertEquals(200, response.statusCode());
    assertEquals("UP", response.path("status"));
}
```

- **`response.then()`** gives you the same `ValidatableResponse` as the chained form.
- **`response.path("status")`** reads a value with the same path language as `body(..)`. Module 4 covers extraction in depth.

> **The code above, ready to run**
>
> All six forms are passing tests in [`GivenWhenThenTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_02/GivenWhenThenTest.java). Run them with `mvn test -pl module-01 -Dtest=GivenWhenThenTest`, then rewrite one form as another and check that it still passes.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## A First Path Parameter

Most real endpoints have a variable part, such as an account id. REST Assured fills `{placeholders}` in the path from the extra arguments of `get(..)`:

```java
@Test
void pathParametersFillPlaceholdersInTheUrl() {
    given()
        .baseUri(api.baseUri())
        .port(api.port())
    .when()
        .get("/api/v1/accounts/{id}", "acc-1001")   // {id} is replaced by "acc-1001"
    .then()
        .statusCode(200)
        .body("owner", equalTo("Anna de Vries"));
}
```

The request goes to `/api/v1/accounts/acc-1001`. REST Assured also URL-encodes the value, which string concatenation would not do for you. Module 2 covers named path parameters, query parameters and form parameters in full.

## Choosing a Form

1. **Prefer the full form in shared code.** given/when/then shows where the request, the action and the checks start, which helps readers who don't know REST Assured.
2. **Use the short form only for trivial requests.** `get(url).then()` is fine for a health check, but not once the request has set-up.
3. **Store a `RequestSpecification` to remove repetition.** It is the simplest way to share a base URI and port across tests.
4. **Store a `Response` when you need its values.** Do this, for example, to use an id from one response in the next request, or to use AssertJ.
5. **Use path parameters, not string concatenation.** `get("/accounts/{id}", id)` is easier to read and encodes the value for you.

## Wrapping Up

`given()` builds a `RequestSpecification`, an HTTP method sends it and returns a `Response`, and `then()` turns that into a `ValidatableResponse` that checks each expectation as you add it. `when()` and `and()` are only there to read well, and every shorter form is the same chain with a step left out or stored in a variable.
