# The Hamcrest Toolbox for REST Assured: Collection, Map, String and Combined Matchers

The Hamcrest Toolbox for REST Assured: Pick the right Hamcrest matcher for each check, covering hasItems, contains and containsInAnyOrder, everyItem and empty, hasKey and hasEntry, string matchers, and combining matchers with allOf, anyOf, oneOf and not.

Every check after `then()` takes a Hamcrest matcher, and Hamcrest has dozens of them. For a list of ids, `hasItems`, `contains` and `containsInAnyOrder` can all make a test pass. They don't mean the same thing, though. One says "at least these", another "exactly these, in this order". Pick the wrong one, and a test passes when the API returns an extra transaction, or fails when the order changes and nobody cares about the order.

This post follows [Section 4 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. It groups the matchers you will use most by what they check, and shows what each one really promises. The examples are in [`HamcrestToolboxTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_04/HamcrestToolboxTest.java), and the [Hamcrest tutorial](https://hamcrest.org/JavaHamcrest/tutorial) lists the rest.

## What is a Hamcrest Matcher?

A matcher is an object that checks a value and describes what it expected. REST Assured passes the value at a path to the matcher, and when the matcher fails, it uses the description in the failure message. That is why a good choice of matcher gives a good failure message.

All the matchers below are static methods of `org.hamcrest.Matchers`, which comes with REST Assured. They fall into four groups:

1. **Collections**: `hasSize`, `hasItem`, `hasItems`, `contains`, `containsInAnyOrder`, `everyItem` and `empty`.
2. **Maps**: `hasKey`, `hasEntry` and `hasValue`.
3. **Strings**: `startsWith`, `containsString`, `matchesPattern` and `equalToIgnoringCase`.
4. **Combinations**: `allOf`, `anyOf`, `oneOf` and `not`.

## The Data Under Test

The tests check the transactions of `acc-1001` from [Section 1](module-03-section-01-rest-assured-gpath-for-json.md). The fields that matter here:

- **`iban`**: `NL91ABNA0417164300`.
- **`currency`**: `EUR`.
- **`balance`**: an object with `opening` and `closing`, and no `available`.
- **`transactions`**: six transactions, `tx-1` to `tx-6`, in the categories `SALARY`, `GROCERIES`, `RENT`, `TRANSPORT` and `REFUND`.

## Collection Matchers

```java
@Test
void collections() {
    transactions()
        .body("transactions.id", hasSize(6))
        .body("transactions.category", hasItem("RENT"))                              // at least this one
        .body("transactions.category", hasItems("RENT", "SALARY"))                   // at least these
        .body("transactions[0..2].id", contains("tx-1", "tx-2", "tx-3"))             // exactly these, in order
        .body("transactions[0..2].id", containsInAnyOrder("tx-3", "tx-1", "tx-2"))   // exactly these, any order
        .body("transactions.id", everyItem(startsWith("tx-")))
        .body("transactions.findAll { it.category == 'GAMBLING' }", empty());
}
```

The three list matchers that are easiest to confuse:

| Matcher | Extra items | Order |
| --- | --- | --- |
| `hasItems("a", "b")` | Allowed | Doesn't matter |
| `containsInAnyOrder("a", "b")` | Not allowed | Doesn't matter |
| `contains("a", "b")` | Not allowed | Must match |

Pick the weakest matcher that still says what the API promises. If the API returns transactions by date, `contains(..)` checks that. If it promises only that the rent is among them, `hasItem(..)` is enough, and the test won't break when a new transaction is added.

`contains(..)` reports both lists when it fails, so you can see the difference at once:

```text
JSON path transactions[0..2].id doesn't match.
Expected: iterable containing ["tx-2", "tx-1", "tx-3"]
  Actual: <[tx-1, tx-2, tx-3]>
```

- **`everyItem(..)`** applies a matcher to every element. It passes on an empty list, so pair it with `hasSize(..)` or `not(empty())` when the list must have elements.
- **`empty()`** checks for an empty list, here the result of a `findAll` that should find nothing.

## Map Matchers

A JSON object is a map, so map matchers work on it:

```java
@Test
void maps() {
    transactions()
        .body("transactions[0]", hasKey("remittance-info"))
        .body("transactions[0].counterparty", hasEntry("name", "Acme B.V."))
        .body("balance", not(hasKey("available")));
}
```

- **`hasKey(..)`** checks that a field exists, whatever its value.
- **`hasEntry(key, value)`** checks a field and its value together.
- **`not(hasKey(..))`** checks that a field is absent. That is different from `body("balance.available", nullValue())`, which also passes when the field exists with the value `null`.

> **The code above, ready to run**
>
> All four tests in [`HamcrestToolboxTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_04/HamcrestToolboxTest.java) pass with `mvn test -pl module-03 -Dtest=HamcrestToolboxTest`. Swap `contains` and `hasItems` on the full list of ids, and compare the two failure messages.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## String Matchers

```java
@Test
void strings() {
    transactions()
        .body("iban", startsWith("NL"))
        .body("iban", containsString("ABNA"))
        .body("iban", matchesPattern("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"))
        .body("iban", equalToIgnoringCase("nl91abna0417164300"));
}
```

- **`startsWith(..)`** and **`containsString(..)`** check part of a value.
- **`matchesPattern(..)`** checks the format with a regular expression. The whole value must match, not just part of it. It is the right check for values the server generates, such as ids, timestamps and IBANs.
- **`equalToIgnoringCase(..)`** ignores upper and lower case.

## Combining Matchers

```java
@Test
void combiningMatchers() {
    transactions()
        .body("balance", allOf(hasKey("opening"), hasKey("closing")))
        .body("currency", anyOf(equalTo("EUR"), equalTo("GBP")))
        .body("transactions.category", everyItem(oneOf("SALARY", "GROCERIES", "RENT", "TRANSPORT", "REFUND")))
        .body("transactions.type", not(hasItem("PENDING")));
}
```

- **`allOf(..)`** passes when every matcher passes.
- **`anyOf(..)`** passes when at least one matcher passes.
- **`oneOf(..)`** passes when the value equals one of the given values. Inside `everyItem(..)`, it checks that every category is a known one.
- **`not(..)`** inverts a matcher.

`allOf(..)` is useful inside a matcher that takes one matcher, such as `everyItem(..)`. In a `then()` chain, two separate `body(..)` calls do the same job and give two clearer failure messages.

## Common Pitfalls

1. **`contains(..)` when you mean "at least".** `contains("tx-1", "tx-2")` fails on a list of six ids, because it expects exactly two. Use `hasItems(..)`.
2. **`hasItems(..)` when you mean "exactly".** It passes when the API returns extra, unexpected items. Use `containsInAnyOrder(..)`.
3. **`everyItem(..)` on an empty list.** It passes. Check the size as well.
4. **`nullValue()` to check that a field is absent.** A missing field and a field set to `null` both pass. Use `not(hasKey(..))` on the parent object when the difference matters.
5. **`containsString(..)` on a value you could check exactly.** It hides wrong values that happen to contain the text. Use `equalTo(..)` or `matchesPattern(..)` when you know the format.

## Wrapping Up

Choose a matcher for what the API promises. `hasItems` means at least these, `containsInAnyOrder` exactly these in any order, and `contains` exactly these in this order. `everyItem` and `empty` check a whole list, `hasKey` and `hasEntry` check objects, and the string matchers check part or all of a value. Combine matchers with `allOf`, `anyOf`, `oneOf` and `not`. The right choice makes the test precise, and its failure message clear.
