package com.example.module_03.section_07;

import static io.restassured.RestAssured.given;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.config.XmlConfig.xmlConfig;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import com.example.transactions.TransactionsApi;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 3.7 – XML and HTML responses use the same {@code body(..)} with GPath, through XmlPath.
 * <p>
 * The statement is a simplified ISO 20022 camt.053, the bank-to-customer statement that banks exchange. In an XML path:
 * <ul>
 *   <li>the path starts with the root element: {@code Document.BkToCstmrStmt...}</li>
 *   <li>{@code @Ccy} reads an attribute</li>
 *   <li>{@code **} searches the whole tree depth first, and {@code it.name()} is the element name</li>
 *   <li>values are text, so compare with strings such as {@code "2407.65"}</li>
 * </ul>
 */
class XmlAndHtmlTest {

    private static final String STATEMENT = "Document.BkToCstmrStmt.Stmt";

    private static TransactionsApi api;

    @BeforeAll
    static void startApi() {
        api = TransactionsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification transactionsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(TransactionsApi.BASE_PATH);
    }

    @Test
    void elementsAndAttributes() {
        transactionsApi()
        .when()
            .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
        .then()
            .contentType(ContentType.XML)
            .body("Document.BkToCstmrStmt.GrpHdr.MsgId", equalTo("STMT-2026-03"))
            .body(STATEMENT + ".Acct.Id.IBAN", equalTo(TransactionsApi.IBAN))
            .body(STATEMENT + ".Ntry.size()", equalTo(6))
            .body(STATEMENT + ".Bal[0].Amt.@Ccy", equalTo("EUR"));
    }

    @Test
    void findAndFindAllWorkOnElements() {
        transactionsApi()
        .when()
            .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
        .then()
            .rootPath(STATEMENT)
            .body("Bal.find { it.Tp == 'CLBD' }.Amt", equalTo("2407.65"))
            .body("Ntry.findAll { it.CdtDbtInd == 'DBIT' }.NtryRef", contains("tx-2", "tx-3", "tx-4", "tx-5"))
            .body("Ntry.NtryDtls.RltdPties.Nm", hasItems("Acme B.V.", "NS Reizigers"));
    }

    @Test
    void depthFirstSearch() {
        transactionsApi()
        .when()
            .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
        .then()
            .body("**.find { it.name() == 'MsgId' }", equalTo("STMT-2026-03"))
            .body("**.findAll { it.name() == 'Ntry' }.size()", equalTo(6));
    }

    @Test
    void namespaces() {
        // Without declaring namespaces, a path matches elements by their local name.
        transactionsApi()
        .when()
            .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
        .then()
            .body(STATEMENT + ".Ntry[2].Category", equalTo("RENT"));

        // Declaring a namespace lets a path match only elements in it.
        transactionsApi()
            .config(config().xmlConfig(xmlConfig().declareNamespace("ext", "urn:example:bank:extensions")))
        .when()
            .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
        .then()
            .body(STATEMENT + ".Ntry[2].ext:Category", equalTo("RENT"));
    }

    @Test
    void html() {
        transactionsApi()
        .when()
            .get("/accounts/{id}/statement.html", TransactionsApi.ACCOUNT_ID)
        .then()
            .contentType(ContentType.HTML)
            .body("html.head.title", equalTo("Statement " + TransactionsApi.IBAN))
            .body("html.body.h1", equalTo("March 2026"))
            // The HTML parser may add a <tbody> to the table, so search the rows depth first instead of table.tr
            .body("**.findAll { it.name() == 'tr' }.size()", equalTo(6))
            .body("**.findAll { it.name() == 'tr' }[2].td[1]", equalTo("Woonstichting De Key"))
            .body("**.find { it.@id == 'transactions' }.name()", equalTo("table"));
    }
}
