package com.example.module_08.support;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logs each exchange on one line per message, with personal data masked: IBANs keep their first and last four
 * characters, and the values of name fields are replaced. {@code LogConfig.blacklistHeader(..)} (Module 5) hides
 * headers; masking data inside bodies needs a filter like this one.
 */
public final class MaskingLogFilter implements Filter {

    private static final Pattern IBAN = Pattern.compile("\\b([A-Z]{2}[0-9]{2})([A-Z0-9]{7,26})([A-Z0-9]{4})\\b");
    private static final Pattern NAME_FIELD = Pattern.compile("(\"[A-Za-z]*[Nn]ame\"\\s*:\\s*\")([^\"]*)(\")");

    private final PrintStream out;

    public MaskingLogFilter(PrintStream out) {
        this.out = out;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Object body = request.getBody();
        out.println("> " + request.getMethod() + " " + request.getURI() + (body == null ? "" : " " + mask(body.toString())));
        Response response = context.next(request, responseSpec);
        out.println("< " + response.statusCode() + " " + mask(response.asString()));
        return response;
    }

    static String mask(String text) {
        Matcher iban = IBAN.matcher(text);
        String masked = iban.replaceAll(m -> m.group(1) + "*".repeat(m.group(2).length()) + m.group(3));
        return NAME_FIELD.matcher(masked).replaceAll("$1***$3");
    }
}
