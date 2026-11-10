package com.example.module_04.section_02;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consents.ConsentsApi;
import com.example.module_04.support.Models.Customer;

import io.restassured.path.json.JsonPath;
import io.restassured.path.xml.XmlPath;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 4.2 – {@link JsonPath} and {@link XmlPath} as objects, for reading many values or JSON that didn't come from
 * an HTTP call.
 * <p>
 * The typed getters ({@code getString}, {@code getInt}, {@code getList(path, type)}) convert the value, so the test
 * doesn't depend on whether REST Assured read a number as Integer or Long.
 */
class JsonPathAndXmlPathTest {

    private static ConsentsApi api;

    @BeforeAll
    static void startApi() {
        api = ConsentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static JsonPath firstPageOfCustomers() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(ConsentsApi.BASE_PATH)
            .queryParam("size", 3)
        .when()
            .get("/customers")
        .then()
            .statusCode(200)
            .extract().jsonPath();
    }

    @Test
    void typedGetters() {
        JsonPath page = firstPageOfCustomers();

        assertThat(page.getInt("totalItems")).isEqualTo(5);
        assertThat(page.getInt("totalPages")).isEqualTo(2);
        assertThat(page.getString("items[0].name")).isEqualTo("Anna de Vries");
        assertThat(page.getList("items.email", String.class)).hasSize(3).allMatch(email -> email.contains("@"));
    }

    @Test
    void mapsAndObjects() {
        JsonPath page = firstPageOfCustomers();

        Map<String, Object> first = page.getMap("items[0]");
        assertThat(first).containsEntry("segment", "RETAIL");

        Customer third = page.getObject("items[2]", Customer.class);   // mapped with Jackson 3
        assertThat(third.name()).isEqualTo("Bakkerij Bart B.V.");
        assertThat(third.createdAt()).isNotNull();

        List<Customer> all = page.getList("items", Customer.class);
        assertThat(all).extracting(Customer::segment).containsExactly("RETAIL", "RETAIL", "BUSINESS");
    }

    @Test
    void jsonThatDidntComeFromHttp() {
        // A webhook payload, a file in src/test/resources, a message from a queue: JsonPath reads any JSON text.
        String webhook = """
                {"event": "consent.revoked", "data": {"consentId": "consent-7", "reason": "CUSTOMER_REQUEST"}}
                """;

        JsonPath json = JsonPath.from(webhook);

        assertThat(json.getString("event")).isEqualTo("consent.revoked");
        assertThat(json.getString("data.reason")).isEqualTo("CUSTOMER_REQUEST");
    }

    @Test
    void xmlPathWorksTheSameWay() {
        String xml = """
                <Document><CstmrPmtStsRpt><OrgnlGrpInfAndSts>
                  <OrgnlMsgId>MSG-42</OrgnlMsgId><GrpSts>ACCP</GrpSts>
                </OrgnlGrpInfAndSts></CstmrPmtStsRpt></Document>
                """;

        XmlPath status = XmlPath.from(xml).setRootPath("Document.CstmrPmtStsRpt.OrgnlGrpInfAndSts");

        assertThat(status.getString("OrgnlMsgId")).isEqualTo("MSG-42");
        assertThat(status.getString("GrpSts")).isEqualTo("ACCP");      // ISO 20022 code for "accepted"
    }
}
