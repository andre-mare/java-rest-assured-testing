# API Workflows With REST Assured: Ordered Steps Versus Independent Tests

API Workflows With REST Assured: Test a payment from initiation to execution, first as ordered steps that share one test instance, then in the preferred style of independent tests that each walk a payment to the state they test.

A payment has a lifecycle: it is initiated, authorised, executed, and finally executed or rejected. Each step is a separate request, and each step is only allowed in the right state. How do you test that? The natural first idea is a test per step, run in order, each continuing where the previous one stopped. It reads beautifully. It also has a cost that shows up the first time a step fails. This section shows both styles and explains why the second one is better.

This post follows [Section 3 of Module 9](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-09) of the REST Assured course. It tests the payment lifecycle as a story. The examples are in [`PaymentWorkflowTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_03/PaymentWorkflowTest.java).

## What is the Payment Lifecycle?

| Step | Request | Result |
| --- | --- | --- |
| 1. Initiate | `POST /api/v1/payments` | `201`, status `INITIATED` |
| 2. Authorise | `POST /api/v1/payments/{id}/authorisation` | `200`, status `AUTHORISED`, or `409` |
| 3. Execute | `POST /api/v1/payments/{id}/execution` | `202`, status `EXECUTING`, or `409` |
| 4. Settle | (about 0.5 s later, on the server) | `EXECUTED`, or `REJECTED` with `INSUFFICIENT_FUNDS` above 5000.00 |

Each payment keeps a `statusHistory`, the list of states it went through. A step in the wrong state gives `409 Conflict`:

```json
{"type":"about:blank","title":"Conflict","status":409,
 "detail":"Only an INITIATED payment can be authorised, it is EXECUTING","instance":"/api/v1/payments/pay-6/authorisation"}
```

The tests use `newPayment(api)`, `spec(api)` and `createPayment(..)` from the module's [`PaymentsClient`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/support/PaymentsClient.java), introduced in [Section 1](module-09-section-01-rest-assured-parameterized-api-tests.md).

## Style 1: Ordered Steps

```java
@Nested
@Order(1)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)                   // one instance, so fields survive between steps
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StepByStep {

    private String paymentId;

    @Test
    @Order(1)
    void initiate() {
        paymentId = newPayment(api).body(validPayment("120.00"))
            .when().post("/payments")
            .then().statusCode(201).body("status", equalTo("INITIATED"))
            .extract().path("paymentId");
    }

    @Test
    @Order(2)
    void authorise() {
        given(spec(api)).when().post("/payments/{id}/authorisation", paymentId)
            .then().statusCode(200).body("status", equalTo("AUTHORISED"));
    }

    @Test
    @Order(3)
    void execute() {
        given(spec(api)).when().post("/payments/{id}/execution", paymentId)
            .then().statusCode(202).body("status", equalTo("EXECUTING"));
    }

    @Test
    @Order(4)
    void settle() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            given(spec(api)).when().get("/payments/{id}", paymentId)
                .then().body("status", equalTo("EXECUTED")));

        given(spec(api)).when().get("/payments/{id}", paymentId)
            .then().body("statusHistory.status", contains("INITIATED", "AUTHORISED", "EXECUTING", "EXECUTED"));
    }
}
```

Three JUnit features make this work:

1. **`@TestInstance(PER_CLASS)`**: one instance for all four methods, so `paymentId` survives from step to step. By default, JUnit creates a new instance per test method.
2. **`@TestMethodOrder(OrderAnnotation.class)`** with `@Order(..)`: the steps run in a fixed order.
3. **`@TestClassOrder`** on the outer class runs this nested class before the other one.

The last step waits for the asynchronous settlement with Awaitility, the topic of [Section 5](module-09-section-05-rest-assured-asynchronous-apis.md), then checks the whole history with `contains(..)`, which proves the order of the states.

The report reads like the flow: initiate, authorise, execute, settle. That is the appeal. The costs:

- **A failed step fails the next ones too.** If `authorise` fails, `execute` and `settle` fail with errors that have nothing to do with their own step.
- **No step can run alone.** Run `settle` from your IDE, and `paymentId` is `null`.
- **The class can't run in parallel**, because its tests share state.

## Style 2: Independent Tests

```java
@Nested
@Order(2)
class IndependentTests {

    private String authorisedPayment(String amount) {
        String id = createPayment(api, amount);
        given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(200);
        return id;
    }

    @Test
    void aLargePaymentIsRejectedForInsufficientFunds() {
        String id = authorisedPayment("7500.00");
        given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(202);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            given(spec(api)).when().get("/payments/{id}", id)
                .then()
                .body("status", equalTo("REJECTED"))
                .body("rejectionReason", equalTo("INSUFFICIENT_FUNDS")));
    }

    @Test
    void aPaymentCanBeAuthorisedOnlyOnce() {
        String id = authorisedPayment("10.00");
        given(spec(api)).when().post("/payments/{id}/authorisation", id).then().statusCode(409);
    }

    @Test
    void aPaymentMustBeAuthorisedBeforeExecution() {
        String id = createPayment(api, "10.00");
        given(spec(api)).when().post("/payments/{id}/execution", id).then().statusCode(409);
    }
}
```

Each test creates its own payment and walks it to the state it needs, with small helpers such as `authorisedPayment(..)`. Then it tests one thing.

- **A failure points at one test.** If authorisation breaks, the helper fails in every test that uses it, with the same clear message, and the test names say what each one was about.
- **Each test runs alone**, from the IDE or the command line.
- **The tests can run in any order, and in parallel**, as Module 12 does.

The helpers keep the setup short, so each test still reads like a small story: an authorised payment of 7500.00, executed, is rejected for insufficient funds.

> **The code above, ready to run**
>
> All seven tests in [`PaymentWorkflowTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_03/PaymentWorkflowTest.java) pass with `mvn test -pl module-09 -Dtest=PaymentWorkflowTest`. Run only `StepByStep.settle()` from your IDE to see why ordered steps can't run alone.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 9 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/README.md)

## Testing the Rules Between Steps

The independent tests check more than the happy path: the transitions that must be refused.

| Test | Rule |
| --- | --- |
| `aPaymentCanBeAuthorisedOnlyOnce` | Authorising twice gives `409` |
| `aPaymentMustBeAuthorisedBeforeExecution` | Executing an initiated payment gives `409` |
| `aLargePaymentIsRejectedForInsufficientFunds` | An execution can end in `REJECTED`, with a reason |

A state machine has more wrong transitions than right ones. A suite that tests only the happy path leaves most of them untested.

## Common Pitfalls

1. **Ordered steps as the main style.** One broken step makes the whole chain red, and no step can be run on its own. Use independent tests, and keep at most one ordered story as documentation.
2. **Forgetting `PER_CLASS`.** With JUnit's default lifecycle, a field set in step 1 is gone in step 2, because each method gets a new instance.
3. **Sharing a payment between independent tests.** One test's authorisation changes another test's result. Create the payment in each test.
4. **Testing only the happy path.** Test the transitions that must fail with `409`, and the outcomes other than success.
5. **A fixed sleep for the last step.** Settlement is asynchronous. Wait for it with Awaitility, as Section 5 explains.

## Wrapping Up

A workflow can be tested as ordered steps that share one test instance, with `@TestInstance(PER_CLASS)` and `@TestMethodOrder`. It reads like the flow, but a failed step breaks the rest, and no step runs alone. Prefer independent tests: each creates its own payment, walks it to the state it needs with small helpers, and tests one thing, so failures point at one test and the tests can run in any order. Test the transitions that must be refused, not just the happy path.
