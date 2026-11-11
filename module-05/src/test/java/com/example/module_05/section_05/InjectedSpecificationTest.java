package com.example.module_05.section_05;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.module_05.section_05.support.OpenBankingApiTest;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import io.restassured.specification.SpecificationQuerier;

import org.junit.jupiter.api.Test;

/**
 * Module 5.5 – no static state and no set-up code: the extension starts the API and injects specifications.
 * <p>
 * Compare with Module 1, where every class repeated {@code @BeforeAll}/{@code @AfterAll} and a helper method, or set
 * {@code RestAssured.baseURI} globally. Each test gets its own {@link RequestSpecification}, so classes like this one can
 * run in parallel (Module 12).
 */
@OpenBankingApiTest
class InjectedSpecificationTest {

    @Test
    void listAccounts(RequestSpecification openBanking, ResponseSpecification ok) {
        given(openBanking)
        .when()
            .get("/accounts")
        .then()
            .spec(ok)
            .body("Data.Account", hasSize(3));
    }

    @Test
    void readOneAccount(RequestSpecification openBanking, ResponseSpecification ok) {
        given(openBanking)
        .when()
            .get("/accounts/{id}", "40114")
        .then()
            .spec(ok)
            .body("Data.Account[0].AccountType", equalTo("Business"));
    }

    @Test
    void everyTestGetsItsOwnInteractionId(RequestSpecification first, RequestSpecification second) {
        String firstId = SpecificationQuerier.query(first).getHeaders().getValue("x-fapi-interaction-id");
        String secondId = SpecificationQuerier.query(second).getHeaders().getValue("x-fapi-interaction-id");

        assertNotEquals(firstId, secondId);
    }
}
