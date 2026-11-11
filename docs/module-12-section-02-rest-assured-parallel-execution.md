# Running REST Assured Tests in Parallel: JUnit Parallel Execution Without Shared State

Running REST Assured Tests in Parallel: Run API tests concurrently with JUnit's parallel execution, configured in junit-platform.properties and opted into with @Execution(CONCURRENT), and follow the rules that make REST Assured safe in parallel: no static configuration, immutable specifications and no shared test data.

API tests spend most of their time waiting: for the network, for the server, for a database. A suite of a thousand requests at 200 milliseconds each takes over three minutes, almost all of it idle. Running tests in parallel turns that waiting into throughput. But parallel tests break in ways sequential tests never do: one test changes a static default while another reads it, two tests create the same customer, a counter is shared between threads. The tests pass alone and fail together, and not always.

This post follows [Section 2 of Module 12](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-12) of the REST Assured course. It runs eight API tests in parallel, and explains what makes REST Assured tests safe to run that way. The examples are in [`ParallelExecutionTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_02/ParallelExecutionTest.java) and [`junit-platform.properties`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/resources/junit-platform.properties).

## What is JUnit Parallel Execution?

JUnit Jupiter can run tests on several threads. Two settings decide what runs concurrently:

1. **Configuration parameters**, usually in `src/test/resources/junit-platform.properties`, turn parallel execution on and choose the default mode and the number of threads.
2. **`@Execution(..)`** on a class or method overrides the default: `CONCURRENT` runs its tests in parallel, `SAME_THREAD` keeps them sequential.

## The Configuration

```properties
# Parallel execution (Module 12, section 2). Enabled for this module only, and opt-in per class:
# everything runs in the same thread unless a class or method says @Execution(CONCURRENT).
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=same_thread
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

- **`enabled=true`** turns the feature on.
- **`mode.default=same_thread`** and **`mode.classes.default=same_thread`** keep every test sequential by default.
- **`strategy=fixed`** with **`parallelism=4`** uses up to four threads.

This is an **opt-in** set-up: only classes that say `@Execution(CONCURRENT)` run in parallel. It is the safe way to introduce parallelism into an existing suite, one class at a time, after checking that each class follows the rules below. Once a whole suite is safe, `mode.default=concurrent` turns it on everywhere.

## The API Under Test

`ReferenceDataApi` serves currencies and bank holidays, with a 200 ms delay on every request, so the effect of parallelism shows:

```json
[{"code":"EUR","name":"Euro","minorUnits":2},{"code":"GBP","name":"Pound sterling","minorUnits":2},
 {"code":"JPY","name":"Yen","minorUnits":0},{"code":"KWD","name":"Kuwaiti dinar","minorUnits":3}]
```

`GET /api/v1/holidays/NL` returns the Dutch bank holidays, such as King's Day (`2026-04-27`) and Liberation Day (`2026-05-05`).

## The Test Class

```java
@Execution(ExecutionMode.CONCURRENT)
class ParallelExecutionTest {

    private static ReferenceDataApi api;
    private static RequestSpecification referenceData;
    private static final Set<String> THREADS = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void startApi() {
        api = ReferenceDataApi.start();
        referenceData = new RequestSpecBuilder()
            .setBaseUri(api.baseUri()).setPort(api.port()).setBasePath("/api/v1")
            .build();
    }

    @AfterAll
    static void stopApiAndReport() {
        api.close();
        System.out.println("ParallelExecutionTest ran on " + THREADS.size() + " thread(s): " + THREADS);
    }

    private static RequestSpecification request() {
        THREADS.add(Thread.currentThread().getName());
        return given(referenceData);
    }
    ...
}
```

- **`@Execution(CONCURRENT)`** opts the class in.
- **One `RequestSpecification`**, built once in `@BeforeAll` and shared by every test. A built specification is immutable, so threads can share it safely, as [Module 5](module-05-section-01-rest-assured-request-specifications.md) explained.
- **`given(referenceData)`** creates a new request for each test, so no two threads ever change the same request.
- **`THREADS`** is a concurrent set that records which threads ran the tests. It is shared mutable state, so it must be a thread-safe collection.

## The Tests

```java
@Test
void allCurrencies() {
    request().when().get("/currencies").then().statusCode(200).body("$", hasSize(4));
}

@Test
void euro() {
    request().when().get("/currencies/{code}", "EUR").then().body("minorUnits", equalTo(2));
}

@Test
void yenHasNoMinorUnits() {
    request().when().get("/currencies/{code}", "JPY").then().body("minorUnits", equalTo(0));
}

@Test
void kingsDay() {
    request().when().get("/holidays/{country}", "NL").then().body("dates", hasItem("2026-04-27"));
}
...
```

Eight independent reads, each about 200 ms. None depends on another, and none changes anything.

## Measuring the Difference

Run the class as it is:

```text
ParallelExecutionTest ran on 3 thread(s): [ForkJoinPool-1-worker-4, ForkJoinPool-1-worker-2, ForkJoinPool-1-worker-3]
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.173 s -- in com.example.module_12.section_02.ParallelExecutionTest
```

Then turn parallel execution off for one run:

```bash
mvn test -pl module-12 -Dtest=ParallelExecutionTest -Djunit.jupiter.execution.parallel.enabled=false
```

```text
ParallelExecutionTest ran on 1 thread(s): [main]
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.168 s -- in com.example.module_12.section_02.ParallelExecutionTest
```

About twice as fast, and both times include starting the API. The thread pool used three of its four threads here; how many it uses depends on how the work is scheduled. For a large suite of slow requests, the gain grows with the number of threads, up to what the API under test can handle.

> **The code above, ready to run**
>
> All eight tests in [`ParallelExecutionTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/src/test/java/com/example/module_12/section_02/ParallelExecutionTest.java) pass with `mvn test -pl module-12 -Dtest=ParallelExecutionTest`. No Docker is needed. Remove `@Execution(CONCURRENT)` and compare the time Surefire reports.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 12 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-12/README.md)

## The Rules for Parallel REST Assured Tests

1. **No static configuration.** Never set `RestAssured.baseURI`, `port`, `filters`, `authentication`, `config` or `requestSpecification` in tests that run concurrently. One thread's setting becomes every thread's setting, and `RestAssured.reset()` in one test clears it for all. Build a specification instead, or inject one, as the JUnit extension in [Module 5](module-05-section-05-rest-assured-junit-extension.md) does.
2. **Immutable, shared specifications.** A built `RequestSpecification` can be shared between threads. The request each test builds from it with `given(spec)` is its own.
3. **No shared mutable test data.** Each test creates or reads its own data. Two tests that both create "customer 42", or both cancel the same payment, will collide. [Module 9](module-09-section-03-rest-assured-api-workflows.md) showed how independent tests each create what they need.
4. **An API that copes with concurrency.** The API under test must handle concurrent requests. The JDK server in this course uses virtual threads; a real API under test should be checked too.

REST Assured 6.2 fixed races in its static configuration, so static defaults are safer than they were. The safest static state is still none.

## Common Pitfalls

1. **Static REST Assured defaults.** `RestAssured.baseURI = ..` in one class changes it for every class running at the same time. Use specifications.
2. **Turning parallelism on everywhere at once.** Hidden shared state then fails at random. Opt in class by class, as this module does.
3. **Tests that depend on each other's data.** An ordered workflow, like the step-by-step style in Module 9, can't run in parallel. Make tests independent first.
4. **Exact counts over shared data.** A test that counts all payments sees the ones other threads are creating. Count what the test created.
5. **Ordinary collections shared between threads.** A `HashSet` or `ArrayList` shared by concurrent tests loses updates or throws. Use the concurrent collections.

## Wrapping Up

JUnit runs tests in parallel when `junit.jupiter.execution.parallel.enabled=true`, with the threads set in `junit-platform.properties`. An opt-in set-up keeps everything sequential by default and lets classes run concurrently with `@Execution(CONCURRENT)`. For API tests that mostly wait on the network, that saves real time: here, about half. It is safe when there is no static REST Assured configuration, specifications are built once and shared immutably, each test owns its data, and the API itself handles concurrent requests.
