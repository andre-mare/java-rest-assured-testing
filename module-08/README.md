# Module 8: Filters and Extensibility

This module covers changing every request and response in one place with filters, signing requests, masking personal data in logs, a custom authentication scheme, parsers for misbehaving APIs, and listeners that react to failures.

## Table of Contents

- [Module 8: Filters and Extensibility](#module-8-filters-and-extensibility)
  - [Table of Contents](#table-of-contents)
  - [The Domain: Signed Payments](#the-domain-signed-payments)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Writing Filters](#section-1-writing-filters)
    - [Section 2: Useful Filters](#section-2-useful-filters)
    - [Section 3: Signing Requests](#section-3-signing-requests)
    - [Section 4: A Custom Authentication Scheme](#section-4-a-custom-authentication-scheme)
    - [Section 5: Parsers and Failure Listeners](#section-5-parsers-and-failure-listeners)

## The Domain: Signed Payments

[`SignedPaymentsApi`](src/main/java/com/example/signing/SignedPaymentsApi.java) requires requests to be signed, in the spirit of the request signing that FAPI and several open banking standards require. [`HttpSignatures`](src/main/java/com/example/signing/HttpSignatures.java) describes the scheme: HTTP Message Signatures (RFC 9421, `hmac-sha256`) covering `@method`, `@path`, `content-digest` and `x-correlation-id`, plus `Content-Digest` (RFC 9530).

| Request | Rules |
| --- | --- |
| `POST /api/v1/payments` | `X-Correlation-Id`; signed; a correct `Content-Digest`; signatures older than five minutes are rejected |
| `GET /api/v1/payments/{id}` | `X-Correlation-Id`; signed |
| `GET /api/v1/partner/accounts` | `X-API-Key: partner-key-123` |
| `GET /api/v1/legacy/fx-rates` | None; sends JSON as `text/plain` and echoes no correlation id |

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-08` | 26 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Writing Filters | [Writing Filters in REST Assured: Filter, FilterContext and OrderedFilter](../docs/module-08-section-01-rest-assured-writing-filters.md) |
| 2. Useful Filters | [Useful Filters for REST Assured: Timing, Masked Logs and Repairing Responses](../docs/module-08-section-02-rest-assured-useful-filters.md) |
| 3. Signing Requests | [Signing Requests in REST Assured: HTTP Message Signatures With an OrderedFilter](../docs/module-08-section-03-rest-assured-signing-requests.md) |
| 4. A Custom Authentication Scheme | [A Custom Authentication Scheme in REST Assured: ContextAuthenticationScheme for an API Key](../docs/module-08-section-04-rest-assured-custom-authentication-scheme.md) |
| 5. Parsers and Failure Listeners | [Parsers and Failure Listeners in REST Assured: Wrong Content Types and Evidence on Failure](../docs/module-08-section-05-rest-assured-parsers-and-failure-listeners.md) |

## Sections

### Section 1: Writing Filters

**Guide:** [module-08-section-01-rest-assured-writing-filters.md](../docs/module-08-section-01-rest-assured-writing-filters.md)

| Class | What it tests |
| --- | --- |
| [`WritingFiltersTest`](src/test/java/com/example/module_08/section_01/WritingFiltersTest.java) | A filter that adds a header, a lambda filter, a filter that sees responses, a filter that fails a test, ordered filters, filters in a specification, and global filters |
| [`CorrelationIdFilter`](src/test/java/com/example/module_08/support/CorrelationIdFilter.java) | Adds `X-Correlation-Id` and checks that the response echoes it |

**REST Assured concepts**

- Code before `context.next(request, responseSpec)` changes the request; code after it sees the response.
- `OrderedFilter.getOrder()`: lower values run first; ordinary filters count as 1000.

### Section 2: Useful Filters

**Guide:** [module-08-section-02-rest-assured-useful-filters.md](../docs/module-08-section-02-rest-assured-useful-filters.md)

| Class | What it tests |
| --- | --- |
| [`UsefulFiltersTest`](src/test/java/com/example/module_08/section_02/UsefulFiltersTest.java) | Timing, a log with IBANs and names masked, the built-in logging filters, and repairing a response with `ResponseBuilder` |
| [`MaskingLogFilter`](src/test/java/com/example/module_08/support/MaskingLogFilter.java) | Logs each exchange with personal data masked |

### Section 3: Signing Requests

**Guide:** [module-08-section-03-rest-assured-signing-requests.md](../docs/module-08-section-03-rest-assured-signing-requests.md)

| Class | What it tests |
| --- | --- |
| [`RequestSigningTest`](src/test/java/com/example/module_08/section_03/RequestSigningTest.java) | Signed POST and GET accepted; unsigned, tampered-after-signing, wrongly signed and stale requests rejected |
| [`HttpMessageSignatureFilter`](src/test/java/com/example/module_08/support/HttpMessageSignatureFilter.java) | Adds `Content-Digest`, `Signature-Input` and `Signature`; written from the RFCs, independently of the server's code |

**REST Assured concepts**

- The signer is an `OrderedFilter` near `LOWEST_PRECEDENCE`, so it runs last, just before sending. A "tamper in transit" filter placed after it proves the server checks the digest.
- An injectable `Clock` makes the "signature too old" case testable.

### Section 4: A Custom Authentication Scheme

**Guide:** [module-08-section-04-rest-assured-custom-authentication-scheme.md](../docs/module-08-section-04-rest-assured-custom-authentication-scheme.md)

| Class | What it tests |
| --- | --- |
| [`CustomAuthenticationTest`](src/test/java/com/example/module_08/section_04/CustomAuthenticationTest.java) | An API key scheme in a specification, removed with `auth().none()`, replaced by another scheme, and as the global default |

**REST Assured concepts**

- `ContextAuthenticationScheme` (REST Assured 6.3) is a functional interface that sets headers through an `AuthenticationContext`, without Apache HttpClient 4 types.

### Section 5: Parsers and Failure Listeners

**Guide:** [module-08-section-05-rest-assured-parsers-and-failure-listeners.md](../docs/module-08-section-05-rest-assured-parsers-and-failure-listeners.md)

| Class | What it tests |
| --- | --- |
| [`ParsersAndListenersTest`](src/test/java/com/example/module_08/section_05/ParsersAndListenersTest.java) | A body that is just text, `parser("text/plain", Parser.JSON)` per response and in a specification, and a `ResponseValidationFailureListener` that collects evidence |

- A parser registered with `ResponseSpecBuilder.registerParser(..)` covers only the expectations inside that specification. `then().spec(..)` doesn't pass it on, so a `body(path, ..)` chained after `spec(..)` still fails on `text/plain`: put the body expectations in the specification, or use `parser(..)` on the response.
