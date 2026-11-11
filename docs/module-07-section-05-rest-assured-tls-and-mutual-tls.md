# TLS and Mutual TLS in REST Assured: Trust Stores, Key Stores and an SSLContext

TLS and Mutual TLS in REST Assured: Test an HTTPS API with a certificate the JVM doesn't trust, see why relaxedHTTPSValidation() is the wrong fix, trust the certificate with SSLConfig.trustStore(..), present a client certificate for mutual TLS, and pass a JDK SSLContext.

Test environments rarely have certificates that the JVM trusts. A local server, a staging API or a test container uses a self-signed certificate or one from an internal certificate authority, and the first HTTPS request fails with a handshake error. The quick fix, found in many test suites, is to turn certificate checking off. That makes the error go away, and with it every protection TLS gives. Open banking adds another layer: APIs for third-party providers often require **mutual TLS**, where the client presents a certificate too.

This post follows [Section 5 of Module 7](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-07) of the REST Assured course. It connects to an HTTPS server the right way, with a trust store, and then with a client certificate. The examples are in [`TlsAndMutualTlsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_05/TlsAndMutualTlsTest.java).

## What Do TLS and Mutual TLS Check?

In a TLS handshake:

1. **The server presents a certificate.** The client checks that it was signed by an authority it trusts, and that the host name matches. That proves the client is talking to the real server.
2. **In mutual TLS, the client presents a certificate too.** The server checks it against the authorities it trusts. That proves who the client is, before any HTTP is exchanged.

REST Assured configures both with `SSLConfig`:

| Setting | What it does |
| --- | --- |
| `trustStore(path, password)` | The certificates the client trusts, in a key store file |
| `keyStore(path, password)` | The client's own key and certificate, for mutual TLS |
| `sslContext(context)` | A JDK `SSLContext` built in any way you like, since REST Assured 6.3 |
| `relaxedHTTPSValidation()` | Trusts every certificate and every host name |

## The Servers Under Test

`SecureAccountsApi` serves `GET /api/v1/whoami` over HTTPS at `https://localhost`. It answers with the TLS protocol and the subject of the client certificate, if any:

```json
{"protocol":"TLSv1.3","clientCertificate":"none"}
```

The test starts two instances: one with plain TLS, and one that requires a client certificate. The test key material is in `src/test/resources/tls`, with the password `changeit`:

| File | Contains |
| --- | --- |
| `client-truststore.p12` | The server's certificate (`CN=localhost`), for the client to trust |
| `client.p12` | The client's key and certificate (`CN=tpp-client,O=Example TPP,C=NL`) |

## Trusting the Server

### Step 1: An Untrusted Certificate Fails the Handshake

```java
@Test
void anUntrustedCertificateFailsTheHandshake() {
    Exception exception = assertThrows(Exception.class, () -> to(tls).when().get("/api/v1/whoami"));
    assertCauseIs(SSLHandshakeException.class, exception);
}
```

The server's certificate is self-signed, so the JVM's default trust store doesn't know it:

```text
javax.net.ssl.SSLHandshakeException: (certificate_unknown) PKIX path building failed:
sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

This is the error that tempts people to turn checking off. `assertCauseIs(..)` walks the chain of causes, as in [Module 5](module-05-section-03-rest-assured-client-configuration.md).

### Step 2: Relaxed Validation Works, but Checks Nothing

```java
@Test
void relaxedValidationWorksButChecksNothing() {
    to(tls)
        .relaxedHTTPSValidation()                            // fine for a quick experiment, not for a suite
    .when()
        .get("/api/v1/whoami")
    .then()
        .statusCode(200);
}
```

`relaxedHTTPSValidation()` trusts every certificate and every host name. A misconfigured server, an expired certificate or a man in the middle all pass silently. It is fine for a quick experiment. In a committed suite it hides exactly the problems TLS exists to catch.

### Step 3: Trust the Server Certificate

```java
@Test
void trustTheServerCertificate() {
    to(tls)
        .config(config().sslConfig(sslConfig().trustStore(TRUST_STORE, PASSWORD)))
    .when()
        .get("/api/v1/whoami")
    .then()
        .statusCode(200)
        .body("protocol", startsWith("TLSv1."))
        .body("clientCertificate", equalTo("none"));
}
```

The client trusts exactly one certificate, the one in the trust store, and still checks the host name. That is the right fix: the test environment's certificate is trusted, and nothing else is.

REST Assured also has a shortcut, `given().trustStore(..)`. It loads the same trust store, but it also turns the host name check off. Prefer the `SSLConfig` form, which keeps it.

> **The code above, ready to run**
>
> All six tests in [`TlsAndMutualTlsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/src/test/java/com/example/module_07/section_05/TlsAndMutualTlsTest.java) pass with `mvn test -pl module-07 -Dtest=TlsAndMutualTlsTest`. The key material is valid until 2036.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 7 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-07/README.md)

## Mutual TLS

### Step 4: Without a Client Certificate the Server Refuses

```java
@Test
void withoutAClientCertificateTheServerRefuses() {
    Exception exception = assertThrows(Exception.class, () ->
        to(mutualTls)
            .config(config().sslConfig(sslConfig().trustStore(TRUST_STORE, PASSWORD)))
        .when()
            .get("/api/v1/whoami"));
    assertCauseIs(IOException.class, exception);             // a handshake failure or a reset connection
}
```

The client trusts the server, but has no certificate of its own. The server ends the handshake. Depending on the JDK and the timing, the client sees a handshake failure or a reset connection, so the test accepts any `IOException` in the causes. There is no HTTP status to check: the request never got that far.

### Step 5: Present a Client Certificate From a Key Store

```java
@Test
void presentAClientCertificateFromAKeyStore() {
    to(mutualTls)
        .config(config().sslConfig(sslConfig()
                .trustStore(TRUST_STORE, PASSWORD)
                .keyStore(KEY_STORE, PASSWORD)))
    .when()
        .get("/api/v1/whoami")
    .then()
        .statusCode(200)
        .body("clientCertificate", equalTo("CN=tpp-client,O=Example TPP,C=NL"));
}
```

The server reports the subject of the certificate the client presented, which proves the client certificate was really used. Since REST Assured 6.2, a key store is used for mutual TLS even when no trust store is set.

### Step 6: Or Give REST Assured a JDK SSLContext

```java
@Test
void orGiveRestAssuredAJdkSslContext() throws Exception {
    to(mutualTls)
        .config(config().sslConfig(sslConfig().sslContext(sslContext())))
    .when()
        .get("/api/v1/whoami")
    .then()
        .statusCode(200)
        .body("clientCertificate", startsWith("CN=tpp-client"));
}

private static SSLContext sslContext() throws GeneralSecurityException, IOException {
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(load(KEY_STORE), PASSWORD.toCharArray());
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(load(TRUST_STORE));
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
    return context;
}
```

`sslConfig().sslContext(..)`, new in REST Assured 6.3, takes a standard JDK `SSLContext`. Build it with the JDK's key and trust managers, as here, or get it from wherever your application already builds one, such as a PEM file loader or a hardware security module. REST Assured then uses it as it is.

## Common Pitfalls

1. **`relaxedHTTPSValidation()` in a committed suite.** It accepts any certificate, so the tests can't notice a broken or intercepted connection. Trust the test certificate with a trust store.
2. **The `given().trustStore(..)` shortcut.** It turns the host name check off. Use `config().sslConfig(sslConfig().trustStore(..))`.
3. **Expecting an HTTP status from a failed handshake.** A rejected client certificate fails before HTTP. Expect an exception, and look for its cause.
4. **Not checking which certificate was used.** A test that passes with or without the client certificate proves nothing. Check something the server learned from it, as the `whoami` endpoint allows.
5. **Committing real key material.** Test key stores like these belong in a repository only because they protect nothing. Real client keys belong in a secret store.

## Wrapping Up

A self-signed test certificate fails the TLS handshake. The right fix is to trust that one certificate with `sslConfig().trustStore(..)`, which keeps the host name check, not to trust everything with `relaxedHTTPSValidation()`. For mutual TLS, add the client's key and certificate with `keyStore(..)`, or pass a JDK `SSLContext` with `sslContext(..)` since REST Assured 6.3. A refused client certificate fails before HTTP, so test it with an exception, and test a successful one by checking which certificate the server saw.
