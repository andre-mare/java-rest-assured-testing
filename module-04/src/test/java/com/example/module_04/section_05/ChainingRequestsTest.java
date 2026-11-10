package com.example.module_04.section_05;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.example.consents.ConsentsApi;
import com.example.module_04.support.Models.Account;
import com.example.module_04.support.Models.Consent;
import com.example.module_04.support.Models.Customer;
import com.example.module_04.support.Models.NewConsent;
import com.example.module_04.support.Models.NewCustomer;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 4.5 – a flow of requests in which each step uses what the previous one returned.
 * <p>
 * This is the open banking account-access flow in miniature: create a customer, request a consent, follow its
 * {@code authorise} link, then use the consent id to read accounts and balances. Every test creates the data it needs,
 * so the tests stay independent of each other and of their order.
 */
class ChainingRequestsTest {

    private static ConsentsApi api;

    @BeforeAll
    static void startApi() {
        api = ConsentsApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    private static RequestSpecification consentsApi() {
        return given().baseUri(api.baseUri()).port(api.port()).basePath(ConsentsApi.BASE_PATH);
    }

    @Test
    void createThenFollowTheLocationHeader() {
        String location = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewCustomer("Lotte Laan", "lotte@example.nl", "RETAIL"))
        .when()
            .post("/customers")
        .then()
            .statusCode(201)
            .extract().header("Location");

        // The Location is an absolute path, so it is resolved against the server, not the base path.
        given().baseUri(api.baseUri()).port(api.port())
        .when()
            .get(location)
        .then()
            .statusCode(200)
            .body("name", equalTo("Lotte Laan"));
    }

    @Test
    void theFullConsentFlow() {
        Customer customer = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewCustomer("Daan Dekker", "daan@example.nl", "RETAIL"))
            .when().post("/customers")
            .then().statusCode(201)
            .extract().as(Customer.class);

        Consent consent = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewConsent(customer.id(), List.of("ReadAccountsBasic", "ReadBalances")))
            .when().post("/consents")
            .then().statusCode(201)
            .body("status", equalTo("AWAITING_AUTHORISATION"))
            .extract().as(Consent.class);

        given().baseUri(api.baseUri()).port(api.port())
            .when().post(consent.links().get("authorise").href())      // follow the link, don't build the URL
            .then().statusCode(200)
            .body("status", equalTo("AUTHORISED"));

        List<Account> accounts = consentsApi()
            .header("Consent-ID", consent.consentId())
            .when().get("/accounts")
            .then().statusCode(200)
            .extract().as(new TypeRef<List<Account>>() {});
        assertThat(accounts).hasSize(2);

        consentsApi()
            .header("Consent-ID", consent.consentId())
        .when()
            .get("/accounts/{id}/balances", accounts.getFirst().accountId())
        .then()
            .statusCode(200)
            .body("balances[0].type", equalTo("closingBooked"))
            .body("balances[0].amount.currency", equalTo("EUR"));
    }

    @Test
    void aConsentThatIsNotAuthorisedOpensNothing() {
        String consentId = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewConsent("cust-1", List.of("ReadAccountsBasic")))
            .when().post("/consents")
            .then().statusCode(201)
            .extract().path("consentId");

        consentsApi()
            .header("Consent-ID", consentId)
        .when()
            .get("/accounts")
        .then()
            .statusCode(403)
            .body("detail", containsString("not authorised"));
    }

    @Test
    void aConsentOnlyGrantsItsPermissions() {
        Consent consent = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewConsent("cust-1", List.of("ReadAccountsBasic")))   // no ReadBalances
            .when().post("/consents")
            .then().extract().as(Consent.class);
        consentsApi().when().post("/consents/{id}/authorisation", consent.consentId()).then().statusCode(200);

        consentsApi()
            .header("Consent-ID", consent.consentId())
        .when()
            .get("/accounts/{id}/balances", "acc-1001")
        .then()
            .statusCode(403)
            .body("detail", containsString("ReadBalances"));
    }
}
