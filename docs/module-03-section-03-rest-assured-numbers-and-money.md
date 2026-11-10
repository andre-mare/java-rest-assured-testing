# Numbers and Money in REST Assured: Float, BigDecimal and comparesEqualTo

Numbers and Money in REST Assured: Learn how REST Assured reads JSON numbers, why the default Float can't hold money, how to read decimals as BigDecimal with JsonConfig, why comparesEqualTo beats equalTo, and when closeTo is the right choice.

A bank statement has to add up. The opening balance plus every transaction must be exactly the closing balance, not almost. In [Module 1](module-01-section-04-first-rest-assured-assertions.md) you learned that REST Assured reads `1250.50` as a `Float`, and wrote `1250.50f` to make the check pass. That works for one value. But what happens when a test adds six amounts together? And why does `equalTo(new BigDecimal("1000"))` fail when the balance is `1000.00`?

This post follows [Section 3 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. It shows how REST Assured turns JSON numbers into Java numbers, and how to check amounts of money exactly. The examples are in [`NumbersAndMoneyTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_03/NumbersAndMoneyTest.java).

## How Does REST Assured Read a JSON Number?

JSON has one number type. REST Assured picks a Java type for each number in the body:

1. **A whole number** becomes an `Integer`, or a `Long` when it is too big for an `Integer`.
2. **A decimal** becomes a `Float` by default, or a `Double` when it is too big for a `Float`.

A `Float` holds about seven significant digits in binary, so it can't store most decimal amounts exactly. `-42.35` is stored as the nearest binary value, which is close to `-42.35` but not equal to it. For one value you rarely notice. When you add several, the small errors show up.

`JsonConfig` changes the type used for decimals. `NumberReturnType.BIG_DECIMAL` reads them as `BigDecimal`, which stores decimal values exactly. Since REST Assured 6.3, `NumberReturnType` lives in `io.restassured.common.config`; the one in `JsonPathConfig` is deprecated.

## The Data Under Test

The transactions of `acc-1001` from [Section 1](module-03-section-01-rest-assured-gpath-for-json.md), with a balance:

```json
{
  "balance": {"closing": 2407.65, "opening": 1000.00},
  "transactions": [
    {"id": "tx-1", "amount": 2500.00, ...},
    {"id": "tx-2", "amount": -42.35, ...},
    {"id": "tx-3", "amount": -1100.00, ...},
    {"id": "tx-4", "amount": -60.00, ...},
    {"id": "tx-5", "amount": -40.00, ...},
    {"id": "tx-6", "amount": 150.00, ...}
  ]
}
```

The amounts add up to `1407.65`, and `1000.00 + 1407.65` is the closing balance, `2407.65`.

Two helpers send the request. The second one sets `JsonConfig` on the request, so every number in its response is read as a `BigDecimal`:

```java
private static ValidatableResponse transactions(RequestSpecification request) {
    return request.when().get("/accounts/{id}/transactions", TransactionsApi.ACCOUNT_ID).then();
}

/** Decimals are read as BigDecimal for every request sent with this specification. */
private static RequestSpecification withBigDecimals() {
    return transactionsApi().config(config().jsonConfig(jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL)));
}
```

## The Default Types

### Step 1: Whole Numbers Are Integers

```java
@Test
void wholeNumbersAreIntegers() {
    transactions(transactionsApi()).body("transactions.size()", equalTo(6));
}
```

`equalTo(6)` compares with an `Integer`, so it matches.

### Step 2: Decimals Are Floats

```java
@Test
void decimalsAreFloatsByDefault() {
    transactions(transactionsApi())
        .body("transactions[1].amount", instanceOf(Float.class))
        .body("transactions[1].amount", equalTo(-42.35f))
        .body("transactions[1].amount", not(equalTo(-42.35)));      // a Double never equals a Float
}
```

- **`equalTo(-42.35f)`** passes, because both sides are the same `Float`.
- **`equalTo(-42.35)`** fails, because `-42.35` is a `Double`, and `Float.equals(..)` is false for any `Double`.

## Money as BigDecimal

### Step 3: Read Decimals as BigDecimal

```java
@Test
void decimalsAsBigDecimal() {
    transactions(withBigDecimals())
        .body("transactions[1].amount", instanceOf(BigDecimal.class))
        .body("transactions[1].amount", equalTo(new BigDecimal("-42.35")))
        .body("balance.closing", equalTo(new BigDecimal("2407.65")));
}
```

Create the expected value from a string, `new BigDecimal("-42.35")`. `new BigDecimal(-42.35)` takes a `double`, which is already inexact.

### Step 4: equalTo Is Strict About Scale

```java
@Test
void equalToIsStrictAboutScaleButComparesEqualToIsNot() {
    transactions(withBigDecimals())
        .body("balance.opening", not(equalTo(new BigDecimal("1000"))))   // 1000.00 has scale 2, 1000 has scale 0
        .body("balance.opening", comparesEqualTo(new BigDecimal("1000")));
}
```

A `BigDecimal` has a value and a scale, the number of digits after the point. `BigDecimal.equals(..)` compares both, so `1000.00` and `1000` are not equal. Without the `not(..)`, the failure looks like this:

```text
JSON path balance.opening doesn't match.
Expected: <1000>
  Actual: <1000.00>
```

`comparesEqualTo(..)` uses `compareTo(..)`, which compares only the value. For money, that is almost always what you want, so use `comparesEqualTo(..)` by default.

> **The code above, ready to run**
>
> All seven tests in [`NumbersAndMoneyTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_03/NumbersAndMoneyTest.java) pass with `mvn test -pl module-03 -Dtest=NumbersAndMoneyTest`. Replace `withBigDecimals()` with `transactionsApi()` in a sum test and read the failure.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Exact Sums

### Step 5: Sum a Filtered List

GPath's `sum()` adds a list of numbers. With `BigDecimal` values the result is exact:

```java
@Test
void sumsAreExactWithBigDecimal() {
    transactions(withBigDecimals())
        .body("transactions.findAll { it.type == 'DEBIT' }.amount.sum()", comparesEqualTo(new BigDecimal("-1242.35")))
        .body("transactions.findAll { it.type == 'CREDIT' }.amount.sum()", comparesEqualTo(new BigDecimal("2650")));
}
```

`2650` has no decimals, but the sum is `2650.00`. `comparesEqualTo(..)` doesn't mind.

### Step 6: The Net Movement

```java
@Test
void theNetMovementIsExact() {
    transactions(withBigDecimals())
        .body("transactions.amount.sum()", comparesEqualTo(new BigDecimal("1407.65")));
}
```

Run the same sum with the default `Float` values, and this is the result:

```text
JSON path transactions.amount.sum() doesn't match.
Expected: <1407.65F>
  Actual: <1407.650001525879>
```

Each `Float` was slightly off, and the errors added up. This is the reason money needs `BigDecimal`.

## Approximate Values

Not every number is money. For a measurement, such as a duration or an exchange rate from a model, a small difference is fine, and `closeTo(..)` allows a tolerance. It needs `Double` values:

```java
@Test
void closeToForValuesThatAreApproximateOnPurpose() {
    transactions(transactionsApi().config(config().jsonConfig(jsonConfig().numberReturnType(NumberReturnType.DOUBLE))))
        .body("transactions[1].amount", closeTo(-42.35, 0.001));
}
```

## Choosing a Number Type

| Value | Number type | Matcher |
| --- | --- | --- |
| A count, an id or a status | Default (`Integer`) | `equalTo(6)` |
| Money | `BIG_DECIMAL` | `comparesEqualTo(new BigDecimal("2407.65"))` |
| A measurement | `DOUBLE` | `closeTo(-42.35, 0.001)` |
| A quick check of a decimal | Default (`Float`) | `equalTo(-42.35f)` |

Module 5 shows how to set the number type once, in a shared specification, instead of on every request.

## Common Pitfalls

1. **Comparing a `Float` with a `Double`.** `equalTo(-42.35)` never matches the default `Float`. Use `-42.35f`, or read `BigDecimal`.
2. **Summing floats.** The result is off by a fraction. Read amounts as `BigDecimal` before you add them.
3. **`equalTo(..)` on `BigDecimal`.** It fails on scale alone: `1000.00` is not `1000`. Use `comparesEqualTo(..)`.
4. **`new BigDecimal(double)`.** `new BigDecimal(-42.35)` is `-42.35000000000000142...`. Always use the string constructor.
5. **`closeTo(..)` with the default number type.** `closeTo(..)` matches only `Double` values, so on a `Float` it fails, even though `-42.35F` is within the tolerance. Set `NumberReturnType.DOUBLE` first.

## Wrapping Up

REST Assured reads whole numbers as `Integer` and decimals as `Float`. A `Float` is fine for a quick check, but it can't hold money exactly, and its errors add up in a sum. Set `NumberReturnType.BIG_DECIMAL` with `JsonConfig`, create expected values from strings, and compare with `comparesEqualTo(..)`, which ignores the scale. Keep `closeTo(..)`, with `Double` values, for numbers that are approximate on purpose.
