# Parsers and Failure Listeners in REST Assured: Wrong Content Types and Evidence on Failure

Parsers and Failure Listeners in REST Assured: Read a JSON body sent as text/plain with parser(..) per response or registerParser(..) in a response specification, learn why a spec's parser doesn't reach checks chained after it, and collect evidence with a ResponseValidationFailureListener.

REST Assured decides how to read a body from its content type: JSON for `application/json`, XML for `application/xml`. Most APIs get that right. Some don't. A legacy service sends perfectly good JSON labelled `text/plain`, and every body path fails with an error about unsupported content types. You can't change the service, but you can tell REST Assured how to read it. And when a check does fail, wouldn't it be useful to capture the response that caused it, for the test report or a bug ticket?

This post follows [Section 5 of Module 8](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-08) of the REST Assured course. It reads a mislabelled body with a parser, and collects evidence with a failure listener. The examples are in [`ParsersAndListenersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_05/ParsersAndListenersTest.java).

## What are Parsers and Failure Listeners?

1. **A parser** tells REST Assured how to read a body for path expressions. `Parser.JSON`, `Parser.XML`, `Parser.HTML` and `Parser.TEXT` are available. You can map a content type to a parser:
   - **per response**, with `then().parser(contentType, parser)`,
   - **in a response specification**, with `ResponseSpecBuilder.registerParser(..)`,
   - **globally**, with `RestAssured.registerParser(..)`.
2. **A failure listener** is called when a validation fails, with the request specification, the response specification and the response. It is set with `FailureConfig`.

## The Endpoint Under Test

`GET /api/v1/legacy/fx-rates` returns exchange rates. The body is JSON, but the content type is `text/plain`:

```text
HTTP/1.1 200 OK
Content-Type: text/plain

{"base":"EUR","rates":{"GBP":0.85,"USD":1.09,"SEK":11.42}}
```

## Without Help, the Body Is Just Text

```java
@Test
void withoutHelpTheBodyIsJustText() {
    legacy()
    .when()
        .get("/api/v1/legacy/fx-rates")
    .then()
        .contentType("text/plain")
        .body(equalTo("{\"base\":\"EUR\",\"rates\":{\"GBP\":0.85,\"USD\":1.09,\"SEK\":11.42}}"));
}
```

A matcher on the whole body works, because it compares text. A body path doesn't. `body("base", equalTo("EUR"))` fails with:

```text
java.lang.IllegalStateException: Expected response body to be verified as JSON, HTML or XML but content-type 'text/plain' is not supported out of the box.
Try registering a custom parser using:
   RestAssured.registerParser("text/plain", <parser type>);
Content was:
{"base":"EUR","rates":{"GBP":0.85,"USD":1.09,"SEK":11.42}}
```

The error suggests a global parser. There are better places for it.

## Parsing Text as JSON

### Step 1: For One Response

```java
@Test
void parseTextAsJsonForOneResponse() {
    legacy()
    .when()
        .get("/api/v1/legacy/fx-rates")
    .then()
        .parser("text/plain", Parser.JSON)
        .body("base", equalTo("EUR"))
        .body("rates.USD", equalTo(1.09f));
}
```

`parser(..)` after `then()` applies to the checks that follow it. The response itself is unchanged: its content type is still `text/plain`, so a content type check still tells the truth. That is the advantage over repairing the response with a filter, as [Section 2](module-08-section-02-rest-assured-useful-filters.md) did.

### Step 2: In a Response Specification

```java
@Test
void orInAResponseSpecification() {
    ResponseSpecification legacyJson = new ResponseSpecBuilder()
        .registerParser("text/plain", Parser.JSON)
        .expectStatusCode(200)
        .expectBody("rates.SEK", equalTo(11.42f))
        .build();

    legacy()
    .when()
        .get("/api/v1/legacy/fx-rates")
    .then()
        .spec(legacyJson);
}
```

The parser and the body expectations live together in the specification, from [Module 5](module-05-section-02-rest-assured-response-specifications.md).

There is a catch. A parser registered in a specification applies to **that specification's own expectations**. Merging the specification with `spec(..)` doesn't carry the parser over to the chain. So this fails with the same `IllegalStateException` as before:

```java
.then()
    .spec(legacyJson)
    .body("base", equalTo("EUR"));                       // fails: the chain doesn't have the parser
```

Put the body expectations in the specification, or use `parser(..)` on the response.

> **The code above, ready to run**
>
> All four tests in [`ParsersAndListenersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_05/ParsersAndListenersTest.java) pass with `mvn test -pl module-08 -Dtest=ParsersAndListenersTest`. Chain a `body("base", ..)` after `spec(legacyJson)` to see the catch for yourself.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 8 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/README.md)

## Collecting Evidence on Failure

```java
@Test
void aFailureListenerSeesTheResponseOfAFailedTest() {
    List<String> evidence = new ArrayList<>();
    ResponseValidationFailureListener collectEvidence =
            (requestSpec, responseSpec, response) -> evidence.add(response.statusCode() + " " + response.asString());

    assertThrows(AssertionError.class, () ->
        legacy()
            .config(config().failureConfig(failureConfig().failureListeners(collectEvidence)))
        .when()
            .get("/api/v1/legacy/fx-rates")
        .then()
            .statusCode(500));                                   // fails: the status is 200

    assertEquals(1, evidence.size());
    assertEquals("200 {\"base\":\"EUR\",\"rates\":{\"GBP\":0.85,\"USD\":1.09,\"SEK\":11.42}}", evidence.getFirst());
}
```

- **`ResponseValidationFailureListener`** is a functional interface. REST Assured calls it once, when validation fails, before the `AssertionError` is thrown.
- **`FailureConfig`** holds the listeners. Put it in a specification's config so every test gets it.
- **The listener sees the whole response**, so it can attach the body to a test report, write it to a file, or add it to a ticket.

Unlike `log().ifValidationFails()`, which prints to a stream, a listener can do anything with the evidence: attach it to an Allure or JUnit report, or upload it to wherever your team looks first. It is called only on failure, so a passing suite does no extra work.

## Common Pitfalls

1. **A global parser for one endpoint.** `RestAssured.registerParser("text/plain", Parser.JSON)` makes every plain-text body JSON, including real text. Use `parser(..)` or a specification, and `reset()` if you must go global.
2. **Expecting a spec's parser in the chain.** `spec(..)` doesn't pass the parser on, so a `body(path, ..)` chained after it fails on `text/plain`. Keep the body checks in the specification.
3. **Repairing the content type when a parser would do.** A filter that rewrites the content type hides it from the test. A parser leaves the response as it was.
4. **A listener that throws.** It runs while a test is already failing, and an exception from it can hide the real failure. Keep listeners simple and defensive.
5. **Evidence with personal data.** A listener that saves response bodies saves whatever they contain. Mask them, as the filter in Section 2 does.

## Wrapping Up

REST Assured picks a parser from the content type, so a JSON body sent as `text/plain` can't be read with a path. `parser(contentType, Parser.JSON)` after `then()` fixes that for one response without changing it, and `registerParser(..)` does it in a response specification, but only for that specification's own expectations. A `ResponseValidationFailureListener`, set with `FailureConfig`, is called when validation fails and sees the response, so it can attach evidence wherever your team needs it.
