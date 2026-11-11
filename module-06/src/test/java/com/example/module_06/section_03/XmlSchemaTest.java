package com.example.module_06.section_03;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pisp.PaymentInitiationApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

/**
 * Module 6.3 – XML Schema (XSD) for ISO 20022 messages.
 * <p>
 * Banks exchange payment files as ISO 20022 XML: {@code pain.001} to initiate credit transfers and {@code pain.002} to
 * report their status. Each message type has an XSD. {@code matchesXsdInClasspath(..)} validates a response against
 * one, and since it is a Hamcrest matcher it can also check the files a test sends.
 * The XSDs in {@code src/test/resources/xsd} are simplified subsets of the official ones.
 */
class XmlSchemaTest {

    private static final String PAIN_001_XSD = "xsd/pain.001.001.09-simplified.xsd";
    private static final String PAIN_002_XSD = "xsd/pain.002.001.10-simplified.xsd";
    private static final String STATUS = "Document.CstmrPmtStsRpt.OrgnlGrpInfAndSts";

    private static PaymentInitiationApi api;
    private static String salaries;

    @BeforeAll
    static void startApi() throws IOException {
        api = PaymentInitiationApi.start();
        try (InputStream in = XmlSchemaTest.class.getResourceAsStream("/pain/salaries-march.xml")) {
            salaries = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification paymentFiles() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(PaymentInitiationApi.BASE_PATH)
            .contentType(ContentType.XML);
    }

    @Test
    void theTestDataIsValidToo() {
        assertThat(salaries, matchesXsdInClasspath(PAIN_001_XSD));
    }

    @Test
    void anAcceptedFile() {
        paymentFiles()
            .body(salaries)
        .when()
            .post("/payment-files")
        .then()
            .statusCode(202)
            .contentType(ContentType.XML)
            .body(matchesXsdInClasspath(PAIN_002_XSD))
            .body(STATUS + ".OrgnlMsgId", equalTo("SAL-2026-03"))
            .body(STATUS + ".GrpSts", equalTo("ACCP"));
    }

    @Test
    void aRejectedFileIsStillAValidReport() {
        paymentFiles()
            .body(salaries.replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>3</NbOfTxs>"))
        .when()
            .post("/payment-files")
        .then()
            .statusCode(202)
            .body(matchesXsdInClasspath(PAIN_002_XSD))
            .body(STATUS + ".GrpSts", equalTo("RJCT"))
            .body(STATUS + ".StsRsnInf.AddtlInf", containsString("the file has 2 transactions"));
    }

    @Test
    void theXsdRejectsBrokenDocuments() {
        String noAmountCurrency = salaries.replace("<InstdAmt Ccy=\"EUR\">2450.00</InstdAmt>", "<InstdAmt>2450.00</InstdAmt>");
        // An invalid document doesn't make the matcher return false: it rethrows the validator's SAXParseException,
        // so not(matchesXsdInClasspath(..)) can't express "is invalid". Expect the exception instead.
        SAXParseException invalid = assertThrows(SAXParseException.class,
            () -> assertThat(noAmountCurrency, matchesXsdInClasspath(PAIN_001_XSD)));
        assertThat(invalid.getMessage(), containsString("Attribute 'Ccy' must appear on element 'InstdAmt'"));
    }
}
