# Module 6: Schema and Contract Validation

This module covers checking the shape of responses instead of single values: JSON Schema 2020-12, a shared contract for errors, XML Schema for ISO 20022 messages, and checking every response against an OpenAPI description.

## Table of Contents

- [Module 6: Schema and Contract Validation](#module-6-schema-and-contract-validation)
  - [Table of Contents](#table-of-contents)
  - [The Domain: a Payment Initiation API](#the-domain-a-payment-initiation-api)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: JSON Schema](#section-1-json-schema)
    - [Section 2: The Error Contract](#section-2-the-error-contract)
    - [Section 3: XML Schema for ISO 20022](#section-3-xml-schema-for-iso-20022)
    - [Section 4: An OpenAPI Contract Filter](#section-4-an-openapi-contract-filter)

## The Domain: a Payment Initiation API

[`PaymentInitiationApi`](src/main/java/com/example/pisp/PaymentInitiationApi.java), in the style of a PSD2 payment initiation (PISP) API, under `/pisp/v1`:

| Request | Response |
| --- | --- |
| `POST /domestic-payments`, JSON | 201 payment; 400, 415 or 422 problem |
| `GET /domestic-payments/{id}` | 200 payment; 404 problem |
| `GET /domestic-payments/{id}?format=v0` | 200, an old shape that breaks the contract, to show what schemas catch |
| `POST /payment-files`, ISO 20022 pain.001 XML | 202, a pain.002 status report: `ACCP`, or `RJCT` when `NbOfTxs` is wrong |

The contract lives in `src/test/resources`:

| File | What it describes |
| --- | --- |
| `schemas/payment.schema.json` | A payment (draft 2020-12, `$defs`, `additionalProperties: false`) |
| `schemas/problem.schema.json` | RFC 9457 errors, with `if`/`then`/`else`: a 422 must list `errors`, other statuses must not |
| `xsd/pain.001.001.09-simplified.xsd`, `xsd/pain.002.001.10-simplified.xsd` | Simplified subsets of the ISO 20022 schemas |
| `openapi/payment-initiation.openapi.json` | The API as OpenAPI 3.1 |
| `pain/salaries-march.xml` | A pain.001 file with two transfers |

All of them were validated against the running API with independent validators while the module was written.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-06` | 18 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. JSON Schema | [JSON Schema Validation in REST Assured: matchesJsonSchemaInClasspath and Draft 2020-12](../docs/module-06-section-01-rest-assured-json-schema-validation.md) |
| 2. The Error Contract | [An Error Contract in REST Assured: RFC 9457 Problems With JSON Schema if/then/else](../docs/module-06-section-02-rest-assured-error-contract.md) |
| 3. XML Schema for ISO 20022 | [XML Schema Validation in REST Assured: XSDs for ISO 20022 pain.001 and pain.002](../docs/module-06-section-03-rest-assured-xml-schema-iso-20022.md) |
| 4. An OpenAPI Contract Filter | [An OpenAPI Contract Filter for REST Assured: Every Response Checked Against the Spec](../docs/module-06-section-04-rest-assured-openapi-contract-filter.md) |

## Sections

### Section 1: JSON Schema

**Guide:** [module-06-section-01-rest-assured-json-schema-validation.md](../docs/module-06-section-01-rest-assured-json-schema-validation.md)

| Class | What it tests |
| --- | --- |
| [`JsonSchemaTest`](src/test/java/com/example/module_06/section_01/JsonSchemaTest.java) | A created and a read payment against the schema, a response that breaks the contract, an inline schema, and validating JSON without HTTP |

**REST Assured concepts**

- The `json-schema-validator` module gives `matchesJsonSchemaInClasspath(..)`. Since REST Assured 6.1 a schema that declares draft 2019-09 or 2020-12 in `$schema` is validated with the networknt validator, so modern keywords work.
- The matcher reads the whole body as text, so it can't be combined with a path, but it is a plain Hamcrest matcher on a `String` and works on any JSON.

### Section 2: The Error Contract

**Guide:** [module-06-section-02-rest-assured-error-contract.md](../docs/module-06-section-02-rest-assured-error-contract.md)

| Class | What it tests |
| --- | --- |
| [`ErrorContractTest`](src/test/java/com/example/module_06/section_02/ErrorContractTest.java) | 404, malformed JSON, the wrong content type and field validation errors against one problem schema, and the schema itself rejecting wrong problems |

### Section 3: XML Schema for ISO 20022

**Guide:** [module-06-section-03-rest-assured-xml-schema-iso-20022.md](../docs/module-06-section-03-rest-assured-xml-schema-iso-20022.md)

| Class | What it tests |
| --- | --- |
| [`XmlSchemaTest`](src/test/java/com/example/module_06/section_03/XmlSchemaTest.java) | The test's own pain.001 against its XSD, an accepted and a rejected file whose pain.002 reports match their XSD, and the XSD rejecting a broken document |

**REST Assured concepts**

- `matchesXsdInClasspath(..)` validates a response, and as a Hamcrest matcher also the files a test sends: test data deserves checking too. An invalid document makes it throw the validator's `SAXParseException` rather than return `false`, so test for invalidity with `assertThrows`, not `not(..)`.

### Section 4: An OpenAPI Contract Filter

**Guide:** [module-06-section-04-rest-assured-openapi-contract-filter.md](../docs/module-06-section-04-rest-assured-openapi-contract-filter.md)

| Class | What it tests |
| --- | --- |
| [`OpenApiContractTest`](src/test/java/com/example/module_06/section_04/OpenApiContractTest.java) | Normal flows and documented errors pass; a body that breaks the contract and an undocumented operation fail |
| [`OpenApiContractFilter`](src/test/java/com/example/module_06/support/OpenApiContractFilter.java) | A REST Assured filter that checks the operation, the status and the body of every response against the OpenAPI file |

**REST Assured concepts**

- OpenAPI 3.1 schemas are JSON Schema 2020-12, so a filter can validate bodies with `matchesJsonSchema`. Copying the whole `components` object into the validated schema keeps `$ref`s such as `#/components/schemas/Party` working.
- The filter is a teaching version. For real projects, `com.atlassian.oai:openapi-request-validator-restassured` (formerly `swagger-request-validator-restassured`) is a complete filter of this kind; check that its version supports REST Assured 6.
- Contract tests ask "does the API keep its promises?", functional tests "does it do the right thing?".
