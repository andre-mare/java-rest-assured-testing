package com.example.module_11.section_02;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.example.accounts.AccountController;
import com.example.accounts.AccountService;
import com.example.accounts.SecurityConfig;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Module 11.2 – a web slice: the controller with the real Spring MVC and Spring Security configuration, no server.
 * <p>
 * {@code @WebMvcTest} starts a Spring context with only the web layer. The security configuration and the service are
 * imported, so requests go through the real filter chain. Spring Security's test support then stands in for the
 * token: {@code jwt()} puts an authenticated JWT principal with the given authorities on the request, so the test
 * needs no authorization server and no signing key. It tests the rules, not the token validation; section 3 does
 * that end to end.
 */
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, AccountService.class})
class WebMvcSliceTest {

    @Autowired
    private MockMvc mockMvc;

    private MockMvcRequestSpecification accounts() {
        return given().mockMvc(mockMvc);
    }

    private static JwtRequestPostProcessor tokenWith(String... scopes) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
        }
        return jwt().authorities(authorities);
    }

    @Test
    void aReaderCanListAccounts() {
        accounts()
            .postProcessors(tokenWith("accounts:read"))
        .when()
            .get("/api/v1/accounts")
        .then()
            .statusCode(200)
            .body("id", contains("acc-1001", "acc-1002"));
    }

    @Test
    void withoutATokenTheAnswerIs401() {
        accounts()
        .when()
            .get("/api/v1/accounts")
        .then()
            .statusCode(401)
            .header("WWW-Authenticate", startsWith("Bearer"));
    }

    @Test
    void aReaderCannotOpenAccounts() {
        accounts()
            .auth().with(tokenWith("accounts:read"))                   // the same, through auth()
            .contentType(ContentType.JSON)
            .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Sanne\", \"currency\": \"EUR\"}")
        .when()
            .post("/api/v1/accounts")
        .then()
            .statusCode(403)
            .header("WWW-Authenticate", containsString("insufficient_scope"));
    }

    @Test
    void aWriterCanOpenAccounts() {
        accounts()
            .postProcessors(tokenWith("accounts:write"))
            .contentType(ContentType.JSON)
            .body("{\"iban\": \"NL20INGB0001234567\", \"owner\": \"Sanne\", \"currency\": \"EUR\"}")
        .when()
            .post("/api/v1/accounts")
        .then()
            .statusCode(201);
    }

    @Test
    void errorsAreProblemDetails() {
        accounts()
            .postProcessors(tokenWith("accounts:read"))
        .when()
            .get("/api/v1/accounts/{id}", "acc-9999")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("detail", equalTo("Account acc-9999 does not exist"));
    }
}
