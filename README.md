# Java API Testing with REST Assured

[![Java 26](https://img.shields.io/badge/Java-26-blue)](https://openjdk.org/projects/jdk/26/)
[![REST Assured 6.3.0](https://img.shields.io/badge/REST%20Assured-6.3.0-6DB33F)](https://rest-assured.io/)
[![JUnit 6.1.3](https://img.shields.io/badge/JUnit-6.1.3-25A162)](https://docs.junit.org/6.1.3/)
[![Maven 3.9](https://img.shields.io/badge/Maven-3.9-C71A36)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

Learn to test REST APIs in Java by reading, running and breaking real tests. In Java, the standard library for this is REST Assured: you describe an HTTP request, send it, and check the response in one readable `given().when().then()` chain, inside ordinary JUnit tests. This repository teaches REST Assured 6 by example, and continues the [JUnit 6 course](https://github.com/andre-mare/java-unit-testing). Every concept is a test you can run, read and change.

The course has twelve Maven modules, from a first request in Module 1 to request signing, WireMock, Spring Boot and real OAuth 2.0 tokens from Keycloak in Modules 8 to 12. Each module tests its own small HTTP API from banking and identity, such as accounts, payments, consents and an OAuth 2.0 resource server, which the tests start themselves, and the tests for each section are in their own package. The modules share no code, so you can work through them in order or start with the one you need.

## Table of Contents
- [Java API Testing with REST Assured](#java-api-testing-with-rest-assured)
  - [Table of Contents](#table-of-contents)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Build and Test](#build-and-test)
  - [Course Modules](#course-modules)
    - [How the APIs Under Test Work](#how-the-apis-under-test-work)
  - [Running Tests](#running-tests)
    - [Tags and Module 12 Profiles](#tags-and-module-12-profiles)
  - [Deliberately Failing Tests](#deliberately-failing-tests)
  - [Parallel Execution](#parallel-execution)
  - [Technology Stack](#technology-stack)
  - [Resources](#resources)
  - [License](#license)

## Getting Started

### Prerequisites

- **JDK 26 or later.** The build compiles with `--release 26`, so an older JDK fails even though REST Assured 6 itself only needs Java 17.
- **Maven 3.9 or later.** The project has no Maven Wrapper, so Maven must be installed. It was tested with Maven 3.9.16.
- **Docker**, for the Keycloak tests in Module 12 only. Without Docker those tests are skipped, not failed.

### Build and Test

```bash
git clone https://github.com/andre-mare/java-rest-assured-testing.git
cd java-rest-assured-testing
mvn test
```

The build should end with `BUILD SUCCESS`. The tests start their own APIs on free ports of `localhost`, so nothing else needs to be running. On a machine without Docker, the Keycloak tests in Module 12 are reported as skipped.

## Course Modules

Work through the modules in order. They build on each other conceptually, but their code is independent, so you can also run any module on its own. The [learning outline](REST%20Assured%20Outline.md) lists every topic and which module teaches it.

| Module | Level | Topics | API under test |
| --- | --- | --- | --- |
| [1. Foundations](module-01/) | Beginner | given/when/then, where requests go, first assertions, logging, reading failures | Accounts |
| [2. Building requests](module-02/) | Beginner | Parameters, headers, cookies, bodies, every HTTP method, multipart | Payments |
| [3. Validating responses in depth](module-03/) | Beginner to intermediate | GPath, root paths, money as `BigDecimal`, Hamcrest, cookies, XML and HTML | Transactions, bank statements |
| [4. Extracting values and mapping objects](module-04/) | Intermediate | `extract()`, `JsonPath`, records, `TypeRef`, Jackson 3, chaining | Customers, consents |
| [5. Reusable specifications and configuration](module-05/) | Intermediate | Specifications, `RestAssuredConfig`, timeouts, redirects, log masking, a JUnit extension | Open banking accounts |
| [6. Schema and contract validation](module-06/) | Intermediate | JSON Schema 2020-12, RFC 9457, ISO 20022 XSD, an OpenAPI contract filter | Payment initiation |
| [7. Authentication and transport security](module-07/) | Intermediate to advanced | Basic, bearer, client credentials, negative security tests, sessions, mutual TLS, redirects | OAuth 2.0 authorization and resource server |
| [8. Filters and extensibility](module-08/) | Advanced | Custom filters, HTTP message signatures, log masking, custom authentication, parsers, listeners | Signed payments |
| [9. Data-driven tests and API workflows](module-09/) | Advanced | Parameterized and dynamic tests, workflows, idempotency, ETags, pagination, polling | Payment lifecycle |
| [10. Stubbing dependencies with WireMock](module-10/) | Advanced | Stubs, faults, verifying outgoing calls, templates, scenarios | FX quotes |
| [11. Spring Boot integration](module-11/) | Advanced | `RestAssuredMockMvc`, Spring Security, random-port servers, version alignment | Spring Boot accounts service |
| [12. Real infrastructure with Testcontainers](module-12/) | Advanced | Keycloak tokens, parallel execution, tags and profiles | OAuth-protected API, reference data |

### How the APIs Under Test Work

Modules 1 to 10 and 12 run their APIs on the JDK's built-in HTTP server, so there is no Spring, servlet container or Docker to set up. Each test class starts its API in `@BeforeAll` on a free port and stops it in `@AfterAll`. From Module 2, each module has its own copy of a tiny routing layer in `com.example.http` (`ApiServer`), which gives path templates, JSON with Jackson 3, RFC 9457 problem details, 404/405 handling, `HEAD` and `OPTIONS`. Module 11 is a Spring Boot 4 application.

Read an API's class comment first: it lists every endpoint and rule the tests rely on.

## Running Tests

Run these commands from the repository root.

| Goal | Command |
| --- | --- |
| Run everything | `mvn test` |
| Run one module | `mvn test -pl module-07` |
| Run one class | `mvn test -pl module-03 -Dtest=NumbersAndMoneyTest` |
| Run one test method | `mvn test -pl module-03 -Dtest=NumbersAndMoneyTest#decimalsAsBigDecimal` |

### Tags and Module 12 Profiles

Module 12 defines Maven profiles for its tags:

```bash
mvn test -pl module-12 -Psmoke        # only smoke tests
mvn test -pl module-12 -Psecurity     # only the authentication and authorization checks
mvn test -pl module-12 -Pno-docker    # everything except the Keycloak tests
```

## Deliberately Failing Tests

Some classes fail on purpose so you can read REST Assured's failure messages. They carry the `expected-failure-tests` tag, which the parent POM excludes by default. To run one, clear the filter and name the class:

```bash
mvn test -pl module-01 -DexcludedGroups=none -Dtest=DeliberatelyFailingTest
```

Maven reports `BUILD FAILURE` for these runs. That is the expected result. Other modules show failures inside passing tests with `assertThrows`.

| Module | Classes |
| --- | --- |
| 1 | `DeliberatelyFailingTest` |

## Parallel Execution

Tests run sequentially by default. Module 12 enables parallel execution in its [`junit-platform.properties`](module-12/src/test/resources/junit-platform.properties), but only for classes that opt in with `@Execution(CONCURRENT)`, on a fixed pool of four threads. `ParallelExecutionTest` is the class that opts in. Compare the time Surefire reports for it with and without the annotation, and read its class comment for the rules that make REST Assured tests safe to run in parallel.

```bash
mvn test -pl module-12 -Dtest=ParallelExecutionTest
```

## Technology Stack

| Tool | Version | Used for | Scope |
| --- | --- | --- | --- |
| [REST Assured](https://rest-assured.io/) | 6.3.0, through `rest-assured-bom` | Sending requests and checking responses | All modules |
| [REST Assured JSON Schema Validator](https://github.com/rest-assured/rest-assured/wiki/Usage#json-schema-validation) | 6.3.0, through `rest-assured-bom` | Validating responses against JSON Schema | Module 6 |
| [REST Assured Spring Mock MVC](https://github.com/rest-assured/rest-assured/wiki/Spring) | 6.3.0, through `rest-assured-bom` | Testing Spring MVC controllers without a server | Module 11 |
| [JUnit Jupiter](https://junit.org/) | 6.1.3, through `junit-bom` | Test framework | All modules |
| [Hamcrest](https://hamcrest.org/JavaHamcrest/) | from REST Assured | Matchers | All modules |
| [Jackson](https://github.com/FasterXML/jackson) | 3.2.3, through `jackson-bom` | JSON in the APIs and object mapping in the tests | All modules |
| [AssertJ](https://assertj.github.io/doc/) | 3.27.7 | Fluent assertions on mapped objects | Modules 4, 9 |
| [Awaitility](http://www.awaitility.org/) | 4.3.0 | Waiting for asynchronous work | Module 9 |
| [WireMock](https://wiremock.org/) | 3.13.2 | Stubbing a third-party API | Module 10 |
| [Spring Boot](https://spring.io/projects/spring-boot) | 4.1.2 | The application under test | Module 11 |
| [Testcontainers](https://testcontainers.com/) | 2.0.5, through `testcontainers-bom` | Running containers from tests | Module 12 |
| [testcontainers-keycloak](https://github.com/dasniko/testcontainers-keycloak) | 4.4.0 | Keycloak 26.8 in Docker | Module 12 |
| [Maven Compiler Plugin](https://maven.apache.org/plugins/maven-compiler-plugin/) | 3.15.0 | Compiles with `-parameters` for readable parameterized test names | All modules |
| [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/) | 3.5.5 | Runs the tests | All modules |

## Resources

- [REST Assured Usage Guide](https://github.com/rest-assured/rest-assured/wiki/Usage)
- [REST Assured Release Notes](https://github.com/rest-assured/rest-assured/wiki/ReleaseNotes)
- [REST Assured Javadoc](https://javadoc.io/doc/io.rest-assured/rest-assured/latest/index.html)
- [JUnit 6.1.3 User Guide](https://docs.junit.org/6.1.3/)
- [Java Unit Testing with JUnit 6](https://github.com/andre-mare/java-unit-testing), the course this one builds on

## License

Released under the [MIT License](LICENSE).
