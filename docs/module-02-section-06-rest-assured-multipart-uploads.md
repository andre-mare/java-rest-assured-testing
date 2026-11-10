# Multipart Uploads in REST Assured: Files, Bytes and MultiPartSpecBuilder

Multipart Uploads in REST Assured: Upload a CSV file with a text field as multipart/form-data, from a File, from bytes with a file name and with MultiPartSpecBuilder, and test a request with a missing part.

An HTML form with a file input doesn't send a JSON body or a URL-encoded form. It sends `multipart/form-data`: one request body made of several parts, each with its own name, headers and content, separated by a boundary string. Bulk payment uploads, document uploads and profile pictures all work like that. Building such a body by hand is fiddly. With REST Assured, each part is one method call.

This post follows [Section 6 of Module 2](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-02) of the REST Assured course. It uploads a CSV file of salary payments in three ways and checks what the API received. The examples are in [`MultipartUploadTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_06/MultipartUploadTest.java), and the [usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#multi-part-form-data) has the full reference.

## What is a Multipart Request?

A multipart request has one `Content-Type` header, such as `multipart/form-data; boundary=abc123`, and a body made of parts. Each part has:

1. **A control name**, the name of the form field, such as `batchName` or `file`.
2. **An optional file name**, which tells the server the part is a file.
3. **A content type**, such as `text/plain` or `text/csv`.
4. **The content** itself.

REST Assured builds all of this from `multiPart(..)` calls. It sets the content type to `multipart/form-data`, chooses the boundary and writes every part.

## The Endpoint Under Test

`POST /api/v1/payments/bulk` takes two parts:

- **`batchName`**, a text part that names the batch.
- **`file`**, a CSV file with one payment per line.

It creates one payment per line and answers `201` with what it received:

```json
{
  "batchName": "March salaries",
  "fileName": "salaries.csv",
  "fileContentType": "text/csv",
  "count": 3,
  "paymentIds": ["pay-1", "pay-2", "pay-3"]
}
```

The tests upload this CSV, with a header row and three payments:

```java
private static final String CSV = """
        debtorIban,creditorIban,creditorName,amount,currency
        NL91ABNA0417164300,NL69INGB0123456789,Jan Jansen,1200.00,EUR
        NL91ABNA0417164300,GB33BUKB20201555555555,Emily Clarke,950.00,EUR
        NL91ABNA0417164300,NL20INGB0001234567,Anna de Vries,1100.00,EUR
        """;
```

## Uploading a File

### Step 1: Upload a File

```java
@Test
void uploadAFile(@TempDir Path folder) throws IOException {
    Path csv = Files.writeString(folder.resolve("salaries.csv"), CSV);

    paymentsApi()
        .multiPart("batchName", "March salaries")
        .multiPart("file", csv.toFile(), "text/csv")             // the file name comes from the File
    .when()
        .post("/payments/bulk")
    .then()
        .statusCode(201)
        .body("batchName", equalTo("March salaries"))
        .body("fileName", equalTo("salaries.csv"))
        .body("fileContentType", startsWith("text/csv"))
        .body("count", equalTo(3))
        .body("paymentIds", hasSize(3));
}
```

- **`multiPart("batchName", "March salaries")`** adds a text part. A string part has the content type `text/plain`.
- **`multiPart("file", file, "text/csv")`** adds a file part. The file name, `salaries.csv`, comes from the `File`, and the third argument sets the part's content type.
- **No `contentType(..)` call.** REST Assured sets `multipart/form-data` and the boundary itself.

### Step 2: Upload Bytes With a File Name

The content doesn't have to be in a file on disk. Bytes work too, with the file name passed separately:

```java
@Test
void uploadBytesWithAFileName() {
    paymentsApi()
        .multiPart("batchName", "April salaries")
        .multiPart("file", "april.csv", CSV.getBytes(StandardCharsets.UTF_8), "text/csv")
    .when()
        .post("/payments/bulk")
    .then()
        .statusCode(201)
        .body("fileName", equalTo("april.csv"))
        .body("count", equalTo(3));
}
```

This is often the cleanest form in a test: the content is built in memory, and nothing has to be written to disk or cleaned up.

### Step 3: Build a Part With MultiPartSpecBuilder

When you need full control over a part, build it:

```java
@Test
void buildAPartWithMultiPartSpecBuilder() {
    paymentsApi()
        .multiPart(new MultiPartSpecBuilder("May salaries").controlName("batchName").build())
        .multiPart(new MultiPartSpecBuilder(CSV.getBytes(StandardCharsets.UTF_8))
                .controlName("file")
                .fileName("may.csv")
                .mimeType("text/csv")
                .build())
    .when()
        .post("/payments/bulk")
    .then()
        .statusCode(201)
        .body("fileName", equalTo("may.csv"));
}
```

`MultiPartSpecBuilder` sets every detail of a part by name: the control name, the file name, the MIME type and the charset, and extra headers for the part. The builder makes each setting explicit, which helps when an API is strict about one of them.

> **The code above, ready to run**
>
> All five tests in [`MultipartUploadTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/src/test/java/com/example/module_02/section_06/MultipartUploadTest.java) pass with `mvn test -pl module-02 -Dtest=MultipartUploadTest`. Add `.log().all()` before `when()` to see how REST Assured lists the parts.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 2 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-02/README.md)

## What REST Assured Sends

The echo endpoint shows the content type of a multipart request:

```java
@Test
void theRequestIsMultipartFormData() {
    paymentsApi()
        .multiPart("batchName", "June")
    .when()
        .post("/echo")
    .then()
        .body("contentType", startsWith("multipart/form-data"))
        .body("contentType", containsString("boundary="));
}
```

The boundary is a random string that separates the parts in the body. The server reads it from the content type, so it must be there. That is the reason to let REST Assured set the content type, rather than setting the header yourself.

## A Missing Part

The API needs both parts. Without the file, it answers `400`:

```java
@Test
void aMissingPartIsRejected() {
    paymentsApi()
        .multiPart("batchName", "No file")
    .when()
        .post("/payments/bulk")
    .then()
        .statusCode(400);
}
```

The problem detail says `The part 'file' is missing`. A test like this proves that the API checks its input instead of failing later with a less useful error.

## Common Pitfalls

1. **Setting the content type by hand.** `header("Content-Type", "multipart/form-data")` has no boundary, so the server can't split the body. Let REST Assured set it.
2. **Expecting a file name on a text part.** `multiPart("batchName", "March salaries")` has no file name. Pass a `File`, a file name or a builder with `fileName(..)` when the server needs one.
3. **A wrong control name.** The name must match the form field the server expects, here `file` and `batchName`. A typo gives the same `400` as a missing part.
4. **Adding a `body(..)` to a multipart request.** A multipart request is its own body, and REST Assured drops the other one without a warning. Send every field as a part.
5. **Leaving out the MIME type of a file part.** `multiPart("file", file)` sends the part as `application/octet-stream`. A server that checks for `text/csv` rejects it, so pass the MIME type as the third argument.

## Wrapping Up

A multipart request is one body made of named parts. `multiPart(name, value)` adds a text part, `multiPart(name, file, mimeType)` adds a file part, and the bytes form adds a file name to content built in memory. `MultiPartSpecBuilder` sets every detail of a part when you need it. REST Assured sets `multipart/form-data` and the boundary for you, so don't set the content type yourself. Test the upload, and test the request with a part missing.
