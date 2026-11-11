package com.example.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** A request as a route handler sees it. */
public final class ApiRequest {

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> query;
    private final byte[] body;
    private final JsonMapper mapper;

    ApiRequest(HttpExchange exchange, Map<String, String> pathParams, byte[] body, JsonMapper mapper) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.query = ApiServer.parseUrlEncoded(exchange.getRequestURI().getRawQuery());
        this.body = body;
        this.mapper = mapper;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    /** The raw query string, or {@code null}. */
    public String rawQuery() {
        return exchange.getRequestURI().getRawQuery();
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /** The first value of a query parameter, or {@code null}. */
    public String query(String name) {
        List<String> values = query.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public Map<String, List<String>> queryParams() {
        return query;
    }

    /** The first value of a header, or {@code null}. Header names are case-insensitive. */
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public Headers headers() {
        return exchange.getRequestHeaders();
    }

    public String contentType() {
        String contentType = header("Content-Type");
        return contentType == null ? "" : contentType;
    }

    /** The cookies of the {@code Cookie} header, in the order they were sent. */
    public Map<String, String> cookies() {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String pair : header.split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                } else if (!pair.isBlank()) {
                    cookies.put(pair.trim(), "");
                }
            }
        }
        return cookies;
    }

    public byte[] body() {
        return body;
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** The form parameters of an {@code application/x-www-form-urlencoded} body. */
    public Map<String, List<String>> formParams() {
        return ApiServer.parseUrlEncoded(bodyAsString());
    }

    /** The first value of a form parameter, or {@code null}. */
    public String formParam(String name) {
        List<String> values = formParams().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** The JSON body as a map. Throws a 400 problem when the body is missing or isn't a JSON object. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> jsonObject() {
        requireJson();
        try {
            Object value = mapper.readValue(body, Object.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (JacksonException e) {
            throw ApiException.badRequest("The body is not valid JSON");
        }
        throw ApiException.badRequest("The body must be a JSON object");
    }

    /** The JSON body as a list. Throws a 400 problem when the body isn't a JSON array. */
    public List<Object> jsonArray() {
        requireJson();
        try {
            Object value = mapper.readValue(body, Object.class);
            if (value instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        } catch (JacksonException e) {
            throw ApiException.badRequest("The body is not valid JSON");
        }
        throw ApiException.badRequest("The body must be a JSON array");
    }

    private void requireJson() {
        String type = contentType().toLowerCase();
        if (!(type.startsWith("application/json") || type.contains("+json"))) {
            throw new ApiException(415, "Unsupported Media Type",
                    "Expected a JSON body, got Content-Type '" + contentType() + "'");
        }
        if (body.length == 0) {
            throw ApiException.badRequest("The request has no body");
        }
    }

    public HttpExchange exchange() {
        return exchange;
    }
}
