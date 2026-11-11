# Tags and Maven Profiles for REST Assured Suites: Smoke, Security and No-Docker Runs

Tags and Maven Profiles for REST Assured Suites: Organise a large API test suite with JUnit tags and a composed @SmokeTest annotation, and run the right subset with Maven profiles that set Surefire's groups and excludedGroups, or with a tag expression on the command line.

A test suite grows. At first, running everything on every change is fine. Then it takes twenty minutes, half of it Keycloak and other containers, and not every situation needs all of it. After a deployment you want to know in a minute whether the service is up. Before a release you want every security check. On a laptop without Docker you want everything that can run there. One suite, several purposes. Tags describe what each test is for, and Maven profiles pick the subset to run.

This post follows [Section 3 of Module 12](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-12) of the REST Assured course, and ends the course. It tags API tests and runs subsets of them with Maven profiles. The examples are in [`TaggedSuiteTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_03/TaggedSuiteTest.java), [`SmokeTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/support/SmokeTest.java) and the module's [`pom.xml`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/pom.xml).

## What are Tags and Profiles?

1. **A tag** is a label on a test class or method: `@Tag("smoke")`. A test can have several, and a class's tags apply to all its methods.
2. **Surefire's `groups` and `excludedGroups`** select tests by tag: only the tests with these tags, or every test except those with these tags. They accept JUnit **tag expressions** such as `smoke | security`.
3. **A Maven profile** is a named set of build settings, switched on with `-P<name>`. Here, each profile sets `groups` or `excludedGroups`.

## The Tags in This Module

| Tag | On | Meaning |
| --- | --- | --- |
| `smoke` | Two tests in `TaggedSuiteTest` | Fast checks that the service is up and basically works |
| `regression` | One test in `TaggedSuiteTest` | A slower, more detailed check |
| `reference-data` | `TaggedSuiteTest` | Tests of the reference data API |
| `keycloak`, `security` | `KeycloakTokensTest` | Needs Docker; checks authentication and authorisation |

## A Composed Annotation for Smoke Tests

```java
/** A test that belongs in the smoke suite: fast, and checks that the service is up and basically works. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@Tag("smoke")
public @interface SmokeTest {
}
```

`@SmokeTest` combines `@Test` and `@Tag("smoke")`. A smoke test then needs one annotation, and the tag name can't be misspelled. Composed annotations are covered in Module 1 of the [JUnit course](https://github.com/andre-mare/java-unit-testing), and [Module 5](module-05-section-05-rest-assured-junit-extension.md) used one to combine an extension and a tag.

## The Tagged Tests

```java
@Tag("reference-data")
class TaggedSuiteTest {

    @SmokeTest
    void theServiceAnswers() {
        referenceData()
        .when()
            .get("/currencies")
        .then()
            .statusCode(200)
            .time(lessThan(2L), TimeUnit.SECONDS);
    }

    @SmokeTest
    void theMainCurrencyIsThere() {
        referenceData().when().get("/currencies/{code}", "EUR").then().body("name", equalTo("Euro"));
    }

    @Test
    @Tag("regression")
    void pastHolidaysAreKept() {
        referenceData().when().get("/holidays/{country}", "NL").then().body("dates[0]", equalTo("2026-01-01"));
    }
}
```

A good smoke test is fast and answers one question: is the service up and doing its basic job? The first checks that the service answers within a generous time, the second that its most important data is there. Detailed checks, like which holidays are kept, belong in the full suite.

## The Profiles

The module's `pom.xml` defines three profiles:

```xml
<profiles>
    <profile>
        <id>smoke</id>
        <properties>
            <groups>smoke</groups>
        </properties>
    </profile>
    <profile>
        <id>security</id>
        <properties>
            <groups>security</groups>
        </properties>
    </profile>
    <profile>
        <id>no-docker</id>
        <properties>
            <excludedGroups>keycloak,expected-failure-tests</excludedGroups>
        </properties>
    </profile>
</profiles>
```

Surefire reads the `groups` and `excludedGroups` properties. The parent POM sets `excludedGroups` to `expected-failure-tests`, the deliberately failing tests from [Module 1](module-01-section-06-reading-rest-assured-failures.md). The `no-docker` profile replaces that value, so it lists that tag again next to `keycloak`. Forget it, and the failing tests would run.

## Running Subsets

Each command below was run against this module with Docker available:

| Command | Runs | Tests |
| --- | --- | --- |
| `mvn test -pl module-12` | Everything | 16 |
| `mvn test -pl module-12 -Psmoke` | `@Tag("smoke")` only | 2 |
| `mvn test -pl module-12 -Psecurity` | `@Tag("security")` only: the Keycloak tests | 5 |
| `mvn test -pl module-12 -Pno-docker` | Everything except `keycloak` | 11 |
| `mvn test -pl module-12 -Dgroups="smoke \| security"` | Smoke or security | 7 |

The smoke run takes under a second for the tests themselves. The security run takes about eleven seconds, almost all of it Keycloak starting.

### Tag Expressions Without a Profile

```bash
mvn test -pl module-12 -Dgroups="smoke | security"
```

`groups` accepts JUnit tag expressions: `|` for or, `&` for and, `!` for not, and parentheses. Quote the expression so the shell doesn't treat `|` as a pipe. Profiles are names for the subsets a team uses every day; expressions are for everything else.

> **The code above, ready to run**
>
> All three tests in [`TaggedSuiteTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_03/TaggedSuiteTest.java) pass with `mvn test -pl module-12 -Dtest=TaggedSuiteTest`. Try each profile from the table, and compare the test counts.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 12 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/README.md)

## Docker: Skipped or Excluded?

There are two ways to deal with a machine without Docker, and they look different in the report:

| | `disabledWithoutDocker = true` | `-Pno-docker` |
| --- | --- | --- |
| Without Docker | The Keycloak tests are **skipped**: 11 pass, 5 skipped | The Keycloak tests are **not selected**: 11 pass |
| With Docker | They run | They are still left out |
| Use for | Laptops, as a safety net | CI agents that never have Docker |

The [Section 1](module-12-section-01-rest-assured-keycloak-testcontainers.md) class uses both: the annotation keeps a laptop build green, and the `keycloak` tag lets a pipeline leave the tests out on purpose.

## A Suite in a Pipeline

| Stage | Command | Why |
| --- | --- | --- |
| Every commit | `-Pno-docker` | Fast feedback without containers |
| Nightly or before merge | Everything | The full suite, with real infrastructure |
| After each deployment | `-Psmoke`, against the deployed service | Is it up, in a minute? |
| Before a release | `-Psecurity` | Every authentication and authorisation check |

## Common Pitfalls

1. **Misspelled tags.** `@Tag("smok")` silently leaves a test out of the smoke run. Use composed annotations such as `@SmokeTest`.
2. **Replacing the parent's `excludedGroups`.** A profile that sets `excludedGroups` overrides the parent's value, so the deliberately failing tests come back. List every tag to exclude.
3. **An unquoted tag expression.** `-Dgroups=smoke | security` pipes Maven's output into a command called `security`. Quote it.
4. **Smoke tests that aren't fast.** A smoke suite that needs Keycloak, or checks every field, isn't a smoke suite. Keep it to a handful of quick, essential checks.
5. **Tags nobody runs.** A tag only helps if a pipeline uses it. Document the profiles, and use them in CI.

## Wrapping Up

Tags describe what each test is for, and a composed annotation such as `@SmokeTest` makes the important ones impossible to misspell. Surefire's `groups` and `excludedGroups` select tests by tag, Maven profiles give the everyday subsets names (`smoke`, `security`, `no-docker`), and a tag expression such as `"smoke | security"` covers the rest. Combined with `disabledWithoutDocker`, parallel execution and the techniques from the earlier modules, one REST Assured suite can serve every stage of a pipeline, from a one-minute smoke check after a deployment to a full run against real infrastructure.
