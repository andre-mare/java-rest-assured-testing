package com.example.auth;

import com.example.http.ApiResponse;
import com.example.http.ApiServer;
import com.sun.net.httpserver.HttpsExchange;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;

/**
 * An accounts API served over HTTPS with a self-signed certificate for {@code localhost}, optionally requiring a
 * client certificate (mutual TLS, as FAPI and PSD2 use with eIDAS QWACs).
 * <p>
 * Key material on the classpath (PKCS#12, password {@value #PASSWORD}, valid for ten years from 2026):
 * <ul>
 *   <li>{@code tls/server.p12} – the server's key and certificate ({@code CN=localhost})</li>
 *   <li>{@code tls/server-truststore.p12} – the client certificate the server trusts ({@code CN=tpp-client})</li>
 * </ul>
 * {@code GET /api/v1/whoami} answers with the TLS protocol and the subject of the client certificate, if any.
 */
public final class SecureAccountsApi implements AutoCloseable {

    public static final String PASSWORD = "changeit";

    private final ApiServer server = new ApiServer();

    private SecureAccountsApi() {
        server.get("/api/v1/whoami", request -> {
            Map<String, Object> body = new LinkedHashMap<>();
            if (request.exchange() instanceof HttpsExchange https) {
                body.put("protocol", https.getSSLSession().getProtocol());
                try {
                    X509Certificate client = (X509Certificate) https.getSSLSession().getPeerCertificates()[0];
                    body.put("clientCertificate", client.getSubjectX500Principal().getName());
                } catch (SSLPeerUnverifiedException e) {
                    body.put("clientCertificate", "none");
                }
            }
            return ApiResponse.ok(body);
        });
    }

    /** HTTPS without client authentication. */
    public static SecureAccountsApi startTls() {
        SecureAccountsApi api = new SecureAccountsApi();
        api.server.startHttps(sslContext(false), false);
        return api;
    }

    /** HTTPS that refuses a handshake without a trusted client certificate. */
    public static SecureAccountsApi startMutualTls() {
        SecureAccountsApi api = new SecureAccountsApi();
        api.server.startHttps(sslContext(true), true);
        return api;
    }

    public int port() {
        return server.port();
    }

    /** {@code https://localhost} */
    public String baseUri() {
        return server.baseUri();
    }

    @Override
    public void close() {
        server.close();
    }

    private static SSLContext sslContext(boolean trustClients) {
        try {
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(load("tls/server.p12"), PASSWORD.toCharArray());
            TrustManagerFactory trust = null;
            if (trustClients) {
                trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(load("tls/server-truststore.p12"));
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), trust == null ? null : trust.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not set up TLS", e);
        }
    }

    private static KeyStore load(String resource) throws GeneralSecurityException, IOException {
        try (InputStream in = SecureAccountsApi.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing " + resource);
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, PASSWORD.toCharArray());
            return store;
        }
    }
}
