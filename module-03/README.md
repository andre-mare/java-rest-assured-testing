# Module 3: Validating Responses in Depth

This module covers checking any part of a response precisely: GPath for JSON, root paths and path arguments, numbers and money, the Hamcrest matchers you will use most, response-aware matchers, cookies with their security attributes, and XML and HTML.

## Table of Contents

- [Module 3: Validating Responses in Depth](#module-3-validating-responses-in-depth)
  - [Table of Contents](#table-of-contents)
  - [The Domain: Transactions and Bank Statements](#the-domain-transactions-and-bank-statements)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: GPath for JSON](#section-1-gpath-for-json)
    - [Section 2: Root Paths and Path Arguments](#section-2-root-paths-and-path-arguments)
    - [Section 3: Numbers and Money](#section-3-numbers-and-money)
    - [Section 4: The Hamcrest Toolbox](#section-4-the-hamcrest-toolbox)
    - [Section 5: Using the Response to Check Itself](#section-5-using-the-response-to-check-itself)
    - [Section 6: Cookies](#section-6-cookies)
    - [Section 7: XML and HTML](#section-7-xml-and-html)

## The Domain: Transactions and Bank Statements

[`TransactionsApi`](src/main/java/com/example/transactions/TransactionsApi.java) serves one account, `acc-1001`, with a month of transactions in three formats.

| Request | Response |
| --- | --- |
| `GET /api/v1/accounts/acc-1001/transactions?type=DEBIT\|CREDIT` | JSON: `accountId`, `iban`, `currency`, `balance.opening`/`closing`, `transactions[]` |
| `POST /api/v1/accounts/acc-1001/transactions` | 201, `Location` ending in the new id |
| `GET /api/v1/accounts/acc-1001/statement` | XML, a simplified ISO 20022 camt.053 statement |
| `GET /api/v1/accounts/acc-1001/statement.html` | An HTML page with a table |
| `GET /api/v1/categories` | A JSON array at the root |
| `POST /api/v1/sessions` | 201 with two `Set-Cookie` headers |

The transactions (amounts are JSON numbers, debits negative; opening balance 1000.00, closing 2407.65):

| id | Date | Type | Amount | Counterparty | Category |
| --- | --- | --- | --- | --- | --- |
| tx-1 | 2026-03-01 | CREDIT | 2500.00 | Acme B.V. | SALARY |
| tx-2 | 2026-03-02 | DEBIT | -42.35 | Albert Heijn | GROCERIES |
| tx-3 | 2026-03-05 | DEBIT | -1100.00 | Woonstichting De Key | RENT |
| tx-4 | 2026-03-09 | DEBIT | -60.00 | Albert Heijn | GROCERIES |
| tx-5 | 2026-03-15 | DEBIT | -40.00 | NS Reizigers | TRANSPORT |
| tx-6 | 2026-03-20 | CREDIT | 150.00 | Belastingdienst | REFUND |

Each transaction also has `tags` (a list) and `remittance-info` (a key with a dash, on purpose).

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-03` | 38 tests pass |
| Run one class | `mvn test -pl module-03 -Dtest=NumbersAndMoneyTest` | 7 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. GPath for JSON | [GPath for JSON in REST Assured: find, findAll, collect, Ranges and Root Arrays](../docs/module-03-section-01-rest-assured-gpath-for-json.md) |
| 2. Root Paths and Path Arguments | [Root Paths and Path Arguments in REST Assured: rootPath, appendRootPath and withArgs](../docs/module-03-section-02-rest-assured-root-paths-and-path-arguments.md) |
| 3. Numbers and Money | [Numbers and Money in REST Assured: Float, BigDecimal and comparesEqualTo](../docs/module-03-section-03-rest-assured-numbers-and-money.md) |
| 4. The Hamcrest Toolbox | [The Hamcrest Toolbox for REST Assured: Collection, Map, String and Combined Matchers](../docs/module-03-section-04-rest-assured-hamcrest-matchers.md) |
| 5. Using the Response to Check Itself | [Response-Aware Matchers in REST Assured: Using the Response to Check Itself](../docs/module-03-section-05-rest-assured-response-aware-matchers.md) |
| 6. Cookies | [Response Cookies in REST Assured: Values, Security Attributes and detailedCookie()](../docs/module-03-section-06-rest-assured-response-cookies.md) |
| 7. XML and HTML | [XML and HTML in REST Assured: Elements, Attributes, Depth-First Search and Namespaces](../docs/module-03-section-07-rest-assured-xml-and-html.md) |

## Sections

### Section 1: GPath for JSON

**Guide:** [module-03-section-01-rest-assured-gpath-for-json.md](../docs/module-03-section-01-rest-assured-gpath-for-json.md)

| Class | What it tests |
| --- | --- |
| [`JsonGPathTest`](src/test/java/com/example/module_03/section_01/JsonGPathTest.java) | `find`, `findAll`, nested conditions, `collect`, `any`/`every`, `max`/`min`, indexes and ranges, flattening lists of lists, keys with dashes, a server-side filter, and a JSON array at the root |

**REST Assured concepts**

- GPath comes from Groovy. A closure in braces filters or transforms; `it` is the current element: `transactions.findAll { it.type == 'DEBIT' }.id`.
- `[0..1]` is a range (both ends included), `[-1]` the last element.
- `$` is the root; on a root array, a path such as `code` walks into every element.
- REST Assured 6 still evaluates GPath with Groovy, so methods such as `unique()`, `flatten()` and `sum()` work.

### Section 2: Root Paths and Path Arguments

**Guide:** [module-03-section-02-rest-assured-root-paths-and-path-arguments.md](../docs/module-03-section-02-rest-assured-root-paths-and-path-arguments.md)

| Class | What it tests |
| --- | --- |
| [`RootPathTest`](src/test/java/com/example/module_03/section_02/RootPathTest.java) | `rootPath`, `appendRootPath`, `detachRootPath`, `noRootPath`, and `withArgs(..)` filling `%s` in a path and in a root path |

### Section 3: Numbers and Money

**Guide:** [module-03-section-03-rest-assured-numbers-and-money.md](../docs/module-03-section-03-rest-assured-numbers-and-money.md)

| Class | What it tests |
| --- | --- |
| [`NumbersAndMoneyTest`](src/test/java/com/example/module_03/section_03/NumbersAndMoneyTest.java) | Integers, the default `Float`, `BigDecimal` with `JsonConfig`, `equalTo` versus `comparesEqualTo`, exact sums, and `closeTo` with doubles |

**REST Assured concepts**

- By default a decimal is a `Float`, which can't hold most amounts exactly, and a `Double` never equals a `Float`.
- `jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL)` reads decimals as `BigDecimal`. Since REST Assured 6.3, `NumberReturnType` lives in `io.restassured.common.config`; the one in `JsonPathConfig` is deprecated.
- `equalTo` on `BigDecimal` is strict about scale (`1000.00` is not `1000`); `comparesEqualTo` is not. Use `comparesEqualTo` for money.

### Section 4: The Hamcrest Toolbox

**Guide:** [module-03-section-04-rest-assured-hamcrest-matchers.md](../docs/module-03-section-04-rest-assured-hamcrest-matchers.md)

| Class | What it tests |
| --- | --- |
| [`HamcrestToolboxTest`](src/test/java/com/example/module_03/section_04/HamcrestToolboxTest.java) | Collection, map and string matchers, and combining matchers with `allOf`, `anyOf`, `oneOf` and `not` |

**REST Assured concepts**

- `hasItems` means "at least these", `contains` "exactly these, in this order", `containsInAnyOrder` "exactly these, any order". Choosing the right one makes the test and its failure message precise.

### Section 5: Using the Response to Check Itself

**Guide:** [module-03-section-05-rest-assured-response-aware-matchers.md](../docs/module-03-section-05-rest-assured-response-aware-matchers.md)

| Class | What it tests |
| --- | --- |
| [`ResponseAwareMatcherTest`](src/test/java/com/example/module_03/section_05/ResponseAwareMatcherTest.java) | A `Location` header that must end with the new id, the ready-made `endsWithPath(..)`, a body value checked against another, and a matcher on the whole body |

**REST Assured concepts**

- A `ResponseAwareMatcher` is a function from the response to a matcher, for values the server chooses. This class adds transactions, so it starts its own API.

### Section 6: Cookies

**Guide:** [module-03-section-06-rest-assured-response-cookies.md](../docs/module-03-section-06-rest-assured-response-cookies.md)

| Class | What it tests |
| --- | --- |
| [`CookiesTest`](src/test/java/com/example/module_03/section_06/CookiesTest.java) | Cookie values, all the attributes of a session cookie with `detailedCookie()`, and reading a `Cookie` object afterwards |

**REST Assured concepts**

- For a session cookie the attributes are the security: `Secure`, `HttpOnly`, `SameSite`, `Path` and `Max-Age`. `detailedCookie()` checks them all.

### Section 7: XML and HTML

**Guide:** [module-03-section-07-rest-assured-xml-and-html.md](../docs/module-03-section-07-rest-assured-xml-and-html.md)

| Class | What it tests |
| --- | --- |
| [`XmlAndHtmlTest`](src/test/java/com/example/module_03/section_07/XmlAndHtmlTest.java) | Elements and attributes (`@Ccy`), `find`/`findAll` on elements, depth-first search with `**`, namespaces with and without `declareNamespace`, and HTML |

**REST Assured concepts**

- An XML path starts with the root element, values are text, and `**` searches the whole tree.
- Without a declared namespace, a path matches elements by their local name. `xmlConfig().declareNamespace("ext", ..)` makes `ext:Category` match only that namespace.
- HTML is parsed leniently and a parser may add elements such as `<tbody>`, so depth-first search is safer than a full path.
