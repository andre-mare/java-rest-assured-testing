package com.example.module_04.section_03;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.consents.ConsentsApi;
import com.example.module_04.support.Models.Account;
import com.example.module_04.support.Models.Consent;
import com.example.module_04.support.Models.Customer;
import com.example.module_04.support.Models.NewConsent;
import com.example.module_04.support.Models.NewCustomer;
import com.example.module_04.support.Models.Page;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Module 4.3 – mapping response bodies to Java records, and asserting on them with AssertJ.
 * <p>
 * {@code as(Customer.class)} reads the body with the object mapper REST Assured finds on the classpath; here that is
 * Jackson 3. A generic type such as {@code List<Account>} or {@code Page<Customer>} loses its type argument at
 * runtime, so it is given as a {@link TypeRef}, an anonymous subclass that keeps it.
 */
class ObjectMappingTest {

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
    void aRecord() {
        Customer customer = consentsApi()
        .when()
            .get("/customers/{id}", "cust-3")
        .then()
            .statusCode(200)
            .extract().as(Customer.class);

        assertThat(customer.name()).isEqualTo("Bakkerij Bart B.V.");
        assertThat(customer.segment()).isEqualTo("BUSINESS");
        assertThat(customer.createdAt()).isBefore(Instant.now());      // an ISO-8601 string became an Instant
    }

    @Test
    void aRecordInAndARecordOut() {
        NewCustomer request = new NewCustomer("Sanne Smit", "sanne@example.nl", "RETAIL");

        Customer created = consentsApi()
            .contentType(ContentType.JSON)
            .body(request)                                              // serialized with Jackson 3
        .when()
            .post("/customers")
        .then()
            .statusCode(201)
            .extract().as(Customer.class);

        assertThat(created)
            .returns("Sanne Smit", Customer::name)
            .returns("sanne@example.nl", Customer::email);
        assertThat(created.id()).startsWith("cust-");
    }

    @Test
    void aGenericPageNeedsATypeRef() {
        Page<Customer> page = consentsApi()
            .queryParam("size", 2)
        .when()
            .get("/customers")
        .then()
            .extract().as(new TypeRef<Page<Customer>>() {});

        assertThat(page.totalItems()).isGreaterThanOrEqualTo(5);
        assertThat(page.items()).hasSize(2).allSatisfy(customer -> assertThat(customer.id()).startsWith("cust-"));
    }

    @Test
    void aJsonArrayAsAList() {
        String consentId = authorisedConsent("cust-1", List.of("ReadAccountsBasic"));

        List<Account> accounts = consentsApi()
            .header("Consent-ID", consentId)
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .extract().as(new TypeRef<List<Account>>() {});

        assertThat(accounts)
            .extracting(Account::type)
            .containsExactly("CURRENT", "SAVINGS");
        assertThat(accounts).allSatisfy(account -> assertThat(account.iban()).matches("NL\\d{2}BANK\\d{10}"));
    }

    @Test
    void unknownFieldsAreIgnoredByDefault() {
        // The API also sends "productCode" and "balance", which Account doesn't have. Jackson 3 ignores unknown
        // properties by default (Jackson 2 failed on them), so a new field in the API doesn't break this test.
        // Section 4 shows how to make it strict when you want to notice such changes.
        String consentId = authorisedConsent("cust-2", List.of("ReadAccountsBasic"));

        List<Account> accounts = consentsApi()
            .header("Consent-ID", consentId)
            .when().get("/accounts")
            .then().extract().as(new TypeRef<>() {});                   // the type argument can be inferred

        assertThat(accounts).isNotEmpty();
    }

    private static String authorisedConsent(String customerId, List<String> permissions) {
        Consent consent = consentsApi()
            .contentType(ContentType.JSON)
            .body(new NewConsent(customerId, permissions))
            .when().post("/consents")
            .then().statusCode(201)
            .extract().as(Consent.class);

        consentsApi().when().post(consent.links().get("authorise").href().substring(ConsentsApi.BASE_PATH.length()))
            .then().statusCode(200);
        return consent.consentId();
    }
}
