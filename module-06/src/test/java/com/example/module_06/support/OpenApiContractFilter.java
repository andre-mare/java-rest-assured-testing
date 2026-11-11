package com.example.module_06.support;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;
import static org.hamcrest.MatcherAssert.assertThat;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * A REST Assured {@link Filter} that checks every response against an OpenAPI 3.1 description:
 * <ol>
 *   <li>the request's path and method must be an operation in the description,</li>
 *   <li>the response status must be documented for that operation (or a {@code default} response must exist),</li>
 *   <li>the response content type must be documented, and the body must match its schema.</li>
 * </ol>
 * OpenAPI 3.1 schemas are JSON Schema 2020-12, so the body is checked with REST Assured's
 * {@code matchesJsonSchema}. References such as {@code #/components/schemas/Party} keep working because the whole
 * {@code components} object is copied into the schema that is validated.
 * <p>
 * This is a teaching version: it handles the parts of OpenAPI this module uses. For real projects, the Atlassian
 * library {@code com.atlassian.oai:openapi-request-validator-restassured} (formerly
 * {@code swagger-request-validator-restassured}) offers a complete filter of this kind. Filters are covered in depth in
 * Module 8.
 */
public final class OpenApiContractFilter implements Filter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Map<String, Object> document;
    private final String serverPath;

    private OpenApiContractFilter(Map<String, Object> document) {
        this.document = document;
        this.serverPath = (String) map(list(document.get("servers")).getFirst()).get("url");
    }

    /** Loads an OpenAPI description in JSON from the classpath. */
    public static OpenApiContractFilter fromClasspath(String path) {
        try (InputStream in = OpenApiContractFilter.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("No OpenAPI description at " + path);
            }
            return new OpenApiContractFilter(map(MAPPER.readValue(in.readAllBytes(), Map.class)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Response response = context.next(request, responseSpec);

        String method = request.getMethod().toLowerCase(Locale.ROOT);
        String path = URI.create(request.getURI()).getPath();
        if (!path.startsWith(serverPath)) {
            throw new AssertionError(path + " is outside the server URL " + serverPath + " of the OpenAPI description");
        }
        String apiPath = path.substring(serverPath.length());
        String template = findTemplate(apiPath);
        Map<String, Object> operation = map(map(map(document.get("paths")).get(template)).get(method));
        if (operation.isEmpty()) {
            throw new AssertionError(method.toUpperCase(Locale.ROOT) + " " + template + " is not in the OpenAPI description");
        }

        Map<String, Object> responses = map(operation.get("responses"));
        Object documented = responses.getOrDefault(String.valueOf(response.statusCode()), responses.get("default"));
        if (documented == null) {
            throw new AssertionError("Status " + response.statusCode() + " is not documented for "
                    + method.toUpperCase(Locale.ROOT) + " " + template + "; documented: " + responses.keySet());
        }
        Map<String, Object> responseObject = resolve(map(documented));
        Map<String, Object> content = map(responseObject.get("content"));
        if (content.isEmpty()) {
            return response;                                            // nothing documented about the body
        }

        String contentType = response.contentType().split(";")[0].trim();
        Map<String, Object> media = map(content.get(contentType));
        if (media.isEmpty()) {
            throw new AssertionError("Content type " + contentType + " is not documented for status "
                    + response.statusCode() + "; documented: " + content.keySet());
        }

        Map<String, Object> schema = new LinkedHashMap<>(map(media.get("schema")));
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("components", document.get("components"));
        assertThat(method.toUpperCase(Locale.ROOT) + " " + template + " " + response.statusCode() + " body",
                response.asString(), matchesJsonSchema(MAPPER.writeValueAsString(schema)));
        return response;
    }

    private String findTemplate(String apiPath) {
        for (String template : map(document.get("paths")).keySet()) {
            if (apiPath.matches(template.replaceAll("\\{[^/]+}", "[^/]+"))) {
                return template;
            }
        }
        throw new AssertionError(apiPath + " is not a path in the OpenAPI description");
    }

    /** Follows a {@code $ref} such as {@code #/components/responses/Problem}. */
    private Map<String, Object> resolve(Map<String, Object> object) {
        Object ref = object.get("$ref");
        if (!(ref instanceof String pointer)) {
            return object;
        }
        Object current = document;
        for (String part : pointer.substring(2).split("/")) {
            current = map(current).get(part);
        }
        return resolve(map(current));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> list(Object value) {
        return value instanceof java.util.List<?> l ? (java.util.List<Object>) l : java.util.List.of();
    }
}
