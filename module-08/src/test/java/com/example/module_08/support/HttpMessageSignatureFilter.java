package com.example.module_08.support;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs requests with HTTP Message Signatures (RFC 9421, algorithm {@code hmac-sha256}) and adds a
 * {@code Content-Digest} (RFC 9530) for requests with a body.
 * <p>
 * It is written from the RFCs, independently of the server's code, so a mistake in one is caught by the other. It is
 * an {@link OrderedFilter} with a very low priority, so it runs after every other filter, just before the request is
 * sent: a filter that changed the request after signing would break the signature.
 */
public final class HttpMessageSignatureFilter implements OrderedFilter {

    private final String keyId;
    private final String secret;
    private final Clock clock;

    public HttpMessageSignatureFilter(String keyId, String secret) {
        this(keyId, secret, Clock.systemUTC());
    }

    public HttpMessageSignatureFilter(String keyId, String secret, Clock clock) {
        this.keyId = keyId;
        this.secret = secret;
        this.clock = clock;
    }

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
            if (!(body instanceof String text)) {
                throw new IllegalStateException("This filter signs String bodies only, not " + body.getClass());
            }
            digest = "sha-256=:" + Base64.getEncoder().encodeToString(sha256(text.getBytes(StandardCharsets.UTF_8))) + ":";
            request.header("Content-Digest", digest);
            components.add("content-digest");
        }
        components.add("x-correlation-id");

        String params = components.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(" ", "(", ")"))
                + ";created=" + clock.instant().getEpochSecond() + ";keyid=\"" + keyId + "\"";

        StringBuilder base = new StringBuilder();
        for (String component : components) {
            String value = switch (component) {
                case "@method" -> request.getMethod().toUpperCase(Locale.ROOT);
                case "@path" -> URI.create(request.getURI()).getRawPath();
                case "content-digest" -> digest;
                default -> request.getHeaders().getValue(component);
            };
            base.append('"').append(component).append("\": ").append(value).append('\n');
        }
        base.append("\"@signature-params\": ").append(params);

        request.header("Signature-Input", "sig1=" + params);
        request.header("Signature", "sig1=:" + hmac(base.toString()) + ":");
        return context.next(request, responseSpec);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
