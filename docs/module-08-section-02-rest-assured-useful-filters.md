# Useful Filters for REST Assured: Timing, Masked Logs and Repairing Responses

Useful Filters for REST Assured: Write filters worth having in a real suite, covering timing every exchange, a log with IBANs and names masked, the built-in RequestLoggingFilter and ResponseLoggingFilter, and repairing a response with ResponseBuilder.

[Section 1](module-08-section-01-rest-assured-writing-filters.md) showed how filters work. This section shows what they are good for. Some problems can't be solved inside a single test: how long does each request take across the whole suite? How do you log payment requests without putting customers' names and IBANs in the CI log? And what do you do with a legacy endpoint that sends JSON but calls it plain text? Each of these is a filter of a few lines.

This post follows [Section 2 of Module 8](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-08) of the REST Assured course. It times requests, masks personal data in logs, uses REST Assured's own logging filters, and repairs a response. The examples are in [`UsefulFiltersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_02/UsefulFiltersTest.java) and [`MaskingLogFilter`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/support/MaskingLogFilter.java).

## What Makes a Filter Useful?

A filter is worth writing when something must happen for many requests and doesn't belong in any one test:

1. **Measuring**: timing, counting, recording what was sent.
2. **Logging**: with control over what is written, and where.
3. **Repairing**: handing REST Assured a corrected response from an API you can't change.

## The Data Under Test

The tests post a payment to the signed payments API:

```json
{"debtorName": "Anna de Vries", "debtorIban": "NL91ABNA0417164300",
 "creditorName": "Jan Jansen", "creditorIban": "NL69INGB0123456789", "amount": "42.00"}
```

`POST /api/v1/payments` must be signed. The helper adds the signing filter from [Section 3](module-08-section-03-rest-assured-signing-requests.md) and the correlation id filter from Section 1:

```java
private static RequestSpecification signedApi() {
    return given().baseUri(api.baseUri()).port(api.port())
        .filter(new CorrelationIdFilter())
        .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET));
}
```

The legacy endpoint, `GET /api/v1/legacy/fx-rates`, returns exchange rates as JSON, but with the content type `text/plain`.

## Timing Every Exchange

```java
@Test
void timeEveryExchange() {
    List<Duration> timings = new ArrayList<>();
    Filter timer = (request, responseSpec, context) -> {
        long start = System.nanoTime();
        Response response = context.next(request, responseSpec);
        timings.add(Duration.ofNanos(System.nanoTime() - start));
        return response;
    };

    signedApi().filter(timer).contentType(ContentType.JSON).body(PAYMENT).when().post("/api/v1/payments")
        .then().statusCode(201);

    assertEquals(1, timings.size());
    assertTrue(timings.getFirst().compareTo(Duration.ofSeconds(5)) < 0);
}
```

The timer measures around `context.next(..)`, so it covers the later filters and the network. In a real suite, the timings could go to a report that shows the slowest endpoints. Use generous limits, as [Module 1](module-01-section-04-first-rest-assured-assertions.md) advised for `time(..)`.

## Logging Without Personal Data

[Module 5](module-05-section-04-rest-assured-secrets-in-logs.md) masked headers with `blacklistHeader(..)`. That doesn't help with a body full of names and IBANs, which are personal data under the GDPR. A filter can mask them:

```java
public final class MaskingLogFilter implements Filter {

    private static final Pattern IBAN = Pattern.compile("\\b([A-Z]{2}[0-9]{2})([A-Z0-9]{7,26})([A-Z0-9]{4})\\b");
    private static final Pattern NAME_FIELD = Pattern.compile("(\"[A-Za-z]*[Nn]ame\"\\s*:\\s*\")([^\"]*)(\")");

    private final PrintStream out;

    public MaskingLogFilter(PrintStream out) {
        this.out = out;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Object body = request.getBody();
        out.println("> " + request.getMethod() + " " + request.getURI() + (body == null ? "" : " " + mask(body.toString())));
        Response response = context.next(request, responseSpec);
        out.println("< " + response.statusCode() + " " + mask(response.asString()));
        return response;
    }

    static String mask(String text) {
        Matcher iban = IBAN.matcher(text);
        String masked = iban.replaceAll(m -> m.group(1) + "*".repeat(m.group(2).length()) + m.group(3));
        return NAME_FIELD.matcher(masked).replaceAll("$1***$3");
    }
}
```

- **IBANs** keep their country code, check digits and last four characters, so a reader can still tell accounts apart.
- **Name fields**, any key ending in `name` or `Name`, have their values replaced with `***`.
- **Everything else** stays readable.

The log of one payment looks like this:

```text
> POST http://localhost:60682/api/v1/payments {"debtorName": "***", "debtorIban": "NL91**********4300", "creditorName": "***", "creditorIban": "NL69**********6789", "amount": "42.00"}
< 201 {"debtorName":"***","debtorIban":"NL91**********4300","creditorName":"***","creditorIban":"NL69**********6789","amount":"42.00","paymentId":"pmt-1","status":"RCVD","createdAt":"2026-11-11T06:43:03.323042Z"}
```

The test proves it:

```java
@Test
void logWithPersonalDataMasked() {
    ByteArrayOutputStream log = new ByteArrayOutputStream();

    signedApi()
        .filter(new MaskingLogFilter(new PrintStream(log, true, StandardCharsets.UTF_8)))
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(201);

    String logged = log.toString(StandardCharsets.UTF_8);
    assertTrue(logged.contains("NL91**********4300"), logged);
    assertFalse(logged.contains("NL91ABNA0417164300"), logged);
    assertFalse(logged.contains("Anna de Vries"), logged);
    assertTrue(logged.contains("\"amount\""), logged);           // everything else stays readable
}
```

The masking is regular-expression based, which is enough for a log. It is a safety net, not a guarantee: a name in a free-text field such as a remittance description would still get through.

> **The code above, ready to run**
>
> All four tests in [`UsefulFiltersTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_02/UsefulFiltersTest.java) pass with `mvn test -pl module-08 -Dtest=UsefulFiltersTest`. Replace the `ByteArrayOutputStream` with `System.out` to see the masked log in the console.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 8 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/README.md)

## The Built-In Logging Filters

```java
@Test
void theBuiltInLoggingFiltersWriteWhereYouWant() {
    ByteArrayOutputStream log = new ByteArrayOutputStream();

    given().baseUri(api.baseUri()).port(api.port())
        .filter(new RequestLoggingFilter(LogDetail.METHOD, new PrintStream(log, true, StandardCharsets.UTF_8)))
    .when()
        .get("/api/v1/legacy/fx-rates");

    assertTrue(log.toString(StandardCharsets.UTF_8).contains("GET"));
}
```

`RequestLoggingFilter` and `ResponseLoggingFilter` are what `log()` uses. As filters, they can take a `LogDetail` and a `PrintStream`, and they can go in a specification, so every request is logged in the same way to the same place.

## Repairing a Response

The legacy endpoint sends JSON with the content type `text/plain`, so REST Assured won't read it with a body path. A filter can hand REST Assured a corrected copy of the response:

```java
@Test
void repairAResponseWithResponseBuilder() {
    Filter fixContentType = (request, responseSpec, context) -> {
        Response response = context.next(request, responseSpec);
        return new ResponseBuilder().clone(response).setContentType(ContentType.JSON).build();
    };

    given().baseUri(api.baseUri()).port(api.port())
        .filter(fixContentType)
    .when()
        .get("/api/v1/legacy/fx-rates")
    .then()
        .contentType(ContentType.JSON)
        .body("base", equalTo("EUR"))
        .body("rates.GBP", equalTo(0.85f));
}
```

`ResponseBuilder().clone(response)` copies the status, headers and body, and `setContentType(..)` changes one thing. The filter returns the copy, and every check after it sees `application/json`.

This hides the API's real content type from the test, so a check on it no longer tells the truth. For this particular problem, [Section 5](module-08-section-05-rest-assured-parsers-and-failure-listeners.md) shows a lighter fix that leaves the response alone: a parser.

## Common Pitfalls

1. **Masking only headers.** Personal data lives in bodies too. `blacklistHeader(..)` doesn't touch them.
2. **Logging the request before it is final.** A logging filter that runs before a signer or another filter that changes the request logs what was not sent. Mind the order.
3. **Trusting regex masking completely.** Free-text fields can hold names, addresses and account numbers in any format. Keep logs short-lived as well.
4. **Repairing responses you should report.** A filter that fixes a content type hides a real bug in the API. Repair only what you can't get fixed, and say so in the code.
5. **Tight timing limits.** A filter that fails on a slow request makes the suite flaky on busy agents. Record timings, and alert on trends rather than single values.

## Wrapping Up

Filters make suite-wide behaviour a few lines of code. A timing filter measures around `context.next(..)`. A masking log filter writes each exchange with IBANs and names hidden, which `blacklistHeader(..)` can't do for bodies. `RequestLoggingFilter` and `ResponseLoggingFilter` are the filters behind `log()`, and can be placed in a specification with their own detail and stream. `ResponseBuilder` can repair a response, but hides the original, so prefer a parser where one fits.
