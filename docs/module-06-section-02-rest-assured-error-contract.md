# An Error Contract in REST Assured: RFC 9457 Problems With JSON Schema if/then/else

An Error Contract in REST Assured: Check every error response against one RFC 9457 problem schema, use draft 2020-12 if/then/else so a 422 must list field errors and other statuses must not, and test the schema itself.

Error responses get less attention than successful ones. Each endpoint's errors are often written by a different person at a different time, and clients pay for it: one error has a `message`, another an `error`, a third an HTML page. A client that wants to show the user what went wrong needs every error to have the same shape. That shape is a contract, and a contract can be tested, once, for every error the API returns.

This post follows [Section 2 of Module 6](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-06) of the REST Assured course. It writes one schema for RFC 9457 problem details, combines it with a response specification, and checks four different errors with it. The examples are in [`ErrorContractTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_02/ErrorContractTest.java).

## What is an Error Contract?

[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) defines problem details, a standard JSON shape for HTTP errors, with the content type `application/problem+json`:

| Field | Meaning |
| --- | --- |
| `type` | A URI that identifies the kind of problem, `about:blank` when there is none |
| `title` | A short, human-readable summary |
| `status` | The HTTP status code, repeated in the body |
| `detail` | An explanation of this occurrence |
| `instance` | The path of the request that failed |

APIs may add their own fields. The payment initiation API adds `errors`, a list of field errors, to validation failures. The contract for this API is:

1. **Every error** is `application/problem+json` with at least `type`, `title` and `status`.
2. **A `422`** must list the invalid fields in `errors`.
3. **Any other status** must not have `errors`.

## The Errors Under Test

The API from [Section 1](module-06-section-01-rest-assured-json-schema-validation.md) returns four kinds of error:

| Request | Status | Body |
| --- | --- | --- |
| `GET /domestic-payments/pmt-999` | `404` | `{"type":"about:blank","title":"Not Found","status":404,"detail":"Payment pmt-999 does not exist",...}` |
| `POST` with `{ not json` | `400` | `{"type":"about:blank","title":"Bad Request","status":400,"detail":"The body is not valid JSON",...}` |
| `POST` with `text/plain` | `415` | `{"type":"about:blank","title":"Unsupported Media Type","status":415,...}` |
| `POST` with invalid fields | `422` | The problem, plus `errors` |

A `422` looks like this:

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "The request has invalid fields",
  "instance": "/pisp/v1/domestic-payments",
  "errors": [
    {"field": "debtor", "detail": "is required and must be an object"},
    {"field": "creditor", "detail": "is required and must be an object"},
    {"field": "amount.value", "detail": "is required and must match [0-9]{1,13}\\.[0-9]{2}"},
    {"field": "amount.currency", "detail": "is required and must match [A-Z]{3}"}
  ]
}
```

## The Problem Schema

[`problem.schema.json`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/resources/schemas/problem.schema.json) describes the contract:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["type", "title", "status"],
  "properties": {
    "type": { "type": "string" },
    "title": { "type": "string" },
    "status": { "type": "integer", "minimum": 400, "maximum": 599 },
    "detail": { "type": "string" },
    "instance": { "type": "string" },
    "errors": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["field", "detail"],
        "properties": { "field": { "type": "string" }, "detail": { "type": "string" } }
      }
    }
  },
  "if": { "properties": { "status": { "const": 422 } } },
  "then": { "required": ["errors"] },
  "else": { "not": { "required": ["errors"] } }
}
```

The last three keywords are the interesting part. `if`, `then` and `else`, available since draft 7, make one rule depend on another:

- **`if`**: the status is `422`.
- **`then`**: `errors` is required.
- **`else`**: `errors` must not be there.

`minItems: 1` adds that a `422` can't have an empty list of errors. Without `$schema` set to a modern draft, REST Assured would use its older validator, which doesn't know `if`/`then`/`else` or `const`.

## A Specification for Every Error

The class combines the content type and the schema in a response specification, as in [Module 5](module-05-section-02-rest-assured-response-specifications.md):

```java
problem = new ResponseSpecBuilder()
    .expectContentType("application/problem+json")
    .expectBody(matchesJsonSchemaInClasspath("schemas/problem.schema.json"))
    .build();
```

`expectBody(matcher)` without a path passes the whole body to the matcher, just like `body(matcher)`. Now `spec(problem)` checks any error in one call.

## Checking Errors

### Step 1: Not Found, Malformed JSON and the Wrong Content Type

```java
@Test
void notFound() {
    pisp().when().get("/domestic-payments/{id}", "pmt-999").then().statusCode(404).spec(problem);
}

@Test
void malformedJson() {
    pisp().contentType(ContentType.JSON).body("{ not json").when().post("/domestic-payments")
        .then().statusCode(400).spec(problem);
}

@Test
void wrongContentType() {
    pisp().contentType(ContentType.TEXT).body("hello").when().post("/domestic-payments")
        .then().statusCode(415).spec(problem);
}
```

Each test states the status it expects, and the specification checks the shape. Three different code paths in the API, one contract.

### Step 2: Validation Errors List the Fields

```java
@Test
void validationErrorsListTheFields() {
    pisp()
        .contentType(ContentType.JSON)
        .body("{\"instructionId\": \"INV-1\", \"amount\": {\"value\": \"1\", \"currency\": \"euro\"}}")
    .when()
        .post("/domestic-payments")
    .then()
        .statusCode(422)
        .spec(problem)
        .body("errors.field", hasItems("debtor", "creditor", "amount.value", "amount.currency"));
}
```

The schema guarantees that `errors` is there, so the test can go further and check which fields are reported.

> **The code above, ready to run**
>
> All five tests in [`ErrorContractTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_02/ErrorContractTest.java) pass with `mvn test -pl module-06 -Dtest=ErrorContractTest`. Change `"minimum": 400` to `500` in the schema and watch every test of a 4xx error fail.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 6 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/README.md)

## Testing the Schema Itself

A schema is code. If it is too lenient, every test that uses it passes for the wrong reason. So the schema gets its own tests:

```java
@Test
void theSchemaItselfIsStrictAboutErrors() {
    assertThat("{\"type\":\"about:blank\",\"title\":\"Unprocessable Content\",\"status\":422}",
            not(matchesJsonSchemaInClasspath("schemas/problem.schema.json")));
    assertThat("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
                    + "\"errors\":[{\"field\":\"id\",\"detail\":\"unknown\"}]}",
            not(matchesJsonSchemaInClasspath("schemas/problem.schema.json")));
}
```

- **A `422` without `errors`** must be rejected. Without `not(..)`, the report says `$: required property 'errors' not found`.
- **A `404` with `errors`** must be rejected too.

These two strings are the cases the `if`/`then`/`else` exists for. If someone simplifies the schema and drops the condition, this test fails.

## Common Pitfalls

1. **Checking the status but not the shape.** A `404` with an HTML body passes `statusCode(404)`. Add `spec(problem)` to every error test.
2. **A status code in the error specification.** Errors have many statuses. Keep the status in the test and the shape in the specification.
3. **A schema nobody tests.** A typo in `required` makes the schema accept anything. Test it with examples it must reject.
4. **Conditional rules without a modern `$schema`.** The older validator silently ignores `if`/`then`/`else` and `const`, so a `422` without `errors` passes. Declare draft 2020-12.
5. **Relying on `ContentType.JSON` for problems.** It accepts `application/problem+json`, but also plain `application/json`. Use the exact string when the problem media type is part of the contract.

## Wrapping Up

An error contract makes every error the same shape, so clients can handle them all in one way. RFC 9457 problem details are a ready-made shape, and one JSON Schema describes them. Draft 2020-12's `if`/`then`/`else` expresses rules that depend on the status, such as "a 422 must list its field errors". Combined with the content type in a response specification, `spec(problem)` checks any error in one call. Test the schema too, with examples it must reject.
