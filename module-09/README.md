# Module 9: Data-Driven Tests and API Workflows

This module covers testing many inputs without copying tests, generating tests at run time, multi-step workflows, the HTTP semantics payment APIs depend on (idempotency keys, ETags, pagination), and waiting for asynchronous work.

## Table of Contents

- [Module 9: Data-Driven Tests and API Workflows](#module-9-data-driven-tests-and-api-workflows)
  - [Table of Contents](#table-of-contents)
  - [The Domain: the Payment Lifecycle](#the-domain-the-payment-lifecycle)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Parameterized API Tests](#section-1-parameterized-api-tests)
    - [Section 2: Dynamic Tests](#section-2-dynamic-tests)
    - [Section 3: Workflows](#section-3-workflows)
    - [Section 4: HTTP Semantics](#section-4-http-semantics)
    - [Section 5: Asynchronous APIs](#section-5-asynchronous-apis)

## The Domain: the Payment Lifecycle

[`PaymentLifecycleApi`](src/main/java/com/example/lifecycle/PaymentLifecycleApi.java): every request needs `Authorization: Bearer test-token`.

| Request | Response |
| --- | --- |
| `POST /api/v1/payments` with `Idempotency-Key` | 201 `INITIATED` with an `ETag`; the same key and body again: the same payment and `Idempotent-Replayed: true`; the same key, another body: 422; invalid fields: 422 with `errors` |
| `GET /api/v1/payments/{id}` | 200 with `ETag`; 304 for a matching `If-None-Match` |
| `PATCH /api/v1/payments/{id}` | Needs `If-Match`: 428 without, 412 when stale |
| `POST /api/v1/payments/{id}/authorisation` | `AUTHORISED`, or 409 |
| `POST /api/v1/payments/{id}/execution` | 202 `EXECUTING` with `Location` and `Retry-After: 1`; about 0.5 s later `EXECUTED`, or `REJECTED` (`INSUFFICIENT_FUNDS`) above 5000.00 |
| `GET /api/v1/payments?limit=..&cursor=..` | `items`, with `Link: <..>; rel="next"` while there is more |

Validation is real: IBANs are checked with the ISO 13616 mod-97 check digits; amounts need at most two decimals and at most 100000.00; currencies are EUR, GBP or USD; an execution date can't be in the past.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-09` | 67 tests pass, most of them parameterized or generated |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Parameterized API Tests | [Parameterized API Tests With REST Assured: Validation Rules as a Table](../docs/module-09-section-01-rest-assured-parameterized-api-tests.md) |
| 2. Dynamic Tests | [Dynamic API Tests With REST Assured: @TestFactory and dynamicContainer](../docs/module-09-section-02-rest-assured-dynamic-tests.md) |
| 3. Workflows | [API Workflows With REST Assured: Ordered Steps Versus Independent Tests](../docs/module-09-section-03-rest-assured-api-workflows.md) |
| 4. HTTP Semantics | [HTTP Semantics in REST Assured: Idempotency Keys, ETags and Link Pagination](../docs/module-09-section-04-rest-assured-http-semantics.md) |
| 5. Asynchronous APIs | [Testing Asynchronous APIs With REST Assured: 202 Accepted, Retry-After and Awaitility](../docs/module-09-section-05-rest-assured-asynchronous-apis.md) |

## Sections

### Section 1: Parameterized API Tests

**Guide:** [module-09-section-01-rest-assured-parameterized-api-tests.md](../docs/module-09-section-01-rest-assured-parameterized-api-tests.md)

| Class | What it tests |
| --- | --- |
| [`ValidationMatrixTest`](src/test/java/com/example/module_09/section_01/ValidationMatrixTest.java) | 14 invalid inputs from a `@CsvSource` text block, boundary amounts from `@ValueSource`, IBANs from four countries with `@FieldSource`, and several problems at once from `@MethodSource` |
| [`PerCurrencyTest`](src/test/java/com/example/module_09/section_01/PerCurrencyTest.java) | The whole class once per currency with `@ParameterizedClass` and a `@Parameter` field |

**REST Assured concepts**

- Each row is its own test in the report, named by the `name` pattern, so a failure says which input broke.
- The specification logs only failing exchanges (`enableLoggingOfRequestAndResponseIfValidationFails`), which keeps a data-driven run readable.

### Section 2: Dynamic Tests

**Guide:** [module-09-section-02-rest-assured-dynamic-tests.md](../docs/module-09-section-02-rest-assured-dynamic-tests.md)

| Class | What it tests |
| --- | --- |
| [`DynamicTestsTest`](src/test/java/com/example/module_09/section_02/DynamicTestsTest.java) | For each of six endpoints, generated tests for no token, a wrong token and Basic credentials, grouped with `dynamicContainer` |

### Section 3: Workflows

**Guide:** [module-09-section-03-rest-assured-api-workflows.md](../docs/module-09-section-03-rest-assured-api-workflows.md)

| Class | What it tests |
| --- | --- |
| [`PaymentWorkflowTest`](src/test/java/com/example/module_09/section_03/PaymentWorkflowTest.java) | The flow as ordered steps sharing one instance, and the preferred style: independent tests that each walk a payment to the state they test |

### Section 4: HTTP Semantics

**Guide:** [module-09-section-04-rest-assured-http-semantics.md](../docs/module-09-section-04-rest-assured-http-semantics.md)

| Class | What it tests |
| --- | --- |
| [`HttpSemanticsTest`](src/test/java/com/example/module_09/section_04/HttpSemanticsTest.java) | Idempotent replay, key reuse and a missing key; 304 Not Modified; 428 and 412 with the winner's change kept; following `Link` headers to the last page |

### Section 5: Asynchronous APIs

**Guide:** [module-09-section-05-rest-assured-asynchronous-apis.md](../docs/module-09-section-05-rest-assured-asynchronous-apis.md)

| Class | What it tests |
| --- | --- |
| [`AsyncPollingTest`](src/test/java/com/example/module_09/section_05/AsyncPollingTest.java) | Polling a `Location` with Awaitility, respecting `Retry-After`, and waiting for a final state before checking which one |

**REST Assured concepts**

- 202 Accepted means started, not finished. Awaitility polls until a condition holds and fails clearly after a timeout; a fixed `Thread.sleep` is either flaky or slow.
