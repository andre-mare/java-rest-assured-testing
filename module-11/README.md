# Module 11: Spring Boot Integration

This module covers the two ways to test a Spring Boot API with REST Assured: `RestAssuredMockMvc` without a server, and plain REST Assured against the application on a random port. It also covers keeping versions aligned when Spring Boot's dependency management is involved.

## Table of Contents

- [Module 11: Spring Boot Integration](#module-11-spring-boot-integration)
  - [Table of Contents](#table-of-contents)
  - [The Domain: a Spring Boot Accounts Service](#the-domain-a-spring-boot-accounts-service)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Standalone MockMvc](#section-1-standalone-mockmvc)
    - [Section 2: A Web Slice with Spring Security](#section-2-a-web-slice-with-spring-security)
    - [Section 3: A Real Server on a Random Port](#section-3-a-real-server-on-a-random-port)
    - [Section 4: Keeping Versions Aligned](#section-4-keeping-versions-aligned)

## The Domain: a Spring Boot Accounts Service

A Spring Boot 4.1 application ([`AccountsApplication`](src/main/java/com/example/AccountsApplication.java)) with one controller and an OAuth 2.0 resource server configuration:

| Request | Needs |
| --- | --- |
| `GET /api/v1/accounts`, `GET /api/v1/accounts/{id}` | scope `accounts:read` |
| `POST /api/v1/accounts` | scope `accounts:write` |

[`SecurityConfig`](src/main/java/com/example/accounts/SecurityConfig.java) verifies RS256 JWTs with the public key in `src/main/resources/jwt` and requires the audience `accounts-api`. The matching private key, for tests only, is in `src/test/resources/jwt`. Errors are RFC 9457 problem details (`spring.mvc.problemdetails.enabled=true`).

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module | `mvn test -pl module-11` | 16 tests pass |

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Standalone MockMvc | [RestAssuredMockMvc in Standalone Mode: Testing a Spring Controller Without a Server](../docs/module-11-section-01-rest-assured-mockmvc-standalone.md) |
| 2. A Web Slice with Spring Security | [A Web Slice With Spring Security: RestAssuredMockMvc, @WebMvcTest and jwt()](../docs/module-11-section-02-rest-assured-webmvctest-spring-security.md) |
| 3. A Real Server on a Random Port | [Testing Spring Boot on a Random Port With REST Assured: @SpringBootTest and Real Tokens](../docs/module-11-section-03-rest-assured-spring-boot-random-port.md) |
| 4. Keeping Versions Aligned | [Keeping Versions Aligned: REST Assured, JUnit and Jackson Under Spring Boot's BOM](../docs/module-11-section-04-rest-assured-spring-boot-version-alignment.md) |

## Sections

### Section 1: Standalone MockMvc

**Guide:** [module-11-section-01-rest-assured-mockmvc-standalone.md](../docs/module-11-section-01-rest-assured-mockmvc-standalone.md)

| Class | What it tests |
| --- | --- |
| [`StandaloneMockMvcTest`](src/test/java/com/example/module_11/section_01/StandaloneMockMvcTest.java) | Listing, reading, a missing account, opening one and an invalid one, with `standaloneSetup(new AccountController(..))` |

**REST Assured concepts**

- `RestAssuredMockMvc` (the `spring-mock-mvc` module) keeps REST Assured's syntax but calls the `DispatcherServlet` directly: no socket, no server, and in standalone mode no Spring context. The fastest web tests, without security.

### Section 2: A Web Slice with Spring Security

**Guide:** [module-11-section-02-rest-assured-webmvctest-spring-security.md](../docs/module-11-section-02-rest-assured-webmvctest-spring-security.md)

| Class | What it tests |
| --- | --- |
| [`WebMvcSliceTest`](src/test/java/com/example/module_11/section_02/WebMvcSliceTest.java) | A reader and a writer, no token (401), the wrong scope (403 `insufficient_scope`), and problem details |

**REST Assured concepts**

- `@WebMvcTest` (in Spring Boot 4 the package is `org.springframework.boot.webmvc.test.autoconfigure`) starts the web layer; the security configuration is imported.
- Spring Security's `jwt().authorities(..)` post-processor puts an authenticated principal on the request: it tests the rules, not the token validation. `postProcessors(..)` and `auth().with(..)` both apply it.

### Section 3: A Real Server on a Random Port

**Guide:** [module-11-section-03-rest-assured-spring-boot-random-port.md](../docs/module-11-section-03-rest-assured-spring-boot-random-port.md)

| Class | What it tests |
| --- | --- |
| [`RandomPortServerTest`](src/test/java/com/example/module_11/section_03/RandomPortServerTest.java) | Reading and writing with signed tokens, an expired token, a token for another audience, the wrong scope, and a problem-details 404 |
| [`TestTokens`](src/test/java/com/example/module_11/support/TestTokens.java) | Mints RS256 tokens with the test key, JDK only |

**REST Assured concepts**

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `@LocalServerPort` (package `org.springframework.boot.test.web.server`): requests go over the network through every filter, converter and error handler.

### Section 4: Keeping Versions Aligned

**Guide:** [module-11-section-04-rest-assured-spring-boot-version-alignment.md](../docs/module-11-section-04-rest-assured-spring-boot-version-alignment.md)

There is no test class for this section; it is in this module's [`pom.xml`](pom.xml).

- Spring Boot 4.1.2 manages JUnit 6.0.3, Jackson 3.1.7 and Hamcrest 3.0, and no longer manages REST Assured.
- When several imported BOMs manage the same artifact, the first one declared wins. The module imports JUnit's, REST Assured's and Jackson's BOMs before Spring Boot's, so the course versions apply.
- Mixing versions of one library family is the classic failure: a newer `jackson-databind` on an older `jackson-core`, or an older `rest-assured-common` under REST Assured 6.3, which REST Assured 6.3 detects and reports at start-up.
