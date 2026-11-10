package com.example.accounts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import tools.jackson.databind.json.JsonMapper;

/**
 * A small, read-only REST API over {@link AccountRepository}, served by the JDK's built-in HTTP server.
 * <p>
 * It exists so that Module 1 has a real HTTP server to send requests to, without Spring, Docker or a network.
 * Start it with {@link #start()}, which picks a free port, and stop it with {@link #close()}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Request</th><th>Response</th></tr>
 *   <tr><td>{@code GET /api/health}</td><td>200, {@code {"status":"UP"}}</td></tr>
 *   <tr><td>{@code GET /api/v1/accounts}</td><td>200, {@link AccountList}. Optional query parameters
 *       {@code status} and {@code currency} filter the list; an unknown status gives 400</td></tr>
 *   <tr><td>{@code GET /api/v1/accounts/{id}}</td><td>200, {@link Account}, or 404 for an unknown id</td></tr>
 *   <tr><td>Any other method on these paths</td><td>405 with an {@code Allow: GET} header</td></tr>
 *   <tr><td>Any other path</td><td>404</td></tr>
 * </table>
 *
 * Every response has a {@code Cache-Control: no-store} header and a fresh {@code X-Request-Id}. A request's
 * {@code X-Correlation-Id} header is echoed back. Errors use RFC 9457 problem details
 * ({@code application/problem+json}).
 */
public final class AccountsApi implements AutoCloseable {

    public static final String BASE_PATH = "/api/v1";

    private static final String JSON = "application/json; charset=utf-8";
    private static final String PROBLEM_JSON = "application/problem+json; charset=utf-8";

    private final HttpServer server;
    private final AccountRepository repository = new AccountRepository();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private AccountsApi(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
    }

    /** Starts the API on a free port of the loopback interface. */
    public static AccountsApi start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            AccountsApi api = new AccountsApi(server);
            server.start();
            return api;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the Accounts API", e);
        }
    }

    /** The port the API listens on, chosen by the operating system at start. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** The scheme and host, without a port, for example {@code http://localhost}. */
    public String baseUri() {
        return "http://localhost";
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Request-Id", UUID.randomUUID().toString());
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            if (correlationId != null) {
                exchange.getResponseHeaders().set("X-Correlation-Id", correlationId);
            }

            String path = exchange.getRequestURI().getPath();
            if (!isKnownPath(path)) {
                sendProblem(exchange, ProblemDetail.notFound("No resource at " + path, path));
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendProblem(exchange, ProblemDetail.methodNotAllowed(
                        exchange.getRequestMethod() + " is not supported here", path));
                return;
            }
            route(exchange, path);
        }
    }

    private static boolean isKnownPath(String path) {
        return path.equals("/api/health")
                || path.equals(BASE_PATH + "/accounts")
                || (path.startsWith(BASE_PATH + "/accounts/") && path.length() > (BASE_PATH + "/accounts/").length());
    }

    private void route(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/health")) {
            sendJson(exchange, 200, Map.of("status", "UP"));
        } else if (path.equals(BASE_PATH + "/accounts")) {
            listAccounts(exchange, path);
        } else {
            String id = path.substring((BASE_PATH + "/accounts/").length());
            Optional<Account> account = repository.findById(id);
            if (account.isPresent()) {
                sendJson(exchange, 200, account.get());
            } else {
                sendProblem(exchange, ProblemDetail.notFound("Account " + id + " does not exist", path));
            }
        }
    }

    private void listAccounts(HttpExchange exchange, String path) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        Predicate<Account> filter = account -> true;

        String status = query.get("status");
        if (status != null) {
            AccountStatus wanted;
            try {
                wanted = AccountStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                sendProblem(exchange, ProblemDetail.badRequest(
                        "Unknown status '" + status + "'. Use ACTIVE, BLOCKED or CLOSED", path));
                return;
            }
            filter = filter.and(account -> account.status() == wanted);
        }
        String currency = query.get("currency");
        if (currency != null) {
            filter = filter.and(account -> account.currency().equalsIgnoreCase(currency));
        }
        sendJson(exchange, 200, AccountList.of(repository.findAll(filter)));
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            parameters.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, JSON, mapper.writeValueAsBytes(body));
    }

    private void sendProblem(HttpExchange exchange, ProblemDetail problem) throws IOException {
        send(exchange, problem.status(), PROBLEM_JSON, mapper.writeValueAsBytes(problem));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
