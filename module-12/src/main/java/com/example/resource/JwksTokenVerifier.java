package com.example.resource;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies RS256 JWT access tokens against the keys an authorization server publishes as a JWKS (RFC 7517), the way
 * a resource server does with Keycloak: signature by key id ({@code kid}), issuer, audience and expiry.
 * Unknown key ids trigger one refresh of the key set, so key rotation works.
 */
public final class JwksTokenVerifier {

    private static final Base64.Decoder BASE64URL = Base64.getUrlDecoder();

    private final String issuer;
    private final URI jwksUri;
    private final String audience;
    private final JsonMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();

    public JwksTokenVerifier(String issuer, String jwksUri, String audience, JsonMapper mapper) {
        this.issuer = issuer;
        this.jwksUri = URI.create(jwksUri);
        this.audience = audience;
        this.mapper = mapper;
    }

    /** Thrown for a token that must be rejected; the message is safe to send to the client. */
    public static final class InvalidTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidTokenException(String reason) {
            super(reason);
        }
    }

    /** Verifies the token and returns its claims. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("The token is not a JWS");
        }
        Map<String, Object> header;
        Map<String, Object> claims;
        try {
            header = mapper.readValue(BASE64URL.decode(parts[0]), Map.class);
            claims = mapper.readValue(BASE64URL.decode(parts[1]), Map.class);
        } catch (RuntimeException e) {
            throw new InvalidTokenException("The token can't be read");
        }
        if (!"RS256".equals(header.get("alg"))) {
            throw new InvalidTokenException("Only RS256 tokens are accepted");
        }
        PublicKey key = key(String.valueOf(header.get("kid")));
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(BASE64URL.decode(parts[2]))) {
                throw new InvalidTokenException("The signature is invalid");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new InvalidTokenException("The signature is invalid");
        }
        if (!(claims.get("exp") instanceof Number exp) || exp.longValue() <= Instant.now().getEpochSecond()) {
            throw new InvalidTokenException("The token has expired");
        }
        if (!issuer.equals(claims.get("iss"))) {
            throw new InvalidTokenException("The token was issued by " + claims.get("iss"));
        }
        Object aud = claims.get("aud");
        boolean audienceMatches = aud instanceof List<?> list ? list.contains(audience) : audience.equals(aud);
        if (!audienceMatches) {
            throw new InvalidTokenException("The token is not meant for " + audience);
        }
        return claims;
    }

    private PublicKey key(String kid) {
        PublicKey key = keys.get(kid);
        if (key == null) {
            refresh();
            key = keys.get(kid);
        }
        if (key == null) {
            throw new InvalidTokenException("Unknown signing key " + kid);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private synchronized void refresh() {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(jwksUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> jwks = mapper.readValue(response.body(), Map.class);
            for (Object entry : (List<Object>) jwks.get("keys")) {
                Map<String, Object> jwk = (Map<String, Object>) entry;
                if ("RSA".equals(jwk.get("kty")) && !"enc".equals(jwk.get("use"))) {
                    BigInteger n = new BigInteger(1, BASE64URL.decode((String) jwk.get("n")));
                    BigInteger e = new BigInteger(1, BASE64URL.decode((String) jwk.get("e")));
                    keys.put((String) jwk.get("kid"), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load the JWKS from " + jwksUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
