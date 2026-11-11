package com.example.module_11.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Mints RS256 JWT access tokens with the test signing key in {@code src/test/resources/jwt}. The service trusts the
 * matching public key in {@code src/main/resources/jwt}. In a real set-up the authorization server holds the private
 * key; Module 12 gets real tokens from Keycloak.
 */
public final class TestTokens {

    public static final String AUDIENCE = "accounts-api";

    private static final PrivateKey KEY = loadKey("/jwt/test-signing-key.pem");
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private TestTokens() {
    }

    public static String withScopes(String... scopes) {
        return token(List.of(scopes), AUDIENCE, Duration.ofMinutes(5));
    }

    public static String token(List<String> scopes, String audience, Duration lifetime) {
        Instant now = Instant.now();
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String claims = """
            {"iss":"https://auth.example.com","sub":"tpp-client","aud":"%s","scope":"%s","iat":%d,"exp":%d}"""
            .formatted(audience, String.join(" ", scopes), now.getEpochSecond(), now.plus(lifetime).getEpochSecond());
        String signingInput = encode(header) + "." + encode(claims);
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KEY);
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64URL.encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String json) {
        return BASE64URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static PrivateKey loadKey(String resource) {
        try (InputStream in = TestTokens.class.getResourceAsStream(resource)) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load " + resource, e);
        }
    }
}
