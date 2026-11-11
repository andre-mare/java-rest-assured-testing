# JSON Schema Validation in REST Assured: matchesJsonSchemaInClasspath and Draft 2020-12

JSON Schema Validation in REST Assured: Check the shape of a whole response with JSON Schema draft 2020-12, covering matchesJsonSchemaInClasspath(..), a response that breaks the contract, an inline schema, and validating JSON that didn't come over HTTP.

A value check such as `body("status", equalTo("RCVD"))` tests one field. It says nothing about the other fifteen. What if the amount quietly changed from a string to a number? What if a required field disappeared, or the server started sending a field that leaks internal data? Every test that reads only the status would still pass. To catch that, you need a check of the whole shape of the response: which fields exist, what type each one has, and that nothing else is there. That is what a JSON Schema describes.

This post is the first in the series for [Module 6 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-06), which covers checking the shape of responses instead of single values. It validates payments against a draft 2020-12 schema, and shows what the schema catches that value checks miss. The examples are in [`JsonSchemaTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_01/JsonSchemaTest.java).

## What is JSON Schema?

JSON Schema is a JSON document that describes other JSON documents: the type of each value, which properties are required, patterns, ranges and enumerations. REST Assured checks a response against one with the `json-schema-validator` module:

1. **`matchesJsonSchemaInClasspath(path)`** loads a schema from the classpath, usually `src/test/resources`.
2. **`matchesJsonSchema(schema)`** takes the schema as a string, a `File` or a URL.

Both are static imports from `io.restassured.module.jsv.JsonSchemaValidator`, and both are plain Hamcrest matchers on a `String`.

The `$schema` keyword at the top of a schema says which version of JSON Schema it uses. Since REST Assured 6.1, a schema that declares draft 2019-09 or 2020-12 is validated with the networknt validator, so modern keywords such as `$defs`, `const` and `if`/`then`/`else` work.

## The API Under Test

Module 6 tests a payment initiation API in the style of PSD2, under `/pisp/v1`. `POST /domestic-payments` creates a payment and answers `201`:

```json
{
  "paymentId": "pmt-1",
  "instructionId": "INV-1",
  "status": "RCVD",
  "debtor": {"iban": "NL91ABNA0417164300", "name": "Anna de Vries"},
  "creditor": {"iban": "NL69INGB0123456789", "name": "Jan Jansen"},
  "amount": {"value": "25.00", "currency": "EUR"},
  "remittanceInformation": "Invoice INV-1",
  "requestedExecutionDate": "2026-11-11",
  "createdAt": "2026-11-11T06:29:21Z",
  "_links": {"self": {"href": "/pisp/v1/domestic-payments/pmt-1"}}
}
```

`GET /domestic-payments/{id}` returns the same shape. With `?format=v0`, it returns an old shape that breaks the contract, to show what a schema catches:

```json
{"paymentId":"pmt-1","status":"PENDING","amount":25.0,"currency":"EUR"}
```

## The Payment Schema

The contract lives in [`src/test/resources/schemas/payment.schema.json`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/resources/schemas/payment.schema.json) (shortened):

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["paymentId", "instructionId", "status", "debtor", "creditor", "amount", "createdAt", "_links"],
  "additionalProperties": false,
  "properties": {
    "paymentId": { "type": "string", "pattern": "^pmt-[0-9]+$" },
    "status": { "enum": ["RCVD", "PDNG", "ACSP", "ACSC", "RJCT"] },
    "debtor": { "$ref": "#/$defs/party" },
    "creditor": { "$ref": "#/$defs/party", "required": ["iban", "name"] },
    "amount": { "$ref": "#/$defs/amount" },
    "createdAt": { "type": "string", "format": "date-time" },
    ...
  },
  "$defs": {
    "party": { ... },
    "amount": {
      "type": "object",
      "required": ["value", "currency"],
      "additionalProperties": false,
      "properties": {
        "value": { "type": "string", "pattern": "^[0-9]{1,13}\\.[0-9]{2}$" },
        "currency": { "type": "string", "pattern": "^[A-Z]{3}$" }
      }
    }
  }
}
```

- **`required`** lists the fields that must be there.
- **`additionalProperties: false`** forbids any field the schema doesn't list.
- **`enum`** and **`pattern`** restrict values: the status must be an ISO 20022 status code, and the amount a string with two decimals.
- **`$defs`** holds reusable parts, referenced with `$ref`. The creditor reuses the `party` definition and adds that it must have a name.

## Validating Responses

### Step 1: A Created Payment Matches the Schema

```java
private static final String PAYMENT_SCHEMA = "schemas/payment.schema.json";

@Test
void aCreatedPaymentMatchesTheSchema() {
    pisp()
        .contentType(ContentType.JSON)
        .body(domesticPayment("INV-1", "25.00"))
    .when()
        .post("/domestic-payments")
    .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
}
```

`body(..)` without a path passes the whole body, as text, to the matcher. One line checks every field of the response. `domesticPayment(..)` is a helper in [`SamplePayments`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/support/SamplePayments.java) that returns a valid request body.

### Step 2: A Read Payment Matches the Same Schema

```java
@Test
void aReadPaymentMatchesTheSameSchema() {
    String id = createPayment("INV-2");

    pisp()
    .when()
        .get("/domestic-payments/{id}", id)
    .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
}
```

One schema for both endpoints proves that `POST` and `GET` return the same shape, a promise that is easy to break when two endpoints are built separately.

### Step 3: The Schema Catches a Broken Contract

```java
@Test
void theSchemaCatchesAResponseThatBreaksTheContract() {
    String id = createPayment("INV-3");

    AssertionError error = assertThrows(AssertionError.class, () ->
        pisp()
            .queryParam("format", "v0")
        .when()
            .get("/domestic-payments/{id}", id)
        .then()
            .statusCode(200)
            .body(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA)));

    assertThat(error.getMessage(), containsString("_links"));       // the report names what is wrong
}
```

The old shape still has a `paymentId` and a `200` status, so a test that reads only those would pass. The schema fails, and its report lists every problem, each with a JSON path:

```text
Response body doesn't match expectation.
Expected: The content to match the given JSON schema.
$.status: does not have a value in the enumeration ["RCVD", "PDNG", "ACSP", "ACSC", "RJCT"]
$.amount: number found, object expected
$: required property 'instructionId' not found
$: required property 'debtor' not found
$: required property 'creditor' not found
$: required property 'createdAt' not found
$: required property '_links' not found
$: property 'currency' is not defined in the schema and the schema does not allow additional properties
```

> **The code above, ready to run**
>
> All five tests in [`JsonSchemaTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_01/JsonSchemaTest.java) pass with `mvn test -pl module-06 -Dtest=JsonSchemaTest`. Remove `"additionalProperties": false` from the schema and see which line of the report disappears.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 6 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/README.md)

## Smaller and Offline Schemas

### An Inline Schema for a Quick Check

```java
@Test
void anInlineSchemaForAQuickCheck() {
    String id = createPayment("INV-4");

    pisp()
    .when()
        .get("/domestic-payments/{id}", id)
    .then()
        .body(matchesJsonSchema("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["amount"],
              "properties": {
                "amount": {
                  "properties": {
                    "value": { "type": "string", "pattern": "^[0-9]+\\\\.[0-9]{2}$" },
                    "currency": { "const": "EUR" }
                  }
                }
              }
            }
            """));
}
```

A schema doesn't have to describe everything. This one checks only the amount, which is useful when a test cares about one part of a large response. The matcher reads the whole body, so the schema starts at the root and walks down to `amount`. Note the four backslashes: two for the Java text block and two for JSON, leaving one in the regular expression.

### Validating JSON Without an HTTP Call

```java
@Test
void validatingJsonWithoutAnHttpCall() {
    String example = """
        {"paymentId": "pmt-1", "instructionId": "X", "status": "UNKNOWN",
         "debtor": {"iban": "NL91ABNA0417164300"}, "creditor": {"iban": "NL69INGB0123456789", "name": "Jan"},
         "amount": {"value": "1.00", "currency": "EUR"}, "createdAt": "2026-03-01T10:00:00Z",
         "_links": {"self": {"href": "/pisp/v1/domestic-payments/pmt-1"}}}
        """;

    assertThat(example, not(matchesJsonSchemaInClasspath(PAYMENT_SCHEMA)));         // UNKNOWN isn't a status
    assertThat(example.replace("UNKNOWN", "RCVD"), matchesJsonSchemaInClasspath(PAYMENT_SCHEMA));
}
```

`assertThat(..)` is Hamcrest's. The schema matcher works on any string, so it can check stored examples, documentation, messages from a queue, or the schema itself, as here: one example it must reject, and one it must accept.

## Common Pitfalls

1. **Combining the matcher with a path.** `body("amount", matchesJsonSchema(..))` passes a map, not JSON text, and fails with an empty `Expected:` line. Use `body(matcher)` without a path, and write the schema from the root.
2. **Leaving out `$schema`.** Without it, REST Assured uses its older validator, which doesn't know newer keywords. It ignores `const` without a word, so `{"const": "EUR"}` accepts `"USD"`. Always declare draft 2020-12.
3. **Expecting `format` to be checked.** In draft 2020-12, `format` is an annotation by default, so `"format": "date-time"` accepts `"yesterday"`. Use a `pattern` when the format matters.
4. **A wrong classpath location.** `matchesJsonSchemaInClasspath("schemas/paymnet.schema.json")` throws `IllegalArgumentException: Schema to use cannot be null`. The path is relative to `src/test/resources`, without a leading slash.
5. **A schema without `additionalProperties: false`.** It accepts any extra field, including ones that leak data. Decide per object whether extra fields are allowed.

## Wrapping Up

A JSON Schema checks the whole shape of a response: required fields, types, patterns, enumerations and, with `additionalProperties: false`, that nothing extra is there. `matchesJsonSchemaInClasspath(..)` validates a body against a schema in `src/test/resources`, and `matchesJsonSchema(..)` against an inline one. Declare draft 2020-12 with `$schema`, so REST Assured 6.1 and later use the networknt validator and modern keywords work. The matcher reads the whole body, so use it without a path, and on any JSON string, not just responses.
