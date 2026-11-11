package com.example.module_07.section_05;

import static io.restassured.RestAssured.given;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.config.SSLConfig.sslConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.auth.SecureAccountsApi;

import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Module 7.5 – HTTPS with a certificate the JVM doesn't trust, and mutual TLS.
 * <p>
 * The server's certificate is self-signed, so the JVM's default trust store rejects it. The fix is to trust that
 * certificate, not to switch checking off:
 * <ul>
 *   <li>{@code relaxedHTTPSValidation()} trusts every certificate and every host name. It makes a misconfigured
 *       server or a man in the middle pass silently.</li>
 *   <li>{@code SSLConfig.trustStore(..)} trusts the certificates in a trust store and keeps the host name check.
 *       (The shortcut {@code given().trustStore(..)} also turns the host name check off, so prefer the config.)</li>
 *   <li>{@code SSLConfig.sslContext(..)} (REST Assured 6.3) takes a JDK {@link SSLContext} built however you like.</li>
 * </ul>
 * For mutual TLS the client also presents a certificate from a key store. Test key material (password
 * {@code changeit}): {@code tls/client.p12} with the client key ({@code CN=tpp-client}) and
 * {@code tls/client-truststore.p12} with the server certificate.
 */
class TlsAndMutualTlsTest {

    private static final String TRUST_STORE = "tls/client-truststore.p12";
    private static final String KEY_STORE = "tls/client.p12";
    private static final String PASSWORD = "changeit";

    private static SecureAccountsApi tls;
    private static SecureAccountsApi mutualTls;

    @BeforeAll
    static void startApis() {
        tls = SecureAccountsApi.startTls();
        mutualTls = SecureAccountsApi.startMutualTls();
    }

    @AfterAll
    static void stopApis() {
        tls.close();
        mutualTls.close();
    }

    private static RequestSpecification to(SecureAccountsApi api) {
        return given().baseUri(api.baseUri()).port(api.port());
    }

    @Nested
    class ServerAuthentication {

        @Test
        void anUntrustedCertificateFailsTheHandshake() {
            Exception exception = assertThrows(Exception.class, () -> to(tls).when().get("/api/v1/whoami"));
            assertCauseIs(SSLHandshakeException.class, exception);
        }

        @Test
        void relaxedValidationWorksButChecksNothing() {
            to(tls)
                .relaxedHTTPSValidation()                            // fine for a quick experiment, not for a suite
            .when()
                .get("/api/v1/whoami")
            .then()
                .statusCode(200);
        }

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
    }

    @Nested
    class MutualTls {

        @Test
        void withoutAClientCertificateTheServerRefuses() {
            Exception exception = assertThrows(Exception.class, () ->
                to(mutualTls)
                    .config(config().sslConfig(sslConfig().trustStore(TRUST_STORE, PASSWORD)))
                .when()
                    .get("/api/v1/whoami"));
            assertCauseIs(IOException.class, exception);             // a handshake failure or a reset connection
        }

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

        private static KeyStore load(String resource) throws GeneralSecurityException, IOException {
            try (InputStream in = TlsAndMutualTlsTest.class.getClassLoader().getResourceAsStream(resource)) {
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(in, PASSWORD.toCharArray());
                return store;
            }
        }
    }

    private static void assertCauseIs(Class<? extends Throwable> expected, Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (expected.isInstance(t)) {
                return;
            }
        }
        throw new AssertionError("Expected a " + expected.getSimpleName() + " in the causes of " + thrown, thrown);
    }
}
