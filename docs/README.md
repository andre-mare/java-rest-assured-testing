# REST Assured Course Guides

This folder holds the step-by-step guides for the [REST Assured course](../README.md). Each guide follows one section of a module. It explains the concepts behind the section's tests, walks through the code and points out common mistakes.

## Table of Contents

- [REST Assured Course Guides](#rest-assured-course-guides)
  - [Table of Contents](#table-of-contents)
  - [How to Use the Guides](#how-to-use-the-guides)
  - [Module 1: Foundations](#module-1-foundations)
  - [Module 2: Building Requests](#module-2-building-requests)

## How to Use the Guides

For each section:

1. Read the guide. It explains the concepts and shows the tests one step at a time.
2. Run the section's test class with the command near the end of the guide, such as `mvn test -pl module-01 -Dtest=FirstRequestTest`.
3. Change the code or the tests, predict what will happen, and read the output.

The guides follow the order of the course, but each one can be read on its own. Guide files are named `module-NN-section-NN-<topic>.md`, so they sort in course order. The [learning outline](../REST%20Assured%20Outline.md) lists every topic in the course and which module teaches it.

## Module 1: Foundations

How a REST Assured test is put together, where its requests go, how to check a response, and how to read what happens when a check fails. The tests run against an Accounts API. See the [Module 1 README](../module-01/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Your First REST Assured Request: Testing a Real HTTP API with JUnit](module-01-section-01-your-first-rest-assured-request.md) | Start a real HTTP API with `@BeforeAll`, send a request with given/when/then, and check the status code and JSON body |
| 2 | [The Anatomy of a REST Assured Test: RequestSpecification, Response and ValidatableResponse](module-01-section-02-anatomy-of-a-rest-assured-test.md) | The three types behind given/when/then, the syntactic sugar, the shorter forms, stored responses and a first path parameter |
| 3 | [Where REST Assured Requests Go: baseUri, port, basePath and Static Defaults](module-01-section-03-where-rest-assured-requests-go.md) | How a URL is built from `baseUri`, `port`, `basePath` and the path, when an absolute URL wins, and why static defaults are global state |
| 4 | [First REST Assured Assertions: Status, Content Type, Headers, Body Paths and Response Time](module-01-section-04-first-rest-assured-assertions.md) | Status codes and lines, content types, case-insensitive headers, GPath body paths, lists, `Float` numbers, problem details and response time |
| 5 | [Logging in REST Assured: See What Was Sent and What Came Back](module-01-section-05-rest-assured-logging.md) | Logging everything, single parts, only when validation fails, only for errors, or only for one status code |
| 6 | [Reading REST Assured Failures: Failure Messages and Eager Validation](module-01-section-06-reading-rest-assured-failures.md) | The shape of a failure message, why a `then()` chain stops at its first failure, and how one `body(..)` call reports every mismatch |

## Module 2: Building Requests

Every way to put data into a request, and how to send every HTTP method. The tests run against a Payments API with echo endpoints that show exactly what was sent. See the [Module 2 README](../module-02/README.md) for the code and how to run it.

| Section | Guide | What you will learn |
| --- | --- | --- |
| 1 | [Path Parameters in REST Assured: Named, Unnamed and URL-Encoded Values](module-02-section-01-rest-assured-path-parameters.md) | Named and unnamed path parameters, mixing the two, URL encoding, and finding a resource through its `Location` header |
| 2 | [Query, Form and Plain Parameters in REST Assured: queryParam, formParam and param](module-02-section-02-rest-assured-query-and-form-parameters.md) | Multi-value and empty query parameters, `java.time` values, form bodies, turning URL encoding off, and where `param(..)` puts its value |
| 3 | [Headers, Cookies and Content Types in REST Assured: What Is Merged, What Is Overwritten](module-02-section-03-rest-assured-headers-cookies-content-types.md) | Headers from maps and objects, merging versus overwriting, `accept(..)`, the charset added to text types, and cookies |
| 4 | [Request Bodies in REST Assured: Strings, Maps, Records, Lists, Bytes and Files](module-02-section-04-rest-assured-request-bodies.md) | Which bodies are sent as they are and which are serialized, records as typed bodies, and testing `422` and `415` rejections |
| 5 | [Every HTTP Method in REST Assured: GET, PUT, PATCH, DELETE, HEAD, OPTIONS and QUERY](module-02-section-05-rest-assured-http-methods.md) | Replacing, patching and cancelling a resource, `HEAD`, `OPTIONS`, the new `QUERY` method, custom methods and `405` |
| 6 | [Multipart Uploads in REST Assured: Files, Bytes and MultiPartSpecBuilder](module-02-section-06-rest-assured-multipart-uploads.md) | Uploading a file with a text field from a `File`, from bytes and with `MultiPartSpecBuilder`, and testing a missing part |
