# Request Bodies in REST Assured: Strings, Maps, Records, Lists, Bytes and Files

Request Bodies in REST Assured: Send a JSON body as a String, a Map, a Java record or a List, send bytes and files as they are, and test the API's answer to an invalid body (422) and the wrong content type (415).

Creating a payment means sending a JSON body. You could write the JSON by hand in a text block, but what about a test that needs the same payment with one field removed? Or a record you already have in your code? REST Assured accepts almost anything as a body. Some of it is sent exactly as it is, and some of it is turned into JSON first. Knowing which is which explains most of the surprises you will meet.

This post follows [Section 4 of Module 2](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02) of the REST Assured course. It creates payments with every kind of body, then sends bodies the API must reject. The examples are in [`RequestBodiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_04/RequestBodiesTest.java), and the [usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#serialization) has the full reference on serialization.

## How Does REST Assured Send a Body?

`body(..)` takes any object. What REST Assured does with it depends on its type:

1. **Sent as it is**: a `String`, a `byte[]`, a `File` or an `InputStream`. REST Assured doesn't look inside it.
2. **Serialized**: any other object, such as a `Map`, a `List` or a record. REST Assured picks an object mapper from the content type: JSON for `application/json`, XML for `application/xml`.

This module has Jackson 3 on the classpath, so JSON bodies are written by Jackson 3. Whatever the body, set the content type yourself. It tells the server what the body is, and it tells REST Assured how to serialize it.

## The Endpoint Under Test

`POST /api/v1/payments` creates a payment. It needs:

- **The header `X-Request-ID`**, or it answers `400`.
- **A JSON content type**, or it answers `415 Unsupported Media Type`.
- **A valid JSON object**, or it answers `422` with one entry per invalid field.

A valid body looks like this:

```json
{
  "debtorIban": "NL91ABNA0417164300",
  "creditorIban": "NL69INGB0123456789",
  "creditorName": "Jan Jansen",
  "amount": "12.50",
  "currency": "EUR"
}
```

The amount is a **string** with exactly two decimals, as in most open banking APIs, so no precision is lost in JSON. `reference` is optional.

The tests get valid bodies from a small helper class, [`SamplePayments`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/support/SamplePayments.java). `validPaymentJson(amount)` returns the JSON above as a string, and `validPayment(amount)` returns it as a mutable `Map`, with a `reference` added, so a test can change or remove one field. The request helper sets the required header:

```java
private static RequestSpecification paymentsApi() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH)
            .header("X-Request-ID", "bodies");
}
```

## JSON Bodies

### Step 1: A JSON String

```java
@Test
void aJsonString() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body(validPaymentJson("12.50"))
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .body("amount", equalTo("12.50"));
}
```

A string is sent exactly as written. It is the clearest form when the JSON itself is what the test is about, for example a body with a field the API doesn't know.

### Step 2: A Map

```java
@Test
void aMapIsSerializedToJson() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body(validPayment("13.00"))
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .body("reference", equalTo("Invoice 2026-001"));
}
```

Jackson writes each entry as a JSON field. A map is easy to change in a test: `put(..)` a different value or `remove(..)` a field.

### Step 3: A Record

```java
record PaymentRequest(String debtorIban, String creditorIban, String creditorName, String amount, String currency) {
}

@Test
void aRecordIsSerializedToJson() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body(new PaymentRequest(DEBTOR_IBAN, CREDITOR_IBAN, "Emily Clarke", "14.00", "EUR"))
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .body("creditorName", equalTo("Emily Clarke"));
}
```

Jackson 3 writes each record component as a JSON field with the same name. A record is the easiest typed request body: the compiler checks the field names, and the constructor shows which fields a payment needs. Module 4 maps responses back into records.

### Step 4: A List

```java
@Test
void aListIsSerializedToAJsonArray() {
    paymentsApi()
        .contentType(ContentType.JSON)
        .body(List.of(Map.of("id", "a"), Map.of("id", "b")))
    .when()
        .post("/echo")
    .then()
        .body("body", startsWith("["))
        .body("body", containsString("\"id\":\"a\""));
}
```

A list becomes a JSON array. The echo endpoint returns the body as text, so the test can check the JSON Jackson wrote.

> **The code above, ready to run**
>
> All eight tests in [`RequestBodiesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_04/RequestBodiesTest.java) pass with `mvn test -pl module-02 -Dtest=RequestBodiesTest`. Add `.log().body()` before `when()` to see the JSON that each kind of body becomes.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## Bodies the API Rejects

### An Invalid Body: 422 With Field Errors

```java
@Test
void anInvalidBodyGivesFieldErrors() {
    Map<String, Object> payment = validPayment("0.5");          // one decimal, so invalid
    payment.remove("creditorName");

    paymentsApi()
        .contentType(ContentType.JSON)
        .body(payment)
    .when()
        .post("/payments")
    .then()
        .statusCode(422)
        .body("errors.field", hasItems("amount", "creditorName"));
}
```

The API validates every field and reports all the errors at once, as an RFC 9457 problem with an `errors` list:

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "The request has invalid fields",
  "instance": "/api/v1/payments",
  "errors": [
    {"field": "creditorName", "detail": "must not be blank"},
    {"field": "amount", "detail": "must be a positive amount with two decimals, such as \"25.00\""}
  ]
}
```

`"errors.field"` collects the `field` of every error into a list, and `hasItems(..)` checks that both are in it, whatever the order. Starting from a valid map and breaking one or two fields keeps the test focused on what it is about.

### The Wrong Content Type: 415

```java
@Test
void theWrongContentTypeIsRejected() {
    paymentsApi()
        .contentType(ContentType.TEXT)
        .body(validPaymentJson("15.00"))
    .when()
        .post("/payments")
    .then()
        .statusCode(415);
}
```

The body is valid JSON, but the request says it is text. The API trusts the header, not the content, and answers `415 Unsupported Media Type`.

## Bodies Sent as They Are

### Bytes

```java
@Test
void bytesAreSentAsTheyAre() {
    byte[] content = "plain bytes".getBytes(StandardCharsets.UTF_8);

    paymentsApi()
        .contentType("application/octet-stream")
        .body(content)
    .when()
        .post("/echo")
    .then()
        .body("bodyLength", equalTo(content.length))
        .body("contentType", startsWith("application/octet-stream"));
}
```

`bodyLength` proves that every byte arrived and nothing was added.

### A File

```java
@Test
void aFileIsSentAsItsContent(@TempDir Path folder) throws IOException {
    Path file = Files.writeString(folder.resolve("payment.json"), validPaymentJson("16.00"));

    paymentsApi()
        .contentType(ContentType.JSON)
        .body(file.toFile())
    .when()
        .post("/payments")
    .then()
        .statusCode(201)
        .body("amount", equalTo("16.00"));
}
```

REST Assured sends the content of the file, not its name. JUnit's `@TempDir` gives the test a folder that is deleted afterwards. In a real suite, a body file usually lives in `src/test/resources`. To send a file as an upload, with its name, use a multipart request ([Section 6](module-02-section-06-rest-assured-multipart-uploads.md)).

## Common Pitfalls

1. **Forgetting the content type.** The server can't tell what the body is, and REST Assured can't tell which mapper to use. Set it on every request with a body.
2. **Sending amounts as numbers.** `BigDecimal("12.50")` or `12.50` is written as a JSON number, which this API rejects, and which may lose the trailing zero. Amounts are strings here.
3. **Expecting `"errors.field"` to be in a fixed order.** Use `hasItems(..)` unless the order is part of the contract.
4. **Passing a `Path` instead of a `File`.** A `Path` is not one of the types that are sent as they are, so Jackson serializes it, and the body becomes the JSON string `"file:///..."`. Call `toFile()`.
5. **Building JSON by concatenating strings.** Quotes and special characters break it quickly. Use a text block for fixed JSON, and a map or a record when values change.

## Wrapping Up

A `String`, `byte[]`, `File` or `InputStream` is sent exactly as it is. Any other object, such as a `Map`, a `List` or a record, is serialized by the object mapper that matches the content type, which is Jackson 3 here. Records are the easiest typed bodies, and maps are the easiest to break on purpose. Always set the content type, and test the rejections too: a `422` that lists every invalid field and a `415` for the wrong content type are part of the API's contract.
