# Module 2: Building Requests

This module covers every way to put data into a request: path, query and form parameters, headers and cookies, bodies of every kind, every HTTP method, and multipart uploads.

## Table of Contents

- [Module 2: Building Requests](#module-2-building-requests)
  - [Table of Contents](#table-of-contents)
  - [The Domain: a Payments API](#the-domain-a-payments-api)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Path Parameters](#section-1-path-parameters)
    - [Section 2: Query, Form and Plain Parameters](#section-2-query-form-and-plain-parameters)
    - [Section 3: Headers, Cookies and Content Types](#section-3-headers-cookies-and-content-types)
    - [Section 4: Request Bodies](#section-4-request-bodies)
    - [Section 5: Every HTTP Method](#section-5-every-http-method)
    - [Section 6: Multipart Uploads](#section-6-multipart-uploads)

## The Domain: a Payments API

The API under test is [`PaymentsApi`](src/main/java/com/example/payments/PaymentsApi.java). Like every API in Modules 2 to 10, it runs on the JDK's built-in HTTP server through a small routing layer in [`com.example.http`](src/main/java/com/example/http), which each module has its own copy of.

| Request | Response |
| --- | --- |
| `POST /api/v1/payments`, JSON, header `X-Request-ID` | 201 with `Location`; 400 without the header; 415 for a body that isn't JSON; 422 with field `errors` |
| `GET /api/v1/payments?status=..&currency=..` | 200 `{"count":..,"payments":[..]}`; `status` may be repeated |
| `QUERY /api/v1/payments`, JSON filter `{"currency":..,"minAmount":..}` | 200, same shape as GET |
| `GET`, `PUT`, `PATCH` (`application/merge-patch+json`), `DELETE /api/v1/payments/{id}` | Read, replace, change the reference, cancel |
| `POST /api/v1/payments/bulk`, multipart: text `batchName` + CSV `file` | 201 with the created ids |
| `POST /api/v1/beneficiaries`, form url-encoded `name`, `iban`, `nickname` | 201 |
| Any method on `/api/v1/echo`, `/echo/{a}`, `/echo/{a}/{b}` | 200, the request as the server received it |

The **echo** endpoints are this module's main tool: they answer with the method, decoded and raw path, raw query, query parameters, headers (lower-cased names), cookies, content type and body, so each test can check exactly what REST Assured sent. `HEAD` works for every GET route and `OPTIONS` for every path.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-02` | 54 tests pass |
| Run one class | `mvn test -pl module-02 -Dtest=HeadersAndCookiesTest` | 12 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Path Parameters | [Path Parameters in REST Assured: Named, Unnamed and URL-Encoded Values](../docs/module-02-section-01-rest-assured-path-parameters.md) |
| 2. Query, Form and Plain Parameters | [Query, Form and Plain Parameters in REST Assured: queryParam, formParam and param](../docs/module-02-section-02-rest-assured-query-and-form-parameters.md) |
| 3. Headers, Cookies and Content Types | [Headers, Cookies and Content Types in REST Assured: What Is Merged, What Is Overwritten](../docs/module-02-section-03-rest-assured-headers-cookies-content-types.md) |
| 4. Request Bodies | [Request Bodies in REST Assured: Strings, Maps, Records, Lists, Bytes and Files](../docs/module-02-section-04-rest-assured-request-bodies.md) |
| 5. Every HTTP Method | [Every HTTP Method in REST Assured: GET, PUT, PATCH, DELETE, HEAD, OPTIONS and QUERY](../docs/module-02-section-05-rest-assured-http-methods.md) |
| 6. Multipart Uploads | [Multipart Uploads in REST Assured: Files, Bytes and MultiPartSpecBuilder](../docs/module-02-section-06-rest-assured-multipart-uploads.md) |

## Sections

### Section 1: Path Parameters

**Guide:** [module-02-section-01-rest-assured-path-parameters.md](../docs/module-02-section-01-rest-assured-path-parameters.md)

| Class | What it tests |
| --- | --- |
| [`PathParametersTest`](src/test/java/com/example/module_02/section_01/PathParametersTest.java) | Named parameters, a map of them, unnamed parameters by position, mixing both, URL encoding, and a real resource found through its `Location` |

**REST Assured concepts**

- **Named** (`pathParam("id", ..)`, `pathParams(map)`) or **unnamed** (`get("/x/{id}", value)`): unnamed values fill the placeholders that named ones left open, in order.
- **Encoding**: `Anna de Vries` is sent as `Anna%20de%20Vries`; the echo shows both forms.

### Section 2: Query, Form and Plain Parameters

**Guide:** [module-02-section-02-rest-assured-query-and-form-parameters.md](../docs/module-02-section-02-rest-assured-query-and-form-parameters.md)

| Class | What it tests |
| --- | --- |
| [`ParametersTest`](src/test/java/com/example/module_02/section_02/ParametersTest.java) | One, several and multi-value query parameters, a parameter without a value, `java.time` values, URL encoding on and off, form parameters, and where `param` puts its value |

**REST Assured concepts**

- `queryParam` always goes in the URL, `formParam` always in an `application/x-www-form-urlencoded` body, and `param` picks: the query for GET, the body for POST.
- `queryParam("status", "A", "B")` repeats the name: `status=A&status=B`. `queryParam("verbose")` sends a name without a value.
- Since REST Assured 6.1, `java.time` values are sent as ISO-8601 (`2026-01-31`).
- REST Assured encodes a space as `%20`, also in form bodies, because some servers decode `+` wrongly. An already encoded value is encoded again (`%20` becomes `%2520`) unless you call `urlEncodingEnabled(false)`.

### Section 3: Headers, Cookies and Content Types

**Guide:** [module-02-section-03-rest-assured-headers-cookies-content-types.md](../docs/module-02-section-03-rest-assured-headers-cookies-content-types.md)

| Class | What it tests |
| --- | --- |
| [`HeadersAndCookiesTest`](src/test/java/com/example/module_02/section_03/HeadersAndCookiesTest.java) | Headers one by one, from a map and as objects; multi-value headers; merging versus overwriting; `accept(..)`; the charset REST Assured adds to a content type; cookies one by one, from a map and with `Cookie.Builder` |

**REST Assured concepts**

- **Merging**: setting a header twice sends both values, except for `Accept` and `Content-Type`, which are overwritten. `HeaderConfig.overwriteHeadersWithName(..)` changes that per header.
- **`accept(ContentType.JSON)`** sends all four JSON media types: `application/json, application/javascript, text/javascript, text/json`.
- **Charset**: `application/json` is sent as is, but text types get `; charset=ISO-8859-1` appended, the old HTTP default. `EncoderConfig.appendDefaultContentCharsetToContentTypeIfUndefined(false)` stops that.
- **Cookies** all travel in one `Cookie` header.
- In a GPath, a key with dashes is quoted: `headers.'x-request-id'`.

### Section 4: Request Bodies

**Guide:** [module-02-section-04-rest-assured-request-bodies.md](../docs/module-02-section-04-rest-assured-request-bodies.md)

| Class | What it tests |
| --- | --- |
| [`RequestBodiesTest`](src/test/java/com/example/module_02/section_04/RequestBodiesTest.java) | A JSON string, a `Map`, a record, a `List`, an invalid body (422 with field errors), the wrong content type (415), a `byte[]` and a `File` |

**REST Assured concepts**

- A `String`, `byte[]`, `File` or `InputStream` is sent as it is. Any other object is serialized by an object mapper chosen from the content type; with Jackson 3 on the classpath, JSON is written by Jackson 3.
- A record's components become JSON fields, so a record is the easiest typed request body.

### Section 5: Every HTTP Method

**Guide:** [module-02-section-05-rest-assured-http-methods.md](../docs/module-02-section-05-rest-assured-http-methods.md)

| Class | What it tests |
| --- | --- |
| [`HttpMethodsTest`](src/test/java/com/example/module_02/section_05/HttpMethodsTest.java) | GET, PUT, PATCH (merge patch), DELETE (and 409 after it), HEAD, OPTIONS, QUERY, `request(Method, ..)` and a custom `PURGE`, and 405 for a method the resource doesn't support |

**REST Assured concepts**

- `head(..)` gets the headers without a body; `options(..)` reads the `Allow` header.
- **QUERY** (REST Assured 6.1 and later) is a safe method with a body: search criteria travel in the body, not the URL.
- `request(Method.GET, ..)` or `request("PURGE", ..)` sends any method by name.
- `@BeforeEach` creates a fresh payment for every test, so tests can change or cancel it without affecting each other.

### Section 6: Multipart Uploads

**Guide:** [module-02-section-06-rest-assured-multipart-uploads.md](../docs/module-02-section-06-rest-assured-multipart-uploads.md)

| Class | What it tests |
| --- | --- |
| [`MultipartUploadTest`](src/test/java/com/example/module_02/section_06/MultipartUploadTest.java) | A file part from a `File`, from bytes with a file name, built with `MultiPartSpecBuilder`, the multipart content type, and a missing part |

**REST Assured concepts**

- `multiPart("name", value)` adds a text part, `multiPart("file", file, "text/csv")` a file part. REST Assured sets `multipart/form-data` and the boundary.
- `MultiPartSpecBuilder` controls every detail of a part: control name, file name, MIME type and charset.
