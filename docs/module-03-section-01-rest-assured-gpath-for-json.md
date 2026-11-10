# GPath for JSON in REST Assured: find, findAll, collect, Ranges and Root Arrays

GPath for JSON in REST Assured: Filter and transform JSON lists with Groovy closures, covering find, findAll, collect, any and every, max and min, indexes and ranges, flattening, keys with dashes and a JSON array at the root.

In [Module 1](module-01-section-04-first-rest-assured-assertions.md) you read single values with paths such as `accounts[0].id`, and collected a field from every element with `accounts.id`. Real checks are often more specific. Which counterparty received the rent? Are all four debits the ones you expect? Does every transaction id start with `tx-`? You could extract the whole list and loop over it in Java, but GPath can answer each of these questions in one line, inside `body(..)`.

This post is the first in the series for [Module 3 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03), which covers checking any part of a response precisely. It shows the GPath features you will use most on JSON lists. The examples are in [`JsonGPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_01/JsonGPathTest.java), and the [REST Assured usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#example-3---complex-parsing-and-validation) has more examples.

## What is GPath?

GPath is the path language of Groovy. REST Assured 6 still evaluates it with Groovy, so a path can do more than walk into fields:

1. **A field or an index**: `iban`, `transactions[0].id` and `transactions[-1].id`, as in Module 1.
2. **A collected field**: `transactions.id` walks into every element of the list and returns a list of ids.
3. **A closure in braces**: `transactions.findAll { it.type == 'DEBIT' }` runs the code in braces for each element. Inside it, `it` is the current element.
4. **A Groovy method**: `size()`, `unique()`, `flatten()`, `sum()` and others work on any list in the path.

Strings inside a closure use single quotes, so the path still fits in a Java string without escaping.

## The Data Under Test

Module 3 tests a Transactions API with one account, `acc-1001`, and a month of transactions. `GET /api/v1/accounts/acc-1001/transactions` returns (shortened):

```json
{
  "accountId": "acc-1001",
  "iban": "NL91ABNA0417164300",
  "currency": "EUR",
  "balance": {"closing": 2407.65, "opening": 1000.00},
  "transactions": [
    {
      "id": "tx-1",
      "bookingDate": "2026-03-01",
      "type": "CREDIT",
      "amount": 2500.00,
      "counterparty": {"iban": "NL02ABNA0123456789", "name": "Acme B.V."},
      "category": "SALARY",
      "tags": ["income"],
      "remittance-info": "Salary March"
    },
    ...
  ]
}
```

The six transactions never change, so tests can assert exact values:

| id | Date | Type | Amount | Counterparty | Category |
| --- | --- | --- | --- | --- | --- |
| tx-1 | 2026-03-01 | CREDIT | 2500.00 | Acme B.V. | SALARY |
| tx-2 | 2026-03-02 | DEBIT | -42.35 | Albert Heijn | GROCERIES |
| tx-3 | 2026-03-05 | DEBIT | -1100.00 | Woonstichting De Key | RENT |
| tx-4 | 2026-03-09 | DEBIT | -60.00 | Albert Heijn | GROCERIES |
| tx-5 | 2026-03-15 | DEBIT | -40.00 | NS Reizigers | TRANSPORT |
| tx-6 | 2026-03-20 | CREDIT | 150.00 | Belastingdienst | REFUND |

Amounts are JSON numbers, and debits are negative. Each transaction also has a list of `tags` and a `remittance-info` field, a key with a dash in it, on purpose.

Most tests check the same response, so a helper sends the request and returns the `ValidatableResponse`:

```java
private static ValidatableResponse transactions() {
    return transactionsApi().when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID).then();
}
```

## Filtering a List

### Step 1: find Returns the First Match

```java
@Test
void findReturnsTheFirstMatch() {
    transactions().body("transactions.find { it.category == 'RENT' }.counterparty.name",
            equalTo("Woonstichting De Key"));
}
```

`find { .. }` returns the first element for which the closure is true, so the path can carry on into its fields. When nothing matches, it returns `null`.

### Step 2: findAll Returns Every Match

```java
@Test
void findAllReturnsEveryMatch() {
    transactions().body("transactions.findAll { it.type == 'DEBIT' }.id",
            contains("tx-2", "tx-3", "tx-4", "tx-5"));
}
```

`findAll { .. }` returns a list, so `.id` after it collects the id of every match. When nothing matches, the list is empty.

### Step 3: Conditions Can Look Into Nested Objects

```java
@Test
void conditionsCanLookIntoNestedObjects() {
    transactions().body("transactions.findAll { it.counterparty.name == 'Albert Heijn' }.size()", equalTo(2));
}
```

`it` is a whole transaction, so the condition can use any field in it, at any depth. A dot inside a quoted string, as in `'Acme B.V.'`, is part of the string, not a step in the path.

## Transforming and Testing a List

### collect Transforms Every Element

```java
@Test
void collectTransformsEveryElement() {
    transactions().body("transactions.collect { it.counterparty.name }.unique().size()", equalTo(5));
}
```

`collect { .. }` turns each element into the value of the closure. Here that is the counterparty name. `unique()` removes the second Albert Heijn, which leaves five names.

### any and every Return a Boolean

```java
@Test
void anyAndEvery() {
    transactions()
        .body("transactions.any { it.category == 'RENT' }", is(true))
        .body("transactions.every { it.id.startsWith('tx-') }", is(true));
}
```

A closure can call Java methods on the values, such as `startsWith(..)` on a string.

### max and min Compare by a Closure

```java
@Test
void maxAndMinCompareByAClosure() {
    transactions()
        .body("transactions.max { it.amount }.id", equalTo("tx-1"))
        .body("transactions.min { it.amount }.counterparty.name", equalTo("Woonstichting De Key"));
}
```

`max { it.amount }` returns the whole element with the highest amount, not the amount itself, so the path can go on to `.id`.

> **The code above, ready to run**
>
> All 11 tests in [`JsonGPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_01/JsonGPathTest.java) pass with `mvn test -pl module-03 -Dtest=JsonGPathTest`. Change a closure, predict the result, and read the failure message, which repeats the path you wrote.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Indexes and Ranges

```java
@Test
void indexesAndRanges() {
    transactions()
        .body("transactions[0].id", equalTo("tx-1"))
        .body("transactions[-1].id", equalTo("tx-6"))               // from the end
        .body("transactions[0..1].id", contains("tx-1", "tx-2"))    // a range, both ends included
        .body("transactions[-2..-1].id", contains("tx-5", "tx-6"));
}
```

- **A negative index** counts from the end: `[-1]` is the last element.
- **A range** `[0..1]` includes both ends, so it returns two elements. Negative ranges work too.

## Lists of Lists

Each transaction has a list of tags, so `transactions.tags` is a list of lists:

```java
@Test
void listsOfListsCanBeFlattened() {
    transactions()
        .body("transactions.tags", hasSize(6))                      // one list of tags per transaction
        .body("transactions.tags.flatten().unique()", hasItems("income", "food", "housing", "tax"));
}
```

`flatten()` turns the six lists into one list of tags, and `unique()` removes the duplicates. Without `flatten()`, a check for `"food"` fails, and the failure shows why:

```text
JSON path transactions.tags doesn't match.
Expected: a collection containing "food"
  Actual: <[[income], [food], [housing, fixed], [food], [travel], [income, tax]]>
```

## Keys With a Dash

```java
@Test
void keysWithADash() {
    transactions()
        .body("transactions[0].remittance-info", equalTo("Salary March"))
        .body("transactions[0].'remittance-info'", equalTo("Salary March"));   // quoting works too
}
```

REST Assured reads `remittance-info` as a key, even without quotes. Quoting it makes the intent clear and is the safe choice in more complex paths, as in the echo paths of [Module 2](module-02-section-03-rest-assured-headers-cookies-content-types.md).

## Letting the Server Filter

GPath filters the response on the client. When an API can filter for you, test that too:

```java
@Test
void theServerCanFilterToo() {
    transactionsApi()
        .queryParam("type", "credit")
    .when()
        .get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID)
    .then()
        .body("transactions.type", everyItem(equalTo("CREDIT")))
        .body("transactions.id", contains("tx-1", "tx-6"));
}
```

The test checks that every returned transaction is a credit, and that none is missing.

## A JSON Array at the Root

Not every body is an object. `GET /api/v1/categories` returns a list:

```json
[{"code":"SALARY","label":"Salary"},{"code":"GROCERIES","label":"Groceries"}, ...]
```

```java
@Test
void aJsonArrayAtTheRoot() {
    transactionsApi()
    .when()
        .get("/categories")
    .then()
        .body("$", hasSize(5))                                      // "$" is the root
        .body("size()", equalTo(5))
        .body("code", hasItems("SALARY", "RENT"))                   // a path on the root list walks into every element
        .body("find { it.code == 'RENT' }.label", equalTo("Rent"));
}
```

- **`$`** is the whole body. On a root array, `""` works as well.
- **A path without a list name**, such as `code` or `find { .. }`, starts at the root list.

## Common Pitfalls

1. **A decimal number inside a closure.** REST Assured splits a path on its dots, so `find { it.amount == -42.35 }` breaks into pieces and fails with `No signature of method`. Filter on another field, or compare the text: `find { it.amount.toString() == '-42.35' }`. Whole numbers, such as `findAll { it.amount < -50 }`, work.
2. **Using `find` when you mean `findAll`.** `find` returns one element and `findAll` a list. A matcher such as `hasSize(..)` or `contains(..)` after `find` checks the fields of one transaction, not a list of them.
3. **Forgetting `flatten()`.** `transactions.tags` is a list of lists, so `hasItem("food")` fails on it.
4. **Treating `[0..1]` as half-open.** A Groovy range includes both ends, so it has two elements, not one.
5. **Double quotes inside a closure.** They work, but they need escaping in Java: `"... == \"RENT\" ..."`. Single quotes are easier to read.

## Wrapping Up

GPath turns a list check into one line. `find` returns the first match and `findAll` every match. `collect` transforms each element, and `any`, `every`, `max` and `min` answer questions about the whole list. Indexes can count from the end, ranges include both ends, and `flatten()` turns a list of lists into one list. Keys with dashes can be quoted, and `$` is the root, also when the body itself is an array.
