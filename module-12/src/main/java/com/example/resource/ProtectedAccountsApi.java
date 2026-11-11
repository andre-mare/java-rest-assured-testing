package com.example.resource;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module 12's resource server: accounts and payments protected by access tokens from a real authorization server
 * (Keycloak in the tests), verified with the server's published keys.
 * <ul>
 *   <li>{@code GET /api/v1/accounts} needs the scope {@code accounts:read}</li>
 *   <li>{@code POST /api/v1/payments} needs {@code payments:write}</li>
 * </ul>
 * Errors follow RFC 6750, as in Module 7.
 */
public final class ProtectedAccountsApi implements AutoCloseable {

    public static final String AUDIENCE = "accounts-api";

    private final ApiServer server = new ApiServer();
    private final JwksTokenVerifier verifier;

    private ProtectedAccountsApi(String issuer, String jwksUri) {
        verifier = new JwksTokenVerifier(issuer, jwksUri, AUDIENCE, server.mapper());
        server.get("/api/v1/accounts", request -> {
                  Map<String, Object> claims = authorize(request, "accounts:read");
                  return ApiResponse.ok(Map.of(
                          "client", String.valueOf(claims.getOrDefault("azp", claims.get("client_id"))),
                          "accounts", List.of(Map.of("id", "acc-1001", "iban", "NL91ABNA0417164300"))));
              })
              .post("/api/v1/payments", request -> {
                  authorize(request, "payments:write");
                  return ApiResponse.json(201, Map.of("paymentId", "pmt-" + UUID.randomUUID().toString().substring(0, 8)));
              });
    }

    /**
     * Starts the API, trusting tokens from {@code issuer} signed with the keys at {@code jwksUri}. For Keycloak,
     * {@code issuer} is {@code <server>/realms/<realm>} and {@code jwksUri} is
     * {@code <issuer>/protocol/openid-connect/certs}.
     */
    public static ProtectedAccountsApi start(String issuer, String jwksUri) {
        ProtectedAccountsApi api = new ProtectedAccountsApi(issuer, jwksUri);
        api.server.start();
        return api;
    }

    public int port() {
        return server.port();
    }

    public String baseUri() {
        return server.baseUri();
    }

    @Override
    public void close() {
        server.close();
    }

    private Map<String, Object> authorize(ApiRequest request, String scope) {
        String realm = "Bearer realm=\"" + AUDIENCE + "\"";
        String authorization = request.header("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Unauthorized", "A bearer token is required").withHeader("WWW-Authenticate", realm);
        }
        Map<String, Object> claims;
        try {
            claims = verifier.verify(authorization.substring(7));
        } catch (JwksTokenVerifier.InvalidTokenException e) {
            throw new ApiException(401, "Unauthorized", e.getMessage()).withHeader("WWW-Authenticate",
                    realm + ", error=\"invalid_token\", error_description=\"" + e.getMessage() + "\"");
        }
        List<String> scopes = Arrays.asList(String.valueOf(claims.getOrDefault("scope", "")).split(" "));
        if (!scopes.contains(scope)) {
            throw new ApiException(403, "Forbidden", "The token lacks the scope " + scope).withHeader("WWW-Authenticate",
                    realm + ", error=\"insufficient_scope\", scope=\"" + scope + "\"");
        }
        return claims;
    }
}
