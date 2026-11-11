package com.example.auth;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

/**
 * Issues and verifies JWT access tokens (RFC 9068 style) signed with RS256, using only the JDK.
 * The RSA key pair is created at start-up and never leaves the process; the public part is published as a JWKS.
 */
public final class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final KeyPair keyPair;
    private final String keyId = "key-" + UUID.randomUUID().toString().substring(0, 8);
    private final String issuer;
    private final JsonMapper mapper;

    JwtService(String issuer, JsonMapper mapper) {
        this.issuer = issuer;
        this.mapper = mapper;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String issuer() {
        return issuer;
    }

    /** A signed token for {@code clientId} with the given scopes and audience, valid for {@code lifetime}. */
    public String issue(String clientId, List<String> scopes, String audience, Duration lifetime) {
        Instant now = Instant.now();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "at+jwt");
        header.put("kid", keyId);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", clientId);
        claims.put("client_id", clientId);
        claims.put("aud", audience);
        claims.put("scope", String.join(" ", scopes));
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(lifetime).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        String signingInput = encode(header) + "." + encode(claims);
        return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    }

    /**
     * Verifies the signature, expiry, issuer and audience, and returns the claims.
     *
     * @throws InvalidTokenException with a reason suitable for an {@code error_description}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token, String expectedAudience) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("The token is not a JWS");
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(DECODER.decode(parts[2]))) {
                throw new InvalidTokenException("The signature is invalid");
            }
            Map<String, Object> claims = mapper.readValue(DECODER.decode(parts[1]), Map.class);
            if (((Number) claims.get("exp")).longValue() <= Instant.now().getEpochSecond()) {
                throw new InvalidTokenException("The token has expired");
            }
            if (!issuer.equals(claims.get("iss"))) {
                throw new InvalidTokenException("The token was issued by someone else");
            }
            if (!expectedAudience.equals(claims.get("aud"))) {
                throw new InvalidTokenException("The token is meant for another audience");
            }
            return claims;
        } catch (InvalidTokenException e) {
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new InvalidTokenException("The token can't be read");
        }
    }

    /** The public key as a JSON Web Key Set (RFC 7517). */
    public Map<String, Object> jwks() {
        RSAPublicKey key = (RSAPublicKey) keyPair.getPublic();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA", "use", "sig", "alg", "RS256", "kid", keyId,
                "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent()))));
    }

    private byte[] sign(String input) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(input.getBytes(StandardCharsets.US_ASCII));
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(Map<String, Object> json) {
        return ENCODER.encodeToString(mapper.writeValueAsBytes(json));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return ENCODER.encodeToString(bytes);
    }

    /** A token that must be rejected, with the reason. */
    public static final class InvalidTokenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidTokenException(String reason) {
            super(reason);
        }
    }
}
