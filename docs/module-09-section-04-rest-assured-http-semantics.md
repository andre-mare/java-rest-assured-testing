# HTTP Semantics in REST Assured: Idempotency Keys, ETags and Link Pagination

HTTP Semantics in REST Assured: Test the HTTP rules payment APIs depend on, covering idempotent replay with an Idempotency-Key, conditional reads with If-None-Match and 304, conditional writes with If-Match, 428 and 412, and following Link headers through every page.

A client sends a payment and the connection drops before the answer arrives. Did the payment go through? If the client sends it again, will the money move twice? Two clients edit the same payment at the same time. Whose change wins, and does the other one get lost without anyone noticing? A list of payments is too long for one response. How does a client get all of them, exactly once? HTTP has standard answers to each of these questions, and each one is easy to get subtly wrong.

This post follows [Section 4 of Module 9](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-09) of the REST Assured course. It tests idempotency keys, ETags and cursor pagination. The examples are in [`HttpSemanticsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_04/HttpSemanticsTest.java).

## What are These Semantics?

| Problem | Mechanism | Standard |
| --- | --- | --- |
| A retried `POST` must not pay twice | `Idempotency-Key` header | IETF draft "The Idempotency-Key HTTP Header Field" |
| Don't send an unchanged resource again | `ETag` and `If-None-Match`, answered with `304 Not Modified` | [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110#section-13), section 13 |
| Don't overwrite someone else's change | `If-Match`, answered with `428` or `412` | RFC 9110, and [RFC 6585](https://www.rfc-editor.org/rfc/rfc6585) for `428` |
| Get a long list page by page | `Link: <..>; rel="next"` | [RFC 8288](https://www.rfc-editor.org/rfc/rfc8288) |

## The API Under Test

The payment lifecycle API from [Section 1](module-09-section-01-rest-assured-parameterized-api-tests.md):

| Request | Behaviour |
| --- | --- |
| `POST /payments` with `Idempotency-Key` | `201`. The same key and body again: the same payment, with `Idempotent-Replayed: true`. The same key with another body: `422`. No key: `400` |
| `GET /payments/{id}` | `200` with an `ETag` such as `"pay-6-v2"`. A matching `If-None-Match`: `304` |
| `PATCH /payments/{id}` | Needs `If-Match`: `428 Precondition Required` without it, `412 Precondition Failed` when it is stale |
| `GET /payments?limit=..&cursor=..` | `items`, with `Link: </api/v1/payments?limit=2&cursor=pay-3>; rel="next"` while there is more |

The nested classes in the test group the three topics.

## Idempotency Keys

### Step 1: The Same Request Twice Creates One Payment

```java
@Test
void theSameRequestTwiceCreatesOnePayment() {
    String key = UUID.randomUUID().toString();
    Map<String, Object> body = validPayment("75.00");

    String first = given(spec(api)).header("Idempotency-Key", key).body(body)
        .when().post("/payments")
        .then().statusCode(201).header("Idempotent-Replayed", nullValue())
        .extract().path("paymentId");

    given(spec(api)).header("Idempotency-Key", key).body(body)
        .when().post("/payments")
        .then()
        .statusCode(201)
        .header("Idempotent-Replayed", "true")
        .body("paymentId", equalTo(first));                  // the same payment, not a second one
}
```

The second request simulates a client retrying after a lost answer. It gets the original answer back, marked as a replay, and no second payment is created. That is what makes retries safe.

### Step 2: Reusing a Key for Another Request Is an Error

```java
@Test
void reusingAKeyForAnotherRequestIsAnError() {
    String key = UUID.randomUUID().toString();
    given(spec(api)).header("Idempotency-Key", key).body(validPayment("75.00"))
        .when().post("/payments").then().statusCode(201);

    given(spec(api)).header("Idempotency-Key", key).body(validPayment("76.00"))
        .when().post("/payments").then().statusCode(422);
}
```

A key identifies one request. A client that sends another body with the same key has a bug, and silently replaying the first answer would hide it. The server refuses.

### Step 3: The Key Is Required

```java
@Test
void theKeyIsRequired() {
    given(spec(api)).body(validPayment("75.00")).when().post("/payments").then().statusCode(400);
}
```

The detail is `The header Idempotency-Key is required`. For a payment API, a `POST` without a key is a request that can't be retried safely, so it isn't accepted at all.

## ETags

### Step 4: An Unchanged Resource Is Not Sent Again

```java
@Test
void anUnchangedResourceIsNotSentAgain() {
    String id = createPayment(api, "20.00");
    String etag = given(spec(api)).when().get("/payments/{id}", id)
        .then().statusCode(200).extract().header("ETag");

    given(spec(api))
        .header("If-None-Match", etag)
    .when()
        .get("/payments/{id}", id)
    .then()
        .statusCode(304)                                      // Not Modified
        .body(emptyString());
}
```

The `ETag` is a version of the resource. A client that already has that version asks with `If-None-Match`, and gets `304` with no body. That saves bandwidth, and it is how clients cache safely.

### Step 5: An Update Needs If-Match

```java
@Test
void anUpdateNeedsIfMatch() {
    String id = createPayment(api, "20.00");
    given(spec(api)).contentType("application/merge-patch+json").body(Map.of("reference", "Rent"))
        .when().patch("/payments/{id}", id)
        .then().statusCode(428);                              // Precondition Required
}
```

The detail is `Send If-Match with the ETag you last read`. Requiring `If-Match` forces every client to say which version it is changing.

### Step 6: A Stale ETag Loses the Race

```java
@Test
void aStaleETagLosesTheRace() {
    String id = createPayment(api, "20.00");
    String etag = given(spec(api)).when().get("/payments/{id}", id).then().extract().header("ETag");

    String newEtag = given(spec(api))                         // the first writer wins ...
        .header("If-Match", etag)
        .contentType("application/merge-patch+json").body(Map.of("reference", "First"))
        .when().patch("/payments/{id}", id)
        .then().statusCode(200).header("ETag", not(equalTo(etag)))
        .extract().header("ETag");

    given(spec(api))                                          // ... the second, with the old ETag, gets 412
        .header("If-Match", etag)
        .contentType("application/merge-patch+json").body(Map.of("reference", "Second"))
        .when().patch("/payments/{id}", id)
        .then().statusCode(412);

    given(spec(api)).when().get("/payments/{id}", id)
        .then().header("ETag", newEtag).body("reference", equalTo("First"));
}
```

Two clients read the same version. The first one's change succeeds and creates a new `ETag`. The second one, still holding the old `ETag`, gets `412 Precondition Failed`. The last request proves the important part: the winner's change was kept. Without `If-Match`, the second write would have silently overwritten the first, the classic lost update.

> **The code above, ready to run**
>
> All eight tests in [`HttpSemanticsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/src/test/java/com/example/module_09/section_04/HttpSemanticsTest.java) pass with `mvn test -pl module-09 -Dtest=HttpSemanticsTest`. Add `.log().headers()` after `then()` to see the `ETag`, `Idempotent-Replayed` and `Link` headers.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 9 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-09/README.md)

## Pagination With Link Headers

### Step 7: Follow next Links Until the Last Page

```java
private static final Pattern NEXT = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

@Test
void followNextLinksUntilTheLastPage() {
    List<String> ids = new ArrayList<>();
    String next = PaymentLifecycleApi.BASE_PATH + "/payments?limit=2";
    int pages = 0;
    while (next != null) {
        Response page = given(spec(api)).basePath("").when().get(next).then().statusCode(200).extract().response();
        ids.addAll(page.path("items.paymentId"));
        next = nextLink(page.header("Link"));
        pages++;
    }

    assertThat(ids).startsWith("pay-1", "pay-2", "pay-3", "pay-4", "pay-5").doesNotHaveDuplicates();
    assertThat(pages).isEqualTo((ids.size() + 1) / 2);
}
```

A page with more after it has a header such as:

```text
Link: </api/v1/payments?limit=2&cursor=pay-3>; rel="next"
```

- **The loop follows the link**, instead of building `cursor=..` itself. The cursor format is the server's business.
- **`basePath("")`** clears the specification's base path, because the link is already an absolute path, as with the `Location` header in [Module 4](module-04-section-05-rest-assured-chaining-requests.md).
- **The assertions** check that the first payments come in order, that no payment appears twice, and that the number of pages matches. Other tests in the class create payments too, so the test checks the start of the list rather than its exact length.

### Step 8: The Last Page Has No next Link

```java
@Test
void theLastPageHasNoNextLink() {
    given(spec(api))
        .queryParam("limit", 100)
    .when()
        .get("/payments")
    .then()
        .header("Link", nullValue());
}
```

A missing `Link` header is how the client knows to stop. A server that always sends one sends clients into an endless loop.

## Common Pitfalls

1. **A new idempotency key for a retry.** A retry must reuse the original key; a new key is a new payment. Generate the key once per logical request.
2. **Testing idempotency with one request.** Only a second request with the same key proves anything. Check that it returns the same payment, not a new one.
3. **Not checking the winner after a `412`.** A server could reject the second write and still lose the first. Read the resource and check the first change is there.
4. **Building page URLs yourself.** The cursor format can change. Follow `Link: rel="next"`, and stop when there is none.
5. **An absolute link with the base path still set.** The request goes to `/api/v1/api/v1/payments..`. Clear it with `basePath("")`.

## Wrapping Up

Payment APIs depend on HTTP semantics that are easy to get wrong, and each needs a test. An `Idempotency-Key` makes a retried `POST` safe: the same key and body replay the same payment, another body is refused. ETags make reads cacheable with `If-None-Match` and `304`, and writes safe with `If-Match`, `428` and `412`, with the winner's change kept. `Link: rel="next"` headers lead through every page; follow them, check for duplicates, and stop when there is none.
