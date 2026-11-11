# Signing Requests in REST Assured: HTTP Message Signatures With an OrderedFilter

Signing Requests in REST Assured: Sign every request with HTTP Message Signatures (RFC 9421) and Content-Digest (RFC 9530) in an OrderedFilter that runs last, and prove the server checks the signature with unsigned, tampered, wrongly signed and stale requests.

A bearer token says who you are. It doesn't say that the request wasn't changed on its way. For payments, that matters: an attacker who can change `100.00` into `100000.00` in transit doesn't need to steal anything else. That is why FAPI and several open banking standards require requests to be signed. The signature covers the method, the path, a digest of the body and other headers, so any change breaks it. Signing a request by hand in every test is out of the question. A filter can do it for all of them.

This post follows [Section 3 of Module 8](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-08) of the REST Assured course. It signs requests in a filter, and then proves that the server rejects every kind of bad signature. The examples are in [`RequestSigningTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_03/RequestSigningTest.java) and [`HttpMessageSignatureFilter`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/support/HttpMessageSignatureFilter.java).

## What is an HTTP Message Signature?

The signed payments API uses two standards:

1. **Content-Digest** ([RFC 9530](https://www.rfc-editor.org/rfc/rfc9530)): a hash of the body, sent as `Content-Digest: sha-256=:<base64>:`.
2. **HTTP Message Signatures** ([RFC 9421](https://www.rfc-editor.org/rfc/rfc9421)): a signature over chosen parts of the request.

A signed request carries three headers:

```text
Content-Digest:  sha-256=:<base64 of the SHA-256 of the body>:
Signature-Input: sig1=("@method" "@path" "content-digest" "x-correlation-id");created=1767225600;keyid="tpp-key-1"
Signature:       sig1=:<base64 HMAC-SHA256 of the signature base>:
```

- **`Signature-Input`** lists the covered components, when the signature was created, and which key signed it.
- **The signature base** is one line per component, such as `"@method": POST`, followed by a `"@signature-params"` line. Both sides build it the same way.
- **`Signature`** is the HMAC-SHA256 of the signature base with a secret the client and server share.

The body is covered through its digest, so changing the body breaks the digest, and changing the digest breaks the signature.

## The Endpoints Under Test

| Request | Rules |
| --- | --- |
| `POST /api/v1/payments` | `X-Correlation-Id`, signed, a correct `Content-Digest`, a signature less than five minutes old |
| `GET /api/v1/payments/{id}` | `X-Correlation-Id`, signed |

An unsigned request gets `401` with `WWW-Authenticate: Signature error="The request is not signed"`.

## The Signing Filter

The filter builds the three headers, then sends the request (shortened):

```java
public final class HttpMessageSignatureFilter implements OrderedFilter {

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 1;                                // leave room for a "tamper in transit" demo
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        List<String> components = new ArrayList<>(List.of("@method", "@path"));
        Object body = request.getBody();
        String digest = null;
        if (body != null) {
            ...
            digest = "sha-256=:" + Base64.getEncoder().encodeToString(sha256(text.getBytes(StandardCharsets.UTF_8))) + ":";
            request.header("Content-Digest", digest);
            components.add("content-digest");
        }
        components.add("x-correlation-id");

        String params = ... + ";created=" + clock.instant().getEpochSecond() + ";keyid=\"" + keyId + "\"";
        StringBuilder base = new StringBuilder();
        for (String component : components) {
            ...
            base.append('"').append(component).append("\": ").append(value).append('\n');
        }
        base.append("\"@signature-params\": ").append(params);

        request.header("Signature-Input", "sig1=" + params);
        request.header("Signature", "sig1=:" + hmac(base.toString()) + ":");
        return context.next(request, responseSpec);
    }
}
```

Three design decisions matter:

1. **It runs last.** As an `OrderedFilter` with an order near `LOWEST_PRECEDENCE`, it runs after every other filter, including the `CorrelationIdFilter` from [Section 1](module-08-section-01-rest-assured-writing-filters.md). A filter that changed the request after signing would break the signature.
2. **It is written from the RFCs**, independently of the server's code. If the test reused the server's signing code, a bug in it would be on both sides, and every test would pass.
3. **It takes a `Clock`.** The default is the system clock. A test can pass one set in the past, which makes the "too old" rule testable.

By the time filters run, REST Assured has already serialized an object body, so a `Map` or a record arrives in the filter as a `String` and is signed like any other. The filter refuses bodies it can't hash as text, such as a `byte[]` or a `File`.

## Signed Requests

### Step 1: A Signed POST Is Accepted

```java
private static HttpMessageSignatureFilter signer() {
    return new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET);
}

@Test
void aSignedPostIsAccepted() {
    api()
        .filter(signer())
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(201)
        .body("amount", equalTo("100.00"));
}
```

`api()` adds the correlation id filter. The test itself contains no signing code.

### Step 2: A Signed GET Is Accepted

```java
@Test
void aSignedGetIsAccepted() {
    String id = api().filter(signer()).contentType(ContentType.JSON).body(PAYMENT)
        .when().post("/api/v1/payments")
        .then().statusCode(201).extract().path("paymentId");

    api().filter(signer()).when().get("/api/v1/payments/{id}", id).then().statusCode(200);
}
```

A request without a body has no digest, so `content-digest` is left out of the covered components.

> **The code above, ready to run**
>
> All six tests in [`RequestSigningTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/test/java/com/example/module_08/section_03/RequestSigningTest.java) pass with `mvn test -pl module-08 -Dtest=RequestSigningTest`. [`HttpSignatures`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/src/main/java/com/example/signing/HttpSignatures.java) documents the scheme from the server's side.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 8 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-08/README.md)

## Proving the Server Checks

A server that accepts any signature makes signing worthless, and the positive tests above can't tell. These negative tests can.

### An Unsigned Request

```java
@Test
void anUnsignedRequestIsRejected() {
    api()
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", startsWith("Signature"));
}
```

### A Body Changed After Signing

```java
@Test
void aBodyChangedAfterSigningIsRejected() {
    api()
        .filter(signer())
        .filter(new TamperInTransit())                           // runs after the signer
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(401)
        .body("detail", equalTo("Content-Digest doesn't match the body"));
}

/** Changes the amount after the request was signed, as an attacker in the middle would. */
private static final class TamperInTransit implements OrderedFilter {
    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        request.body(request.<String>getBody().replace("100.00", "100000.00"));
        return context.next(request, responseSpec);
    }
}
```

This is why the signer's order is `LOWEST_PRECEDENCE - 1`: it leaves room for one filter after it. `TamperInTransit` plays the attacker, changing the amount after the signature was made. The server recomputes the digest and refuses.

### The Wrong Secret

```java
@Test
void aSignatureWithTheWrongSecretIsRejected() {
    api()
        .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, "not-the-secret"))
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(401)
        .body("detail", equalTo("The signature is invalid"));
}
```

The digest is correct, but the signature wasn't made with the client's secret.

### A Stale Signature

```java
@Test
void anOldSignatureIsRejected() {
    Clock tenMinutesAgo = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-10));

    api()
        .filter(new HttpMessageSignatureFilter(SignedPaymentsApi.TPP_KEY_ID, SignedPaymentsApi.TPP_SECRET, tenMinutesAgo))
        .contentType(ContentType.JSON)
        .body(PAYMENT)
    .when()
        .post("/api/v1/payments")
    .then()
        .statusCode(401)
        .body("detail", containsString("too old"));
}
```

A valid signature that is ten minutes old could be a recorded request being replayed. `Clock.offset(..)` moves the filter's clock back, without waiting or changing the system time.

## Common Pitfalls

1. **A signer that doesn't run last.** Any filter after it that changes the request breaks the signature. Make it an `OrderedFilter` with a very low priority.
2. **Logging before the signer.** `log()` and ordinary logging filters run before an `OrderedFilter` near `LOWEST_PRECEDENCE`, so the logged headers have no `Content-Digest`, `Signature-Input` or `Signature`. To see them, log in a filter ordered after the signer.
3. **Sharing signing code with the server.** A bug in it is then on both sides, and every test passes. Write the test's signer from the specification.
4. **Testing only signed requests.** A server that skips verification passes every positive test. Test unsigned, tampered, wrongly signed and stale requests.
5. **`Thread.sleep(..)` for time-based rules.** A five-minute rule would need a five-minute test. Inject a `Clock`.

## Wrapping Up

HTTP Message Signatures (RFC 9421) with a Content-Digest (RFC 9530) prove that a request wasn't changed on its way. A filter adds `Content-Digest`, `Signature-Input` and `Signature` to every request, and as an `OrderedFilter` near `LOWEST_PRECEDENCE` it runs last, after everything that could change the request. Write the signer from the RFCs, not from the server's code, and give it a `Clock`. Then prove the server checks: an unsigned request, a body changed after signing, the wrong secret and a stale signature must all be rejected.
