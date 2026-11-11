package com.example.auth;

import com.example.http.ApiException;
import com.example.http.ApiRequest;
import com.example.http.ApiResponse;
import com.example.http.ApiServer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module 7's protected bank: an OAuth 2.0 authorization server and the resource server it protects, in one process,
 * plus endpoints for Basic authentication, a form login with CSRF and a session, and a cross-host redirect.
 *
 * <h2>Authorization server</h2>
 * <ul>
 *   <li>{@code POST /oauth2/token} – the client credentials grant (RFC 6749 section 4.4). The client authenticates
 *       with HTTP Basic ({@code client_secret_basic}) or with {@code client_id}/{@code client_secret} form parameters
 *       ({@code client_secret_post}). Errors follow RFC 6749 section 5.2.</li>
 *   <li>{@code GET /.well-known/jwks.json} – the public signing key.</li>
 * </ul>
 * Clients: {@code tpp-accounts}/{@code accounts-secret} may get {@code accounts:read};
 * {@code tpp-payments}/{@code payments-secret} may get {@code accounts:read} and {@code payments:write}.
 *
 * <h2>Resource server (audience {@value #AUDIENCE})</h2>
 * <ul>
 *   <li>{@code GET /api/v1/accounts} needs {@code accounts:read}</li>
 *   <li>{@code POST /api/v1/payments} needs {@code payments:write}</li>
 * </ul>
 * Errors follow RFC 6750: 401 with {@code WWW-Authenticate: Bearer ...} for a missing or invalid token, 403 with
 * {@code error="insufficient_scope"} for a missing scope.
 *
 * <h2>Other endpoints</h2>
 * <ul>
 *   <li>{@code GET /admin/health} – Basic {@code admin}/{@code changeit}; 401 with a {@code Basic} challenge without it</li>
 *   <li>{@code GET /admin/report} – the same credentials, but 404 without them, as APIs that hide resources do</li>
 *   <li>{@code GET /portal/login} (HTML form with a CSRF token), {@code POST /portal/login},
 *       {@code GET /portal/home} (needs the {@code JSESSIONID} session cookie)</li>
 *   <li>{@code GET /api/v1/partner-redirect} – 302 to the partner server, on another port</li>
 * </ul>
 */
public final class BankAuthApi implements AutoCloseable {

    public static final String AUDIENCE = "accounts-api";

    private static final Map<String, String> CLIENT_SECRETS = Map.of(
            "tpp-accounts", "accounts-secret",
            "tpp-payments", "payments-secret");
    private static final Map<String, Set<String>> CLIENT_SCOPES = Map.of(
            "tpp-accounts", Set.of("accounts:read"),
            "tpp-payments", Set.of("accounts:read", "payments:write"));
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);

    private final ApiServer server = new ApiServer();
    private final ApiServer partner = new ApiServer();
    private final Map<String, String> csrfTokens = new ConcurrentHashMap<>();    // login page token -> unused
    private final Map<String, String> sessions = new ConcurrentHashMap<>();      // session id -> user
    private JwtService jwt;

    private BankAuthApi() {
        server.post("/oauth2/token", this::token)
              .get("/.well-known/jwks.json", r -> ApiResponse.ok(jwt.jwks()))
              .get("/api/v1/accounts", r -> {
                  Map<String, Object> claims = requireScope(r, "accounts:read");
                  return ApiResponse.ok(Map.of("client", claims.get("client_id"),
                          "accounts", List.of(Map.of("id", "acc-1001", "iban", "NL91ABNA0417164300"))));
              })
              .post("/api/v1/payments", r -> {
                  requireScope(r, "payments:write");
                  return ApiResponse.json(201, Map.of("paymentId", "pmt-" + UUID.randomUUID().toString().substring(0, 8)));
              })
              .get("/admin/health", r -> {
                  if (!isAdmin(r)) {
                      throw new ApiException(401, "Unauthorized", "Admin credentials are required")
                              .withHeader("WWW-Authenticate", "Basic realm=\"admin\"");
                  }
                  return ApiResponse.ok(Map.of("status", "UP"));
              })
              .get("/admin/report", r -> {
                  if (!isAdmin(r)) {
                      throw ApiException.notFound("No resource at /admin/report");      // hide that it exists
                  }
                  return ApiResponse.ok(Map.of("report", "daily", "payments", 42));
              })
              .get("/portal/login", this::loginPage)
              .post("/portal/login", this::login)
              .get("/portal/home", this::home)
              .get("/api/v1/partner-redirect", r -> ApiResponse.status(302)
                      .header("Location", partner.url() + "/partner/echo-credentials"));

        partner.get("/partner/echo-credentials", r -> ApiResponse.ok(Map.of(
                "authorizationReceived", r.header("Authorization") != null,
                "cookieReceived", r.header("Cookie") != null)));
    }

    public static BankAuthApi start() {
        BankAuthApi api = new BankAuthApi();
        api.server.start();
        api.partner.start();
        api.jwt = new JwtService(api.server.url(), api.server.mapper());
        return api;
    }

    public int port() {
        return server.port();
    }

    public String baseUri() {
        return server.baseUri();
    }

    /** The issuer ({@code iss}) of the tokens: the authorization server's URL. */
    public String issuer() {
        return jwt.issuer();
    }

    /**
     * Mints a token directly, bypassing the token endpoint. Tests use it to create tokens the endpoint would never
     * issue: expired ones, or ones for another audience.
     */
    public String mintToken(String clientId, List<String> scopes, String audience, Duration lifetime) {
        return jwt.issue(clientId, scopes, audience, lifetime);
    }

    @Override
    public void close() {
        server.close();
        partner.close();
    }

    // ------------------------------------------------------------------------------------------- token endpoint

    private ApiResponse token(ApiRequest request) {
        if (!request.contentType().startsWith("application/x-www-form-urlencoded")) {
            return oauthError(400, "invalid_request", "The token request must be a form post");
        }
        Optional<String[]> basic = basicCredentials(request);
        String clientId = basic.map(c -> c[0]).orElse(request.formParam("client_id"));
        String secret = basic.map(c -> c[1]).orElse(request.formParam("client_secret"));
        if (clientId == null || secret == null || !secret.equals(CLIENT_SECRETS.get(clientId))) {
            ApiResponse response = oauthError(401, "invalid_client", "Client authentication failed");
            return basic.isPresent() ? response.header("WWW-Authenticate", "Basic realm=\"token\"") : response;
        }
        if (!"client_credentials".equals(request.formParam("grant_type"))) {
            return oauthError(400, "unsupported_grant_type", "Only client_credentials is supported");
        }
        Set<String> allowed = CLIENT_SCOPES.get(clientId);
        String scopeParam = request.formParam("scope");
        List<String> scopes = scopeParam == null || scopeParam.isBlank()
                ? allowed.stream().sorted().toList()
                : Arrays.asList(scopeParam.trim().split("\\s+"));
        if (!allowed.containsAll(scopes)) {
            return oauthError(400, "invalid_scope", "The client may not request " + scopes);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", jwt.issue(clientId, scopes, AUDIENCE, TOKEN_LIFETIME));
        body.put("token_type", "Bearer");
        body.put("expires_in", TOKEN_LIFETIME.toSeconds());
        body.put("scope", String.join(" ", scopes));
        return ApiResponse.ok(body).header("Cache-Control", "no-store");      // RFC 6749 section 5.1
    }

    private static ApiResponse oauthError(int status, String error, String description) {
        return ApiResponse.json(status, Map.of("error", error, "error_description", description))
                .header("Cache-Control", "no-store");
    }

    // ------------------------------------------------------------------------------------------- resource server

    private Map<String, Object> requireScope(ApiRequest request, String scope) {
        String realm = "Bearer realm=\"" + AUDIENCE + "\"";
        String authorization = request.header("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(401, "Unauthorized", "A bearer token is required").withHeader("WWW-Authenticate", realm);
        }
        Map<String, Object> claims;
        try {
            claims = jwt.verify(authorization.substring(7).trim(), AUDIENCE);
        } catch (JwtService.InvalidTokenException e) {
            throw new ApiException(401, "Unauthorized", e.getMessage()).withHeader("WWW-Authenticate",
                    realm + ", error=\"invalid_token\", error_description=\"" + e.getMessage() + "\"");
        }
        List<String> granted = Arrays.asList(String.valueOf(claims.get("scope")).split(" "));
        if (!granted.contains(scope)) {
            throw new ApiException(403, "Forbidden", "The token lacks the scope " + scope).withHeader("WWW-Authenticate",
                    realm + ", error=\"insufficient_scope\", scope=\"" + scope + "\"");
        }
        return claims;
    }

    // ------------------------------------------------------------------------------------------- Basic

    private static Optional<String[]> basicCredentials(ApiRequest request) {
        String authorization = request.header("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        return colon < 0 ? Optional.empty() : Optional.of(new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)});
    }

    private static boolean isAdmin(ApiRequest request) {
        return basicCredentials(request).map(c -> c[0].equals("admin") && c[1].equals("changeit")).orElse(false);
    }

    // ------------------------------------------------------------------------------------------- form login

    private ApiResponse loginPage(ApiRequest request) {
        String csrf = UUID.randomUUID().toString();
        csrfTokens.put(csrf, "issued");
        String html = """
                <html>
                  <head><title>Sign in</title></head>
                  <body>
                    <form id="login" method="post" action="/portal/login">
                      <input type="hidden" name="_csrf" value="%s"/>
                      <input type="text" name="username"/>
                      <input type="password" name="password"/>
                      <input type="submit" value="Sign in"/>
                    </form>
                  </body>
                </html>
                """.formatted(csrf);
        return ApiResponse.text(200, "text/html; charset=utf-8", html);
    }

    private ApiResponse login(ApiRequest request) {
        String csrf = request.formParam("_csrf");
        if (csrf == null || csrfTokens.remove(csrf) == null) {
            throw new ApiException(403, "Forbidden", "Missing or reused CSRF token");
        }
        if (!"anna".equals(request.formParam("username")) || !"correct horse".equals(request.formParam("password"))) {
            throw new ApiException(401, "Unauthorized", "Wrong username or password");
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, "anna");
        return ApiResponse.status(302)
                .header("Location", "/portal/home")
                .header("Set-Cookie", "JSESSIONID=" + sessionId + "; Path=/portal; HttpOnly; SameSite=Lax");
    }

    private ApiResponse home(ApiRequest request) {
        String user = sessions.get(request.cookies().getOrDefault("JSESSIONID", ""));
        if (user == null) {
            return ApiResponse.status(302).header("Location", "/portal/login");
        }
        return ApiResponse.ok(Map.of("user", user, "message", "Welcome back"));
    }
}
