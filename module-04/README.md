# Module 4: Extracting Values and Mapping Objects

This module covers getting data out of responses, as single values or as Java records, configuring the object mapper, and chaining requests in which each step uses what the previous one returned.

## Table of Contents

- [Module 4: Extracting Values and Mapping Objects](#module-4-extracting-values-and-mapping-objects)
  - [Table of Contents](#table-of-contents)
  - [The Domain: Customers and Consents](#the-domain-customers-and-consents)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Extracting After Validation](#section-1-extracting-after-validation)
    - [Section 2: JsonPath and XmlPath on Their Own](#section-2-jsonpath-and-xmlpath-on-their-own)
    - [Section 3: Mapping to Java Records](#section-3-mapping-to-java-records)
    - [Section 4: Choosing and Configuring the Object Mapper](#section-4-choosing-and-configuring-the-object-mapper)
    - [Section 5: Chaining Requests](#section-5-chaining-requests)

## The Domain: Customers and Consents

[`ConsentsApi`](src/main/java/com/example/consents/ConsentsApi.java) is modelled on open banking account access consents (PSD2 AIS): a customer grants a consent with permissions, authorises it, and the consent id then unlocks their accounts.

| Request | Response |
| --- | --- |
| `POST /api/v1/customers`, `GET /api/v1/customers/{id}` | A customer: `id`, `name`, `email`, `segment`, `createdAt` |
| `GET /api/v1/customers?page=0&size=2` | A page: `items`, `page`, `size`, `totalItems`, `totalPages` |
| `POST /api/v1/consents` with `customerId` and `permissions` | 201, status `AWAITING_AUTHORISATION`, with `_links.self` and `_links.authorise` |
| `POST /api/v1/consents/{id}/authorisation` | 200, status `AUTHORISED` |
| `GET /api/v1/accounts`, header `Consent-ID` | A JSON array of the customer's accounts; 403 unless the consent is authorised with `ReadAccountsBasic` |
| `GET /api/v1/accounts/{id}/balances`, header `Consent-ID` | Needs `ReadBalances` |

Five customers exist at start-up. Accounts also carry `productCode` and `balance`, which the tests' `Account` record deliberately leaves out.

The tests describe the JSON with their own records in [`Models`](src/test/java/com/example/module_04/support/Models.java), written from the API's contract rather than copied from its code, so a mismatch shows up as a failing test.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-04` | 21 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Extracting After Validation | [Extracting Values in REST Assured: extract(), path(..) and ExtractableResponse](../docs/module-04-section-01-rest-assured-extracting-values.md) |
| 2. JsonPath and XmlPath on Their Own | [JsonPath and XmlPath in REST Assured: Typed Getters, Maps, Objects and JSON From Anywhere](../docs/module-04-section-02-rest-assured-jsonpath-and-xmlpath.md) |
| 3. Mapping to Java Records | [Mapping Responses to Java Records in REST Assured: as(..), TypeRef and Unknown Fields](../docs/module-04-section-03-rest-assured-mapping-to-java-records.md) |
| 4. Choosing and Configuring the Object Mapper | [Configuring the Object Mapper in REST Assured: Jackson 3, ObjectMapperType and a Strict JsonMapper](../docs/module-04-section-04-rest-assured-object-mapper-configuration.md) |
| 5. Chaining Requests | [Chaining Requests in REST Assured: Location Headers, Hypermedia Links and a Consent Flow](../docs/module-04-section-05-rest-assured-chaining-requests.md) |

## Sections

### Section 1: Extracting After Validation

**Guide:** [module-04-section-01-rest-assured-extracting-values.md](../docs/module-04-section-01-rest-assured-extracting-values.md)

| Class | What it tests |
| --- | --- |
| [`ExtractingValuesTest`](src/test/java/com/example/module_04/section_01/ExtractingValuesTest.java) | `extract().path(..)` for a value and a list, a kept `ExtractableResponse` with headers and status, `extract().response()`, and a value used in the next request |

**REST Assured concepts**

- Validate first, extract second, in one chain: a wrong status fails with a clear message instead of a confusing `NullPointerException` later.

### Section 2: JsonPath and XmlPath on Their Own

**Guide:** [module-04-section-02-rest-assured-jsonpath-and-xmlpath.md](../docs/module-04-section-02-rest-assured-jsonpath-and-xmlpath.md)

| Class | What it tests |
| --- | --- |
| [`JsonPathAndXmlPathTest`](src/test/java/com/example/module_04/section_02/JsonPathAndXmlPathTest.java) | Typed getters, maps and objects, JSON that didn't come from HTTP, and XmlPath on an ISO 20022 status report |

### Section 3: Mapping to Java Records

**Guide:** [module-04-section-03-rest-assured-mapping-to-java-records.md](../docs/module-04-section-03-rest-assured-mapping-to-java-records.md)

| Class | What it tests |
| --- | --- |
| [`ObjectMappingTest`](src/test/java/com/example/module_04/section_03/ObjectMappingTest.java) | A record out, a record in and out, a generic `Page<Customer>` and a `List<Account>` with `TypeRef`, and unknown fields |

**REST Assured concepts**

- `as(Customer.class)` maps with the mapper on the classpath (Jackson 3 here); generic types need `new TypeRef<Page<Customer>>() {}`.
- **Jackson 3 ignores unknown properties by default** (Jackson 2 failed on them), so a new field in the API doesn't break a test.
- AssertJ's `extracting`, `returns` and `allSatisfy` make assertions on mapped objects readable.

### Section 4: Choosing and Configuring the Object Mapper

**Guide:** [module-04-section-04-rest-assured-object-mapper-configuration.md](../docs/module-04-section-04-rest-assured-object-mapper-configuration.md)

| Class | What it tests |
| --- | --- |
| [`ObjectMapperConfigurationTest`](src/test/java/com/example/module_04/section_04/ObjectMapperConfigurationTest.java) | `ObjectMapperType.JACKSON_3`, the lenient default, and a strict `JsonMapper` that fails on unknown properties |

**REST Assured concepts**

- REST Assured 6 supports Jackson 3 and Jackson 2 side by side and prefers Jackson 3.
- Since REST Assured 6.3 the factory interfaces live in `io.restassured.common.mapper.factory`. `jackson3ObjectMapperFactory(..)` accepts both the new and the deprecated interface, so a bare lambda is ambiguous: assign it to a typed variable first.

### Section 5: Chaining Requests

**Guide:** [module-04-section-05-rest-assured-chaining-requests.md](../docs/module-04-section-05-rest-assured-chaining-requests.md)

| Class | What it tests |
| --- | --- |
| [`ChainingRequestsTest`](src/test/java/com/example/module_04/section_05/ChainingRequestsTest.java) | Following a `Location` header, the full consent flow (customer, consent, authorise link, accounts, balances), an unauthorised consent and a missing permission |

**REST Assured concepts**

- Follow links the API gives you instead of building URLs. A `Location` is an absolute path, so resolve it against the server, not the base path.
- Every test creates the data it needs, so tests stay independent of each other and of their order.
