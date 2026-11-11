# Module 12: Real Infrastructure with Testcontainers

This module covers running REST Assured against real infrastructure in Docker, and making a large suite fast and organised: real OAuth 2.0 tokens from Keycloak, parallel execution, and tags with Maven profiles.

## Table of Contents

- [Module 12: Real Infrastructure with Testcontainers](#module-12-real-infrastructure-with-testcontainers)
  - [Table of Contents](#table-of-contents)
  - [The Domain](#the-domain)
  - [Running the Module](#running-the-module)
  - [Guides](#guides)
  - [Sections](#sections)
    - [Section 1: Real Tokens from Keycloak](#section-1-real-tokens-from-keycloak)
    - [Section 2: Running in Parallel](#section-2-running-in-parallel)
    - [Section 3: Tags and Profiles](#section-3-tags-and-profiles)

## The Domain

- [`ProtectedAccountsApi`](src/main/java/com/example/resource/ProtectedAccountsApi.java): accounts (`accounts:read`) and payments (`payments:write`), protected by tokens from a real authorization server. [`JwksTokenVerifier`](src/main/java/com/example/resource/JwksTokenVerifier.java) verifies RS256 signatures with the keys the server publishes as a JWKS, plus issuer, audience and expiry, and reloads the keys for an unknown key id.
- Keycloak 26.8 in Docker, with the realm [`open-banking-realm.json`](src/test/resources/keycloak/open-banking-realm.json): clients `tpp-accounts`/`accounts-secret` and `tpp-payments`/`payments-secret` with the client credentials grant, client scopes `accounts:read` and `payments:write`, and an audience mapper for `accounts-api`.
- [`ReferenceDataApi`](src/main/java/com/example/reference/ReferenceDataApi.java): currencies and bank holidays, with a 200 ms delay per request so parallel execution shows.

## Running the Module

| Goal | Command | Result |
| --- | --- | --- |
| Run the module (Docker running) | `mvn test -pl module-12` | 16 tests pass |
| Run the module without Docker | `mvn test -pl module-12` | 11 tests pass; the 5 Keycloak tests are skipped |
| Leave Docker tests out explicitly | `mvn test -pl module-12 -Pno-docker` | 11 tests pass |
| Smoke tests only | `mvn test -pl module-12 -Psmoke` | 2 tests pass |
| Security tests only | `mvn test -pl module-12 -Psecurity` | the 5 Keycloak tests |

The first run downloads the Keycloak image, which takes a while.

## Guides

Each section has a step-by-step guide in the [`docs`](../docs) folder at the repository root. The guides explain the concepts behind the tests, walk through the code, and point out common mistakes. Read a section's guide, then run and change its tests.

| Section | Guide |
| --- | --- |
| 1. Real Tokens from Keycloak | [Real OAuth 2.0 Tokens From Keycloak: REST Assured and Testcontainers](../docs/module-12-section-01-rest-assured-keycloak-testcontainers.md) |
| 2. Running in Parallel | [Running REST Assured Tests in Parallel: JUnit Parallel Execution Without Shared State](../docs/module-12-section-02-rest-assured-parallel-execution.md) |
| 3. Tags and Profiles | [Tags and Maven Profiles for REST Assured Suites: Smoke, Security and No-Docker Runs](../docs/module-12-section-03-rest-assured-tags-and-maven-profiles.md) |

## Sections

### Section 1: Real Tokens from Keycloak

**Guide:** [module-12-section-01-rest-assured-keycloak-testcontainers.md](../docs/module-12-section-01-rest-assured-keycloak-testcontainers.md)

| Class | What it tests |
| --- | --- |
| [`KeycloakTokensTest`](src/test/java/com/example/module_12/section_01/KeycloakTokensTest.java) | OpenID Connect discovery; an accounts token reading accounts; a payments token initiating payments; an accounts token refused for payments; a wrong client secret; the claims Keycloak puts in a token |

**REST Assured concepts**

- `@Testcontainers(disabledWithoutDocker = true)` with a static `@Container` starts Keycloak once for the class and skips the class where Docker is missing.
- Discovery (`/.well-known/openid-configuration`) gives the token endpoint and JWKS URI, so nothing is hard-coded.
- Keycloak puts the client in `azp` and the audience in `aud`, which may be a string or a list.

### Section 2: Running in Parallel

**Guide:** [module-12-section-02-rest-assured-parallel-execution.md](../docs/module-12-section-02-rest-assured-parallel-execution.md)

| Class | What it tests |
| --- | --- |
| [`ParallelExecutionTest`](src/test/java/com/example/module_12/section_02/ParallelExecutionTest.java) | Eight requests that run concurrently with `@Execution(CONCURRENT)`; the threads used are printed at the end |

**REST Assured concepts**

- `src/test/resources/junit-platform.properties` enables parallel execution for this module, on four threads, and keeps everything sequential unless a class opts in.
- The rules: no static REST Assured configuration, an immutable `RequestSpecification` per class, and no shared mutable test data.

### Section 3: Tags and Profiles

**Guide:** [module-12-section-03-rest-assured-tags-and-maven-profiles.md](../docs/module-12-section-03-rest-assured-tags-and-maven-profiles.md)

| Class | What it tests |
| --- | --- |
| [`TaggedSuiteTest`](src/test/java/com/example/module_12/section_03/TaggedSuiteTest.java) | Two `@SmokeTest`s and a regression test |
| [`SmokeTest`](src/test/java/com/example/module_12/support/SmokeTest.java) | A composed annotation: `@Test` plus `@Tag("smoke")` |

**REST Assured concepts**

- The module's `pom.xml` defines the profiles `smoke`, `security` and `no-docker`, which set Surefire's `groups` and `excludedGroups`. A tag expression such as `-Dgroups="smoke | security"` works without a profile.
