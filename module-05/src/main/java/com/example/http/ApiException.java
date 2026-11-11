package com.example.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thrown by a route handler to answer with an RFC 9457 problem. {@link ApiServer} turns it into the response.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String title;
    private final transient List<Map<String, String>> errors;
    private final transient Map<String, String> headers = new LinkedHashMap<>();

    public ApiException(int status, String title, String detail) {
        this(status, title, detail, List.of());
    }

    public ApiException(int status, String title, String detail, List<Map<String, String>> errors) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errors = List.copyOf(errors);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, "Bad Request", detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(404, "Not Found", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(409, "Conflict", detail);
    }

    /** 422 with one entry per invalid field: {@code {"field": .., "detail": ..}}. */
    public static ApiException validation(List<Map<String, String>> errors) {
        return new ApiException(422, "Unprocessable Content", "The request has invalid fields", errors);
    }

    /** Adds a header to the error response, for example {@code WWW-Authenticate}. */
    public ApiException withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public List<Map<String, String>> errors() {
        return errors;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
