package com.example.signing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The bits of HTTP Message Signatures (RFC 9421) and Content-Digest (RFC 9530) that the Signed Payments API uses,
 * shared by the server and, as documentation, by the tests' signing filter.
 * <p>
 * A signed request carries three headers:
 * <pre>
 *   Content-Digest:  sha-256=:&lt;base64 of the SHA-256 of the body&gt;:
 *   Signature-Input: sig1=("@method" "@path" "content-digest" "x-correlation-id");created=1767225600;keyid="tpp-key-1"
 *   Signature:       sig1=:&lt;base64 HMAC-SHA256 of the signature base&gt;:
 * </pre>
 * The signature base has one line per covered component, {@code "name": value}, and ends with the
 * {@code "@signature-params"} line. A request without a body leaves {@code content-digest} out.
 */
public final class HttpSignatures {

    private HttpSignatures() {
    }

    /** {@code sha-256=:<base64>:} for the given body. */
    public static String contentDigest(byte[] body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
            return "sha-256=:" + Base64.getEncoder().encodeToString(hash) + ":";
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The value of {@code @signature-params}: the covered components and the parameters. */
    public static String signatureParams(List<String> components, long created, String keyId) {
        return components.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(" ", "(", ")"))
                + ";created=" + created + ";keyid=\"" + keyId + "\"";
    }

    /** The signature base: one line per component (values from {@code values}), then {@code @signature-params}. */
    public static String signatureBase(List<String> components, Map<String, String> values, String signatureParams) {
        StringBuilder base = new StringBuilder();
        for (String component : components) {
            base.append('"').append(component).append("\": ").append(values.get(component)).append('\n');
        }
        return base.append("\"@signature-params\": ").append(signatureParams).toString();
    }

    /** HMAC-SHA256 of the signature base with the shared secret, base64 encoded. */
    public static String hmac(String signatureBase, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
