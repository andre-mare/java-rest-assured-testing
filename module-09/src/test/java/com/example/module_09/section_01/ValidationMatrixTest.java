package com.example.module_09.section_01;

import static com.example.module_09.support.PaymentsClient.newPayment;
import static com.example.module_09.support.PaymentsClient.validPayment;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import com.example.lifecycle.PaymentLifecycleApi;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Module 9.1 – one test method, many inputs: validation rules as a table.
 * <p>
 * Each row is a separate test in the report, named by the {@code name} pattern, so a failure says exactly which input
 * broke. This builds on Module 7 of the JUnit course; here every invocation is an HTTP request.
 */
class ValidationMatrixTest {

    private static PaymentLifecycleApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    @ParameterizedTest(name = "{0} = ''{1}'' -> {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
        debtorIban   | NL00ABNA0417164300     | must be a valid IBAN
        debtorIban   | NL91ABNA0417164301     | must be a valid IBAN
        creditorIban | nl69ingb0123456789     | must be a valid IBAN
        creditorIban | NL69INGB               | must be a valid IBAN
        creditorName | ""                     | must be 1 to 70 characters
        amount       | 0.00                   | must be more than zero
        amount       | -5.00                  | must be more than zero
        amount       | 1.234                  | must have at most two decimals
        amount       | 100000.01              | exceeds the single payment limit of 100000.00
        amount       | ten euros              | must be a decimal number as a string
        currency     | JPY                    | is not supported
        currency     | eur                    | is not supported
        executionDate| 2020-01-01             | must not be in the past
        executionDate| 31-12-2026             | must be a date such as 2026-12-31
        """)
    void invalidFieldsAreRejected(String field, String value, String expectedDetail) {
        Map<String, Object> payment = validPayment("10.00");
        payment.put(field, value);

        newPayment(api)
            .body(payment)
        .when()
            .post("/payments")
        .then()
            .statusCode(422)
            .body("errors", hasSize(1))                              // only the field we broke
            .body("errors[0].field", equalTo(field))
            .body("errors[0].detail", equalTo(expectedDetail));
    }

    @ParameterizedTest(name = "amount {0} is accepted")
    @ValueSource(strings = {"0.01", "1", "99.9", "100000.00"})
    void boundaryAmountsAreAccepted(String amount) {
        newPayment(api).body(validPayment(amount)).when().post("/payments").then().statusCode(201);
    }

    static final List<String> VALID_IBANS = List.of(
            "NL91ABNA0417164300", "GB33BUKB20201555555555", "DE89370400440532013000", "BE68539007547034");

    @ParameterizedTest(name = "creditor IBAN {0} is accepted")
    @FieldSource("VALID_IBANS")
    void ibansFromSeveralCountries(String iban) {
        Map<String, Object> payment = validPayment("10.00");
        payment.put("creditorIban", iban);

        newPayment(api).body(payment).when().post("/payments").then().statusCode(201);
    }

    static Stream<Arguments> severalProblemsAtOnce() {
        return Stream.of(
            Arguments.of(Map.of("currency", "JPY", "amount", "0"), List.of("currency", "amount")),
            Arguments.of(Map.of("debtorIban", "X", "creditorIban", "Y"), List.of("debtorIban", "creditorIban")),
            Arguments.of(Map.of("executionDate", LocalDate.now().minusDays(1).toString(), "creditorName", " "),
                    List.of("executionDate", "creditorName")));
    }

    @ParameterizedTest(name = "{index}: {1} are reported together")
    @MethodSource
    void severalProblemsAtOnce(Map<String, Object> changes, List<String> expectedFields) {
        Map<String, Object> payment = validPayment("10.00");
        payment.putAll(changes);

        var errors = newPayment(api).body(payment)
            .when().post("/payments")
            .then().statusCode(422)
            .body("errors", hasSize(expectedFields.size()));
        for (String field : expectedFields) {
            errors.body("errors.field", hasItem(field));
        }
    }
}
