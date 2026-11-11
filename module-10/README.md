# Module 10: Stubbing Dependencies with WireMock

This module covers testing an API whose own dependency you don't control: REST Assured calls our API, and WireMock plays the third-party API it calls.

```
test --REST Assured--> QuoteService --HttpClient--> WireMock (the FX provider)
```

## Table of Contents

- [Module 10: Stubbing Dependencies with WireMock](#module-10-stubbing-dependencies-with-wiremock)
  - [Table of Contents](#table-of-contents)
  - [The Domain: FX Quotes](#the-domain-fx-quotes)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Stubbing Basics](#section-1-stubbing-basics)
    - [Section 2: Faults](#section-2-faults)
    - [Section 3: Verifying Outgoing Calls](#section-3-verifying-outgoing-calls)
    - [Section 4: Templates and Scenarios](#section-4-templates-and-scenarios)

## The Domain: FX Quotes

[`QuoteService`](src/main/java/com/example/fx/QuoteService.java) answers `POST /api/v1/quotes` with `{"sourceCurrency","targetCurrency","sourceAmount"}` by asking an FX provider for the rate: `GET /v2/rates?base=EUR&symbols=GBP` with `Authorization: Bearer fx-api-key` and `X-Client-Id: payments-service`. The target amount is rounded half-even to two decimals.

| The provider... | Our API answers |
| --- | --- |
| answers 503 | retries once; 502 if it fails again |
| answers another 5xx | 502 |
| answers 429 with `Retry-After` | 503 with the same `Retry-After` |
| answers 404 | 422: the currency isn't supported |
| takes longer than a second | 504 |
| resets the connection, sends broken JSON or no rate | 502 |

Invalid input is rejected with 422 before the provider is called.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-10` | 19 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Stubbing Basics | [Stubbing a Dependency With WireMock: Testing Your API With REST Assured](../docs/module-10-section-01-rest-assured-wiremock-stubbing-basics.md) |
| 2. Faults | [Simulating Provider Faults With WireMock: Errors, Delays, Resets and Broken JSON](../docs/module-10-section-02-rest-assured-wiremock-faults.md) |
| 3. Verifying Outgoing Calls | [Verifying Outgoing Calls With WireMock: What Your API Sent to Its Dependency](../docs/module-10-section-03-rest-assured-wiremock-verifying-calls.md) |
| 4. Templates and Scenarios | [WireMock Response Templates and Scenarios: Stubs That Adapt](../docs/module-10-section-04-rest-assured-wiremock-templates-and-scenarios.md) |

## Sections

### Section 1: Stubbing Basics

**Guide:** [module-10-section-01-rest-assured-wiremock-stubbing-basics.md](../docs/module-10-section-01-rest-assured-wiremock-stubbing-basics.md)

| Class | What it tests |
| --- | --- |
| [`StubbingBasicsTest`](src/test/java/com/example/module_10/section_01/StubbingBasicsTest.java) | A quote with a stubbed rate, half-even rounding of exact ties, a request that matches no stub, calling the stub directly with REST Assured, and invalid input that never reaches the provider |
| [`FxProviderStubs`](src/test/java/com/example/module_10/support/FxProviderStubs.java) | The provider's request pattern and response body in one place |

**REST Assured and WireMock concepts**

- `@RegisterExtension static WireMockExtension` starts WireMock on a free port and resets stubs after each test.
- WireMock's `get(..)` and `equalTo(..)` clash with REST Assured's and Hamcrest's static imports, so the course calls them as `WireMock.get(..)`.

### Section 2: Faults

**Guide:** [module-10-section-02-rest-assured-wiremock-faults.md](../docs/module-10-section-02-rest-assured-wiremock-faults.md)

| Class | What it tests |
| --- | --- |
| [`ProviderFaultsTest`](src/test/java/com/example/module_10/section_02/ProviderFaultsTest.java) | A server error, a slow provider, a reset connection, broken JSON, a missing rate, rate limiting and an unknown currency |

**REST Assured and WireMock concepts**

- `withFixedDelay(..)`, `withFault(Fault.CONNECTION_RESET_BY_PEER)` and malformed bodies reach cases a real provider won't produce on demand.

### Section 3: Verifying Outgoing Calls

**Guide:** [module-10-section-03-rest-assured-wiremock-verifying-calls.md](../docs/module-10-section-03-rest-assured-wiremock-verifying-calls.md)

| Class | What it tests |
| --- | --- |
| [`VerifyingCallsTest`](src/test/java/com/example/module_10/section_03/VerifyingCallsTest.java) | Exactly one call with the right query and headers, one retry for 503, no retry for 500, and reading the recorded requests |

### Section 4: Templates and Scenarios

**Guide:** [module-10-section-04-rest-assured-wiremock-templates-and-scenarios.md](../docs/module-10-section-04-rest-assured-wiremock-templates-and-scenarios.md)

| Class | What it tests |
| --- | --- |
| [`TemplatingAndScenariosTest`](src/test/java/com/example/module_10/section_04/TemplatingAndScenariosTest.java) | One templated stub for every currency, a provider that fails twice and recovers, and a single failure hidden by the retry |

**REST Assured and WireMock concepts**

- `withTransformers("response-template")` builds the answer from the request: `{{request.query.symbols}}`.
- A scenario is a small state machine: `inScenario`, `whenScenarioStateIs`, `willSetStateTo`.
