# Parameterized API Tests With REST Assured: Validation Rules as a Table

Parameterized API Tests With REST Assured: Test many inputs without copying tests, covering a validation matrix in a @CsvSource text block, boundary values with @ValueSource, @FieldSource and @MethodSource, a whole class per currency with @ParameterizedClass, and logging only failing exchanges.

A payment API has rules for every field. The IBAN's check digits must be right, the amount needs at most two decimals and can't exceed the single-payment limit, the currency must be supported, the execution date can't be in the past. Fourteen rules make fourteen tests that are identical except for one field and one message. Copy them and the next rule means another copy, and a fix in the shared part means fourteen fixes. JUnit's parameterized tests turn them into one test and a table.

This post is the first in the series for [Module 9 of the REST Assured course](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-09), which covers data-driven tests and API workflows. It drives REST Assured requests from tables of inputs. The examples are in [`ValidationMatrixTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_01/ValidationMatrixTest.java) and [`PerCurrencyTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_01/PerCurrencyTest.java). Parameterized tests are covered in depth in Module 7 of the [JUnit course](https://github.com/andre-mare/java-unit-testing).

## What is a Parameterized API Test?

A `@ParameterizedTest` runs one test method once for each set of arguments. JUnit has several sources for the arguments:

| Source | Arguments come from |
| --- | --- |
| `@CsvSource` | Rows of comma- (or otherwise) separated values, here in a text block |
| `@ValueSource` | A list of single values |
| `@FieldSource` | A static field, such as a `List` |
| `@MethodSource` | A static method that returns a `Stream` of `Arguments` |

`@ParameterizedClass`, available since JUnit 5.13, goes one step further: it runs a whole test class once per value.

Each invocation is its own test, with its own name from the `name` pattern, so a failure says exactly which input broke.

## The API Under Test

Module 9 tests a payment lifecycle API. Every request needs `Authorization: Bearer test-token`, and `POST /api/v1/payments` also needs an `Idempotency-Key`. A valid request creates a payment:

```json
{
  "paymentId": "pay-6",
  "status": "INITIATED",
  "debtorIban": "NL91ABNA0417164300",
  "creditorIban": "NL69INGB0123456789",
  "creditorName": "Jan Jansen",
  "amount": "120.00",
  "currency": "EUR",
  "reference": null,
  "executionDate": "2026-11-11",
  "statusHistory": [{"status": "INITIATED", "at": "2026-11-11T07:06:46.212890Z"}]
}
```

Validation is real: IBANs are checked with the ISO 13616 mod-97 check digits, amounts need at most two decimals and at most `100000.00`, currencies are `EUR`, `GBP` or `USD`, and an execution date can't be in the past. An invalid field gives a `422` with one entry per field:

```json
{"type":"about:blank","title":"Unprocessable Content","status":422,"detail":"The request has invalid fields",
 "instance":"/api/v1/payments","errors":[{"field":"amount","detail":"must have at most two decimals"}]}
```

## Shared Set-Up

All tests in the module use [`PaymentsClient`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/support/PaymentsClient.java):

```java
public static RequestSpecification spec(PaymentLifecycleApi api) {
    return new RequestSpecBuilder()
        .setBaseUri(api.baseUri())
        .setPort(api.port())
        .setBasePath(PaymentLifecycleApi.BASE_PATH)
        .addHeader("Authorization", "Bearer " + PaymentLifecycleApi.TOKEN)
        .setContentType(ContentType.JSON)
        // Data-driven suites send many requests: print them only for a failing test.
        .setConfig(config().logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL)))
        .build();
}

/** A request with a fresh idempotency key, ready to POST a payment. */
public static RequestSpecification newPayment(PaymentLifecycleApi api) {
    return given(spec(api)).header("Idempotency-Key", UUID.randomUUID().toString());
}
```

`validPayment(amount)` returns a valid payment as a mutable `Map`, so each test changes only what it is about.

A data-driven run sends dozens of requests. `enableLoggingOfRequestAndResponseIfValidationFails(..)` in the specification's `LogConfig` logs an exchange only when its validation fails, so a passing run stays quiet and a failing row prints exactly what it sent and got.

## A Validation Matrix

### Step 1: Fourteen Invalid Inputs as a Table

```java
@ParameterizedTest(name = "{0} = ''{1}'' -> {2}")
@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
    debtorIban   | NL00ABNA0417164300     | must be a valid IBAN
    debtorIban   | NL91ABNA0417164301     | must be a valid IBAN
    creditorIban | nl69ingb0123456789     | must be a valid IBAN
    creditorIban | NL69INGB               | must be a valid IBAN
    creditorName | ""                     | must be 1 to 70 characters
    amount       | 0.00                   | must be more than zero
    amount       | -5.00                  | must be more than zero
    amount       | 1.234                  | must have at most two decimals
    amount       | 100000.01              | exceeds the single payment limit of 100000.00
    amount       | ten euros              | must be a decimal number as a string
    currency     | JPY                    | is not supported
    currency     | eur                    | is not supported
    executionDate| 2020-01-01             | must not be in the past
    executionDate| 31-12-2026             | must be a date such as 2026-12-31
    """)
void invalidFieldsAreRejected(String field, String value, String expectedDetail) {
    Map<String, Object> payment = validPayment("10.00");
    payment.put(field, value);

    newPayment(api)
        .body(payment)
    .when()
        .post("/payments")
    .then()
        .statusCode(422)
        .body("errors", hasSize(1))                              // only the field we broke
        .body("errors[0].field", equalTo(field))
        .body("errors[0].detail", equalTo(expectedDetail));
}
```

- **The table** reads like a specification of the rules: field, bad value, expected message. Adding a rule is adding a row.
- **`delimiter = '|'`** lets values contain commas and spaces, and lines up the columns.
- **`quoteCharacter = '"'`** makes `""` an empty string. An unquoted empty value would arrive as `null`, which tests a different rule.
- **`hasSize(1)`** proves that the test broke only one field. Without it, a row could pass because of an error in another field.

The `name` pattern produces names such as `amount = '1.234' -> must have at most two decimals`. Two single quotes in the pattern print one.

### Step 2: Boundary Values

```java
@ParameterizedTest(name = "amount {0} is accepted")
@ValueSource(strings = {"0.01", "1", "99.9", "100000.00"})
void boundaryAmountsAreAccepted(String amount) {
    newPayment(api).body(validPayment(amount)).when().post("/payments").then().statusCode(201);
}
```

Rules have edges. `0.01` is the smallest amount and `100000.00` the largest; together with `0.00` and `100000.01` in the matrix, they pin the limits down from both sides. `1` and `99.9` prove that fewer than two decimals are fine.

### Step 3: Values From a Field

```java
static final List<String> VALID_IBANS = List.of(
        "NL91ABNA0417164300", "GB33BUKB20201555555555", "DE89370400440532013000", "BE68539007547034");

@ParameterizedTest(name = "creditor IBAN {0} is accepted")
@FieldSource("VALID_IBANS")
void ibansFromSeveralCountries(String iban) {
    Map<String, Object> payment = validPayment("10.00");
    payment.put("creditorIban", iban);
    newPayment(api).body(payment).when().post("/payments").then().statusCode(201);
}
```

IBANs differ in length per country, from 16 characters in Belgium to 22 in Germany and the UK. `@FieldSource` keeps the list in a named field that other tests can reuse.

### Step 4: Several Problems at Once

```java
static Stream<Arguments> severalProblemsAtOnce() {
    return Stream.of(
        Arguments.of(Map.of("currency", "JPY", "amount", "0"), List.of("currency", "amount")),
        Arguments.of(Map.of("debtorIban", "X", "creditorIban", "Y"), List.of("debtorIban", "creditorIban")),
        Arguments.of(Map.of("executionDate", LocalDate.now().minusDays(1).toString(), "creditorName", " "),
                List.of("executionDate", "creditorName")));
}

@ParameterizedTest(name = "{index}: {1} are reported together")
@MethodSource
void severalProblemsAtOnce(Map<String, Object> changes, List<String> expectedFields) {
    Map<String, Object> payment = validPayment("10.00");
    payment.putAll(changes);

    var errors = newPayment(api).body(payment)
        .when().post("/payments")
        .then().statusCode(422)
        .body("errors", hasSize(expectedFields.size()));
    for (String field : expectedFields) {
        errors.body("errors.field", hasItem(field));
    }
}
```

`@MethodSource` without a name uses the method with the same name as the test. A method can compute arguments, such as yesterday's date, which a CSV row can't. The test proves that the API reports every problem in one response, so a client can fix them all at once.

> **The code above, ready to run**
>
> All 25 tests in [`ValidationMatrixTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_01/ValidationMatrixTest.java) and the six in [`PerCurrencyTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_01/PerCurrencyTest.java) pass with `mvn test -pl module-09 -Dtest='ValidationMatrixTest,PerCurrencyTest'`. Break one expected message in the table and see which row fails, and what it logs.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 9 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/README.md)

## A Whole Class per Currency

When several tests share one dimension, parameterize the class instead of each method:

```java
@ParameterizedClass(name = "currency {0}")
@ValueSource(strings = {"EUR", "GBP", "USD"})
class PerCurrencyTest {

    @Parameter
    String currency;

    private Map<String, Object> payment() {
        Map<String, Object> payment = validPayment("250.00");
        payment.put("currency", currency);
        return payment;
    }

    @Test
    void aPaymentCanBeInitiated() {
        newPayment(api).body(payment()).when().post("/payments")
            .then().statusCode(201).body("currency", equalTo(currency));
    }

    @Test
    void theAmountIsKeptExactly() {
        newPayment(api).body(payment()).when().post("/payments")
            .then().body("amount", equalTo("250.00"));
    }
}
```

`@ParameterizedClass` runs every test method once per currency, with the value injected into the `@Parameter` field. Two methods and three currencies make six tests, grouped per currency in the report. A new currency is one more value; a new check is one more ordinary `@Test` method.

## Common Pitfalls

1. **Rows that break more than one thing.** A row with two problems can pass because of the wrong one. Check that exactly one error comes back.
2. **Empty and missing values.** In `@CsvSource`, an unquoted empty value is `null`, and a quoted one is `""`. Decide which rule the row tests.
3. **Unreadable test names.** Without a `name` pattern, a failure says `[7]`. Name each invocation by its inputs. Maven Surefire's default reports still use the index, so read failures in the IDE or JUnit's console output, or configure Surefire to use display names.
4. **Logging every request.** A table of 25 rows with `log().all()` buries the one that failed. Log only when validation fails.
5. **Only invalid rows.** A validator that rejects everything passes every negative row. Add the boundary values that must be accepted.

## Wrapping Up

Parameterized tests turn a set of similar API tests into one test and a table. `@CsvSource` with a text block makes validation rules readable as data, `@ValueSource` covers boundaries, `@FieldSource` reuses a named list, and `@MethodSource` computes arguments. `@ParameterizedClass` runs a whole class per value. Name every invocation by its inputs, check that each row breaks one thing only, and log only the exchanges that fail.
