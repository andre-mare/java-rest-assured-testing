# Root Paths and Path Arguments in REST Assured: rootPath, appendRootPath and withArgs

Root Paths and Path Arguments in REST Assured: Stop repeating the start of every path with rootPath, move it with appendRootPath, detachRootPath and noRootPath, and reuse one path for different values with withArgs.

A test that checks one transaction in detail soon looks like this: `transactions[2].id`, `transactions[2].category`, `transactions[2].counterparty.name`. The interesting part of each path is at the end, and the start is repeated every time. Change the index and you have to change it in three places. And when you want the same check for three different transactions, do you really copy the path three times?

This post follows [Section 2 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. It shows how a root path removes the repeated prefix, how to move it during a chain, and how path arguments make one path work for many values. The examples are in [`RootPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_02/RootPathTest.java).

## What is a Root Path?

A root path is a prefix that REST Assured puts in front of every path in the `body(..)` calls that follow it. Four methods control it:

1. **`rootPath(path)`** sets the prefix.
2. **`appendRootPath(path)`** adds a step to the end of the current prefix.
3. **`detachRootPath(path)`** removes a step from the end of the current prefix.
4. **`noRootPath()`** removes the prefix, so paths start at the top of the body again.

A path argument is a `%s` placeholder in a path, filled with `withArgs(..)`. It works like `String.format`, once per `body(..)` call.

## The Data Under Test

The tests use the transactions of `acc-1001` from [Section 1](module-03-section-01-rest-assured-gpath-for-json.md). The third transaction, `transactions[2]`, is the rent:

```json
{
  "id": "tx-3",
  "type": "DEBIT",
  "amount": -1100.00,
  "counterparty": {"iban": "NL27INGB0000123456", "name": "Woonstichting De Key"},
  "category": "RENT",
  ...
}
```

All four tests check the same response, returned by a helper:

```java
private static ValidatableResponse transactions() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH)
        .when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
        .then();
}
```

## Root Paths

### Step 1: Set a Root Path

```java
@Test
void aRootPathPrefixesEveryPath() {
    transactions()
        .rootPath("transactions[2]")
        .body("id", equalTo("tx-3"))                                // transactions[2].id
        .body("category", equalTo("RENT"))
        .body("counterparty.name", equalTo("Woonstichting De Key"));
}
```

Each path is joined to the root path with a dot, so `"id"` is read as `transactions[2].id`. The index now appears once. To check a different transaction, change one line.

A failure message shows the full path, root path included, so you can still see what was checked:

```text
JSON path transactions[2].category doesn't match.
```

### Step 2: Move the Root Path

A root path can change during the chain:

```java
@Test
void movingTheRootPath() {
    transactions()
        .rootPath("transactions[0]")
        .body("id", equalTo("tx-1"))
        .appendRootPath("counterparty")                             // now transactions[0].counterparty
        .body("name", equalTo("Acme B.V."))
        .detachRootPath("counterparty")                             // back to transactions[0]
        .body("category", equalTo("SALARY"))
        .noRootPath()                                               // back to the top of the body
        .body("currency", equalTo("EUR"));
}
```

| Call | Root path afterwards | `body("x", ..)` reads |
| --- | --- | --- |
| `rootPath("transactions[0]")` | `transactions[0]` | `transactions[0].x` |
| `appendRootPath("counterparty")` | `transactions[0].counterparty` | `transactions[0].counterparty.x` |
| `detachRootPath("counterparty")` | `transactions[0]` | `transactions[0].x` |
| `noRootPath()` | none | `x` |

This reads like moving through the body: into a transaction, into its counterparty, back out, and back to the top for the account currency.

> **The code above, ready to run**
>
> All four tests in [`RootPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_02/RootPathTest.java) pass with `mvn test -pl module-03 -Dtest=RootPathTest`. Remove the `noRootPath()` call and read the failure: the path becomes `transactions[0].currency`.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Path Arguments

### Step 3: Fill a Placeholder With withArgs

```java
@Test
void pathArgumentsFillPlaceholders() {
    String categoryOfTransaction = "transactions.find { it.id == '%s' }.category";

    transactions()
        .body(categoryOfTransaction, withArgs("tx-1"), equalTo("SALARY"))
        .body(categoryOfTransaction, withArgs("tx-3"), equalTo("RENT"))
        .body(categoryOfTransaction, withArgs("tx-5"), equalTo("TRANSPORT"));
}
```

- **The path** is written once, in a variable with a name that says what it reads.
- **`withArgs(..)`** comes between the path and the matcher. It is a static import from `io.restassured.RestAssured`.
- **`%s`** is replaced by the argument before the path is evaluated. The quotes around it belong to the GPath string, so the id is compared as text.

### Step 4: A Root Path With a Placeholder

The placeholder can be in the root path, and the argument still goes in the `body(..)` call:

```java
@Test
void aRootPathWithAPlaceholder() {
    transactions()
        .rootPath("transactions.find { it.id == '%s' }")
        .body("counterparty.name", withArgs("tx-2"), equalTo("Albert Heijn"))
        .body("counterparty.name", withArgs("tx-6"), equalTo("Belastingdienst"));
}
```

The root path describes *which kind of* element to look at, and each check says *which one*. Unlike an index, the `find { .. }` keeps working when the order of the transactions changes.

## When to Use Which

1. **No root path** for a few checks spread across the body.
2. **`rootPath(..)`** for several checks on one object deep in the body.
3. **`appendRootPath(..)` and `detachRootPath(..)`** when the checks move into and out of a nested object.
4. **`withArgs(..)`** when the same path is checked for several values.

## Common Pitfalls

1. **Forgetting that the root path stays.** It applies to every `body(..)` call after it, until you change it. A check on a top-level field after a root path reads the wrong path and gets `null`. Call `noRootPath()` first.
2. **Too few arguments.** A placeholder without an argument stays in the path as `%s`, so the path looks for an id that is literally `%s` and returns `null`. Pass one argument per `%s`.
3. **Leaving out the quotes around `%s`.** `it.id == %s` becomes `it.id == tx-1`, which Groovy reads as `tx` minus `1`, and fails with `The parameter "tx" was used but not defined`. Keep `'%s'` for text values.
4. **Indexes where a condition is safer.** `transactions[2]` breaks when the data is reordered. `transactions.find { it.id == '%s' }` does not.
5. **Root paths that hide too much.** A chain with five moves of the root path is hard to follow. Split it into separate tests, or write the full paths.

## Wrapping Up

A root path removes the repeated start of each path. Set it with `rootPath(..)`, move it with `appendRootPath(..)` and `detachRootPath(..)`, and clear it with `noRootPath()`. Path arguments fill `%s` placeholders, in a path or in a root path, so one path can check many values. Failure messages always show the full path, so neither technique makes a failure harder to read.
