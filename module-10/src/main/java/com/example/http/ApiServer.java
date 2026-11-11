package com.example.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import tools.jackson.databind.json.JsonMapper;

/**
 * A tiny REST framework on the JDK's built-in HTTP server, so the APIs under test need no Spring and no container.
 * <p>
 * Routes use path templates such as {@code /api/v1/payments/{id}}. A handler returns an {@link ApiResponse} or throws
 * an {@link ApiException}, which becomes an RFC 9457 problem. The server also answers:
 * <ul>
 *   <li>404 for an unknown path, and 405 with an {@code Allow} header for a known path with another method</li>
 *   <li>{@code HEAD} for every {@code GET} route (same status and headers, no body)</li>
 *   <li>{@code OPTIONS} for every known path (204 with {@code Allow})</li>
 * </ul>
 * Before-filters run in order before routing and can answer instead of the route by returning a response, which is how
 * the APIs check authentication. After-hooks can add headers to every response.
 * <p>
 * This file is the same in every module of the course that uses it; the modules share no code.
 */
public final class ApiServer implements AutoCloseable {

    /** Answers a request. */
    @FunctionalInterface
    public interface Handler {
        ApiResponse handle(ApiRequest request) throws Exception;
    }

    /** Runs before routing. Returning a response answers the request; returning empty lets it through. */
    @FunctionalInterface
    public interface Filter {
        Optional<ApiResponse> apply(ApiRequest request) throws Exception;
    }

    private record Route(String method, Pattern pattern, List<String> names, Handler handler) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");

    private final List<Route> routes = new ArrayList<>();
    private final List<Filter> filters = new ArrayList<>();
    private final List<BiConsumer<ApiRequest, ApiResponse>> afterHooks = new ArrayList<>();
    private final JsonMapper mapper;
    private HttpServer server;
    private ExecutorService executor;
    private String scheme = "http";

    public ApiServer() {
        this(JsonMapper.builder().build());
    }

    public ApiServer(JsonMapper mapper) {
        this.mapper = mapper;
    }

    // ----------------------------------------------------------------------------------- configuration

    public ApiServer get(String template, Handler handler) {
        return route("GET", template, handler);
    }

    public ApiServer post(String template, Handler handler) {
        return route("POST", template, handler);
    }

    public ApiServer put(String template, Handler handler) {
        return route("PUT", template, handler);
    }

    public ApiServer patch(String template, Handler handler) {
        return route("PATCH", template, handler);
    }

    public ApiServer delete(String template, Handler handler) {
        return route("DELETE", template, handler);
    }

    /** Adds a route for any method, including custom ones such as {@code QUERY}. */
    public ApiServer route(String method, String template, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder regex = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(last, matcher.start()))).append("([^/]+)");
            names.add(matcher.group(1));
            last = matcher.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
        return this;
    }

    public ApiServer before(Filter filter) {
        filters.add(filter);
        return this;
    }

    public ApiServer after(BiConsumer<ApiRequest, ApiResponse> hook) {
        afterHooks.add(hook);
        return this;
    }

    // ----------------------------------------------------------------------------------- lifecycle

    /** Starts plain HTTP on a free port of the loopback interface. */
    public ApiServer start() {
        try {
            return start(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the API", e);
        }
    }

    /**
     * Starts HTTPS on a free port. With {@code needClientAuth}, the TLS handshake fails unless the client presents a
     * certificate that the {@code sslContext}'s trust store trusts (mutual TLS).
     */
    public ApiServer startHttps(SSLContext sslContext, boolean needClientAuth) {
        try {
            HttpsServer https = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLParameters parameters = sslContext.getDefaultSSLParameters();
                    parameters.setNeedClientAuth(needClientAuth);
                    params.setSSLParameters(parameters);
                }
            });
            scheme = "https";
            return start(https);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the API", e);
        }
    }

    private ApiServer start(HttpServer httpServer) {
        server = httpServer;
        executor = Executors.newVirtualThreadPerTaskExecutor();   // requests may run in parallel
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        return this;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** The scheme and host without a port, such as {@code http://localhost}. */
    public String baseUri() {
        return scheme + "://localhost";
    }

    /** The full root URL, such as {@code http://localhost:54321}. */
    public String url() {
        return baseUri() + ":" + port();
    }

    public JsonMapper mapper() {
        return mapper;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            executor.close();
        }
    }

    // ----------------------------------------------------------------------------------- request handling

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            ApiRequest request = new ApiRequest(exchange, Map.of(), body, mapper);
            ApiResponse response;
            try {
                response = applyFilters(request).orElseGet(() -> route(exchange, method, path, body));
            } catch (ApiException e) {
                response = problem(e, path);
            } catch (Exception e) {
                response = problem(new ApiException(500, "Internal Server Error", e.toString()), path);
            }
            for (BiConsumer<ApiRequest, ApiResponse> hook : afterHooks) {
                hook.accept(request, response);
            }
            send(exchange, response, "HEAD".equals(method));
        }
    }

    private Optional<ApiResponse> applyFilters(ApiRequest request) throws Exception {
        for (Filter filter : filters) {
            Optional<ApiResponse> answer = filter.apply(request);
            if (answer.isPresent()) {
                return answer;
            }
        }
        return Optional.empty();
    }

    private ApiResponse route(HttpExchange exchange, String method, String path, byte[] body) {
        String lookup = "HEAD".equals(method) ? "GET" : method;
        Set<String> allowed = new LinkedHashSet<>();
        for (Route route : routes) {
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            allowed.add(route.method());
            if (route.method().equals(lookup)) {
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < route.names().size(); i++) {
                    params.put(route.names().get(i), URLDecoder.decode(matcher.group(i + 1), StandardCharsets.UTF_8));
                }
                try {
                    return route.handler().handle(new ApiRequest(exchange, params, body, mapper));
                } catch (ApiException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ApiException(500, "Internal Server Error", e.toString());
                }
            }
        }
        if (allowed.isEmpty()) {
            throw ApiException.notFound("No resource at " + path);
        }
        if (allowed.contains("GET")) {
            allowed.add("HEAD");
        }
        allowed.add("OPTIONS");
        String allow = String.join(", ", allowed);
        if ("OPTIONS".equals(method)) {
            return ApiResponse.noContent().header("Allow", allow);
        }
        throw new ApiException(405, "Method Not Allowed", method + " is not supported here").withHeader("Allow", allow);
    }

    private ApiResponse problem(ApiException e, String path) {
        ApiResponse response = ApiResponse.json(e.status(),
                        new ProblemDetail("about:blank", e.title(), e.status(), e.getMessage(), path,
                                e.errors().isEmpty() ? null : e.errors()))
                .contentType(ApiResponse.PROBLEM_JSON);
        e.headers().forEach(response::header);
        return response;
    }

    private void send(HttpExchange exchange, ApiResponse response, boolean head) throws IOException {
        byte[] bytes = switch (response.body()) {
            case null -> new byte[0];
            case byte[] raw -> raw;
            case String text -> text.getBytes(StandardCharsets.UTF_8);
            default -> mapper.writeValueAsBytes(response.body());
        };
        response.headers().forEach((name, values) -> exchange.getResponseHeaders().put(name, new ArrayList<>(values)));
        if (response.contentType() != null && bytes.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
        }
        boolean noBody = head || bytes.length == 0 || response.status() == 204 || response.status() == 304;
        exchange.sendResponseHeaders(response.status(), noBody ? -1 : bytes.length);
        if (!noBody) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // ----------------------------------------------------------------------------------- helpers

    /** Parses {@code a=1&b=2&a=3} into {@code {a=[1, 3], b=[2]}}. A name without {@code =} gets an empty value. */
    public static Map<String, List<String>> parseUrlEncoded(String raw) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return parameters;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            parameters.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
        }
        return parameters;
    }
}
