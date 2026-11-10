package com.example.payments;

import com.example.http.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Just enough of RFC 7578 (multipart/form-data) to read the parts of a bulk upload. */
final class MultipartParser {

    /** One part: its form field name, optional file name and content type, and its content. */
    record Part(String name, String fileName, String contentType, byte[] content) {
        String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private static final Pattern BOUNDARY = Pattern.compile("boundary=\"?([^\";]+)\"?");
    private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern FILE_NAME = Pattern.compile("filename=\"([^\"]*)\"");

    private MultipartParser() {
    }

    static List<Part> parse(String contentType, byte[] body) {
        Matcher boundaryMatcher = BOUNDARY.matcher(contentType);
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data") || !boundaryMatcher.find()) {
            throw new ApiException(415, "Unsupported Media Type", "Expected multipart/form-data with a boundary");
        }
        // ISO-8859-1 maps every byte to one char and back, so binary parts survive the round trip.
        String raw = new String(body, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundaryMatcher.group(1);
        List<Part> parts = new ArrayList<>();
        for (String section : raw.split(Pattern.quote(delimiter))) {
            if (section.isBlank() || section.startsWith("--")) {
                continue;
            }
            String trimmed = section.startsWith("\r\n") ? section.substring(2) : section;
            int split = trimmed.indexOf("\r\n\r\n");
            if (split < 0) {
                continue;
            }
            String headers = trimmed.substring(0, split);
            String content = trimmed.substring(split + 4);
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }
            parts.add(new Part(find(NAME, headers), find(FILE_NAME, headers), contentTypeOf(headers),
                    content.getBytes(StandardCharsets.ISO_8859_1)));
        }
        return parts;
    }

    private static String find(Pattern pattern, String headers) {
        Matcher matcher = pattern.matcher(headers);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String contentTypeOf(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-type:")) {
                return line.substring("content-type:".length()).trim();
            }
        }
        return "text/plain";
    }
}
