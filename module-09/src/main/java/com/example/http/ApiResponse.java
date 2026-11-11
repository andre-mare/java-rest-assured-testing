package com.example.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What a route handler answers: a status, headers and an optional body. */
public final class ApiResponse {

    public static final String JSON = "application/json; charset=utf-8";
    public static final String PROBLEM_JSON = "application/problem+json; charset=utf-8";

    private final int status;
    private final Object body;              // serialized with Jackson, unless it is a byte[] or a String
    private String contentType;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    private ApiResponse(int status, Object body, String contentType) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    public static ApiResponse json(int status, Object body) {
        return new ApiResponse(status, body, JSON);
    }

    public static ApiResponse ok(Object body) {
        return json(200, body);
    }

    public static ApiResponse created(String location, Object body) {
        return json(201, body).header("Location", location);
    }

    public static ApiResponse noContent() {
        return new ApiResponse(204, null, null);
    }

    public static ApiResponse status(int status) {
        return new ApiResponse(status, null, null);
    }

    public static ApiResponse text(int status, String contentType, String body) {
        return new ApiResponse(status, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    public ApiResponse header(String name, String value) {
        headers.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
        return this;
    }

    public ApiResponse contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public int status() {
        return status;
    }

    public Object body() {
        return body;
    }

    public String contentType() {
        return contentType;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }
}
