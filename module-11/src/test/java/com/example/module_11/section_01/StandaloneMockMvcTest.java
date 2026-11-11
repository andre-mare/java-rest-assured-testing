package com.example.module_11.section_01;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.example.accounts.AccountController;
import com.example.accounts.AccountService;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;

import org.junit.jupiter.api.Test;

/**
 * Module 11.1 – {@code RestAssuredMockMvc}: REST Assured's syntax, Spring MVC's {@code MockMvc} underneath.
 * <p>
 * No server is started and no HTTP goes over a socket: MockMvc calls the {@code DispatcherServlet} directly. With
 * {@code standaloneSetup(..)} not even a Spring context is created; the controller is an object you build yourself.
 * That makes these the fastest web tests, at the price of leaving out everything the context would add, such as
 * security and problem details.
 */
class StandaloneMockMvcTest {

    private static MockMvcRequestSpecification accounts() {
        return given().standaloneSetup(new AccountController(new AccountService()));
    }

    @Test
    void listAccounts() {
        accounts()
        .when()
            .get("/api/v1/accounts")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(2))
            .body("owner[0]", equalTo("Anna de Vries"));               // "owner" collects the owner of every element
    }

    @Test
    void readOneAccount() {
        accounts()
        .when()
            .get("/api/v1/accounts/{id}", "acc-1002")
        .then()
            .statusCode(200)
            .body("balance", equalTo("15000.00"));
    }

    @Test
    void anUnknownAccount() {
        accounts().when().get("/api/v1/accounts/{id}", "acc-9999").then().statusCode(404);
    }

    @Test
    void openAnAccount() {
        accounts()
            .contentType(ContentType.JSON)
            .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Sanne Smit\", \"currency\": \"EUR\"}")
        .when()
            .post("/api/v1/accounts")
        .then()
            .statusCode(201)
            .header("Location", "/api/v1/accounts/acc-1003")
            .body("balance", equalTo("0.00"));
    }

    @Test
    void anInvalidAccount() {
        accounts()
            .contentType(ContentType.JSON)
            .body("{\"owner\": \"No IBAN\"}")
        .when()
            .post("/api/v1/accounts")
        .then()
            .statusCode(422);
    }
}
