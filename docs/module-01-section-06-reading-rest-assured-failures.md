# Reading REST Assured Failures: Failure Messages and Eager Validation

Reading REST Assured Failures: Learn to read REST Assured failure messages, why a then() chain stops at its first failing call, and how one body(..) call reports all its mismatches at once.

A test suite spends most of its life green, but you learn the most from it when it turns red. When an API test fails on CI at 2 a.m., the failure message is often all you have. Does it say which field was wrong? Does it show the actual value? And if three things are wrong, does it tell you about all three, or only the first one?

This post follows [Section 6 of Module 1](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-01) of the REST Assured course. It shows the real failure output of REST Assured 6, explains the shape of every message, and explains eager validation, which decides how many failures you see in one run. The examples are in [`FailureMessagesTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_06/FailureMessagesTest.java) and [`DeliberatelyFailingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/src/test/java/com/example/module_01/section_06/DeliberatelyFailingTest.java).

## What is a REST Assured Failure?

A failed expectation throws a plain `java.lang.AssertionError`, so JUnit reports it like any other failed assertion. Its message always has the same shape:

1. **A count**: `1 expectation failed.` or `2 expectations failed.`
2. **One paragraph per failure**: what was checked, then what was expected and what was found.
3. **The stack trace**: the line in your test where the failing check was made.

Because it is an ordinary `AssertionError`, you can also catch it with `assertThrows` and inspect the message, which is how `FailureMessagesTest` keeps the build green while still testing failures.

## How Validation Runs

After `then()`, REST Assured validates eagerly:

1. **Each call is checked at once.** `statusCode(..)`, `header(..)`, `contentType(..)` and `body(..)` each check the response as soon as they are called.
2. **The first failing call throws.** The rest of the chain never runs, so the message reports only that one call.
3. **One `body(..)` call is one check.** All path and matcher pairs inside a single `body(path, matcher, path, matcher, ..)` call are checked together, so the message reports every mismatch in it.

This decides how much a single red run tells you. Module 5 shows the same rule for a whole response specification.

## The Shapes of a Failure Message

These messages come from a real run of `DeliberatelyFailingTest`, a class whose tests fail on purpose.

### A Wrong Status Code

```java
accountsApi().when().get("/accounts/{id}", "acc-9999").then().statusCode(200);
```

```text
java.lang.AssertionError:
1 expectation failed.
Expected status code <200> but was <404>.
```

### A Wrong Body Value

```java
accountsApi().when().get("/accounts/{id}", "acc-1004").then().body("currency", equalTo("EUR"));
```

```text
java.lang.AssertionError:
1 expectation failed.
JSON path currency doesn't match.
Expected: EUR
  Actual: GBP
```

The message names the path, so you know which field to look at without reading the body.

### A Missing List Item

```java
accountsApi().when().get("/accounts").then().body("accounts.id", hasItems("acc-1001", "acc-2001"));
```

```text
java.lang.AssertionError:
1 expectation failed.
JSON path accounts.id doesn't match.
Expected: (a collection containing "acc-1001" and a collection containing "acc-2001")
  Actual: <[acc-1001, acc-1002, acc-1003, acc-1004]>
```

The `Expected` line is the Hamcrest description of the matcher, and the `Actual` line is the whole collected list.

### A Wrong Content Type

```java
accountsApi().when().get("/accounts").then().contentType(ContentType.XML);
```

```text
java.lang.AssertionError:
1 expectation failed.
Expected content-type "XML" doesn't match actual content-type "application/json; charset=utf-8".
```

> **The code above, ready to run**
>
> `FailureMessagesTest` passes as part of the normal build. `DeliberatelyFailingTest` is excluded by default. Run it with `mvn test -pl module-01 -Dtest=DeliberatelyFailingTest -DexcludedGroups=none` and expect `BUILD FAILURE` with five failed tests.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 1 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-01/README.md)

## Eager Validation in Practice

### Step 1: A Chain Stops at Its First Failure

Here the header check fails, so the `body(..)` check after it never runs:

```java
@Test
void aThenChainStopsAtTheFirstFailingCall() {
    AssertionError error = assertThrows(AssertionError.class, () ->
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .statusCode(200)                            // checked now: holds
            .header("Cache-Control", "max-age=60")      // checked now: fails and throws
            .body("owner", equalTo("Jan Jansen")));     // never reached

    String message = error.getMessage();
    assertTrue(message.startsWith("1 expectation failed."), message);
    assertTrue(message.contains("Expected header \"Cache-Control\""), message);
    assertFalse(message.contains("owner"), message);
}
```

The owner is also wrong, but this run doesn't tell you. You fix the header, run again, and only then find the second problem.

### Step 2: One body(..) Call Reports Everything

Put the paths into one `body(..)` call, and every mismatch is reported together:

```java
@Test
void allPathsOfOneBodyCallAreReportedTogether() {
    AssertionError error = assertThrows(AssertionError.class, () ->
        accountsApi()
        .when()
            .get("/accounts/{id}", "acc-1001")
        .then()
            .body("owner", equalTo("Jan Jansen"),       // fails
                  "currency", equalTo("EUR"),           // holds
                  "type", equalTo("SAVINGS")));         // fails

    String message = error.getMessage();
    assertTrue(message.startsWith("2 expectations failed."), message);
    assertTrue(message.contains("JSON path owner doesn't match."), message);
    assertTrue(message.contains("JSON path type doesn't match."), message);
}
```

### Step 3: Read a Multi-Failure Message With Logging

The last test in `DeliberatelyFailingTest` combines both ideas, and adds `log().ifValidationFails()` from [Section 5](module-01-section-05-rest-assured-logging.md):

```java
accountsApi()
    .log().ifValidationFails()                          // the request is printed with the failure
.when()
    .get("/accounts/{id}", "acc-1003")
.then()
    .log().ifValidationFails()
    .statusCode(200)                                    // holds
    .body("status", equalTo("ACTIVE"),                  // fails: the account is BLOCKED
          "balance", equalTo(-42.10),                   // fails: a double, but the value is read as a Float
          "owner", equalTo("Jan Jansen"));              // holds
```

The output is the logged request and response, followed by this message:

```text
java.lang.AssertionError:
2 expectations failed.
JSON path status doesn't match.
Expected: ACTIVE
  Actual: BLOCKED

JSON path balance doesn't match.
Expected: <-42.1>
  Actual: <-42.1F>
```

Look at the second failure closely. The expected and actual values look the same, apart from the `F`. That `F` is the clue that the actual value is a `Float` and the expected one a `double`, which is the pitfall from [Section 4](module-01-section-04-first-rest-assured-assertions.md).

## How the Failing Tests Are Kept Out of the Build

`DeliberatelyFailingTest` is useful to read, but it must not break the build:

1. **It is tagged.** The class has `@Tag("expected-failure-tests")`.
2. **The tag is excluded.** The parent POM sets the Maven property `excludedGroups` to that tag, so Surefire skips the class.
3. **You can override it.** `-DexcludedGroups=none` on the command line runs it.

## Reading Failures Well

1. **Read the count first.** `2 expectations failed.` tells you how many paragraphs to look for before the stack trace.
2. **Look for the path.** `JSON path currency doesn't match.` tells you which field to look at, without opening the body.
3. **Compare types, not just values.** When `Expected` and `Actual` look the same, the difference is the type, such as `-42.1` and `-42.1F`.
4. **Group related body checks.** Put paths that describe one object into one `body(..)` call, so one run reports all their mismatches.
5. **Log on failure.** `log().ifValidationFails()` puts the full request and response next to the message, which answers most of the remaining questions.

## Wrapping Up

A REST Assured failure is a plain `AssertionError`. Its message starts with the number of failed expectations and then explains each one, naming the path and showing the expected and actual values. After `then()`, each call is checked at once, so a chain stops at its first failing call. The pairs inside one `body(..)` call are checked together and are all reported. Add `log().ifValidationFails()`, and the request and response are printed with the failure.