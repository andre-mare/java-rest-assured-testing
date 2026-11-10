package com.example.module_02.section_06;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import com.example.payments.PaymentsApi;

import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Module 2.6 – multipart/form-data uploads, the format of an HTML form with a file input.
 * <p>
 * {@code POST /payments/bulk} takes a text part {@code batchName} and a CSV part {@code file}, and creates one payment
 * per line. REST Assured sets {@code Content-Type: multipart/form-data} and the boundary between parts itself.
 */
class MultipartUploadTest {

    private static final String CSV = """
            debtorIban,creditorIban,creditorName,amount,currency
            NL91ABNA0417164300,NL69INGB0123456789,Jan Jansen,1200.00,EUR
            NL91ABNA0417164300,GB33BUKB20201555555555,Emily Clarke,950.00,EUR
            NL91ABNA0417164300,NL20INGB0001234567,Anna de Vries,1100.00,EUR
            """;

    private static PaymentsApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification paymentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentsApi.BASE_PATH);
    }

    @Test
    void uploadAFile(@TempDir Path folder) throws IOException {
        Path csv = Files.writeString(folder.resolve("salaries.csv"), CSV);

        paymentsApi()
            .multiPart("batchName", "March salaries")
            .multiPart("file", csv.toFile(), "text/csv")             // the file name comes from the File
        .when()
            .post("/payments/bulk")
        .then()
            .statusCode(201)
            .body("batchName", equalTo("March salaries"))
            .body("fileName", equalTo("salaries.csv"))
            .body("fileContentType", startsWith("text/csv"))
            .body("count", equalTo(3))
            .body("paymentIds", hasSize(3));
    }

    @Test
    void uploadBytesWithAFileName() {
        paymentsApi()
            .multiPart("batchName", "April salaries")
            .multiPart("file", "april.csv", CSV.getBytes(StandardCharsets.UTF_8), "text/csv")
        .when()
            .post("/payments/bulk")
        .then()
            .statusCode(201)
            .body("fileName", equalTo("april.csv"))
            .body("count", equalTo(3));
    }

    @Test
    void buildAPartWithMultiPartSpecBuilder() {
        paymentsApi()
            .multiPart(new MultiPartSpecBuilder("May salaries").controlName("batchName").build())
            .multiPart(new MultiPartSpecBuilder(CSV.getBytes(StandardCharsets.UTF_8))
                    .controlName("file")
                    .fileName("may.csv")
                    .mimeType("text/csv")
                    .build())
        .when()
            .post("/payments/bulk")
        .then()
            .statusCode(201)
            .body("fileName", equalTo("may.csv"));
    }

    @Test
    void theRequestIsMultipartFormData() {
        paymentsApi()
            .multiPart("batchName", "June")
        .when()
            .post("/echo")
        .then()
            .body("contentType", startsWith("multipart/form-data"))
            .body("contentType", containsString("boundary="));
    }

    @Test
    void aMissingPartIsRejected() {
        paymentsApi()
            .multiPart("batchName", "No file")
        .when()
            .post("/payments/bulk")
        .then()
            .statusCode(400);
    }
}
