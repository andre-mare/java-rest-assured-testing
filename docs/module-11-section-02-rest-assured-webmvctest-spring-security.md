# A Web Slice With Spring Security: RestAssuredMockMvc, @WebMvcTest and jwt()

A Web Slice With Spring Security: Test a Spring Boot controller with its real Spring MVC and Spring Security configuration but no server, using @WebMvcTest, RestAssuredMockMvc and Spring Security's jwt() post-processor, covering readers, writers, 401, 403 insufficient_scope and problem details.

[Section 1](module-11-section-01-rest-assured-mockmvc-standalone.md) tested the controller alone: fast, but blind to security. In this service, security is the most important rule of all: reading accounts needs one scope, opening an account another. Those rules live in a Spring Security configuration, not in the controller. To test them, you need Spring's web layer and the security filter chain, but not the whole application, and not a real token from a real authorization server.

This post follows [Section 2 of Module 11](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-11) of the REST Assured course. It tests the security rules in a web slice with `RestAssuredMockMvc`. The examples are in [`WebMvcSliceTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_02/WebMvcSliceTest.java).

## What is a Web Slice?

`@WebMvcTest` is a Spring Boot test annotation that starts a **slice** of the application: a Spring context with only the web layer.

1. **Included**: the named controller, Spring MVC's configuration, JSON conversion, error handling with problem details, and Spring Security's filter chain.
2. **Left out**: services, repositories and everything else, unless the test imports them.
3. **No server**: requests go through `MockMvc`, as in Section 1.

In Spring Boot 4, `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`. The package changed from Spring Boot 3.

## The Security Rules

[`SecurityConfig`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/main/java/com/example/accounts/SecurityConfig.java) makes the service an OAuth 2.0 resource server:

```java
http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(HttpMethod.GET, "/api/v1/accounts", "/api/v1/accounts/**").hasAuthority("SCOPE_accounts:read")
        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_accounts:write")
        .requestMatchers("/error").permitAll()
        .anyRequest().denyAll())
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

Spring Security turns each scope in a token into an authority with the prefix `SCOPE_`. Without a token, the answer is `401` with `WWW-Authenticate: Bearer`. With a token that lacks the scope, it is `403` with `error="insufficient_scope"`, as RFC 6750 requires and [Module 7](module-07-section-03-rest-assured-negative-security-tests.md) tested by hand.

## The Slice

```java
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, AccountService.class})
class WebMvcSliceTest {

    @Autowired
    private MockMvc mockMvc;

    private MockMvcRequestSpecification accounts() {
        return given().mockMvc(mockMvc);
    }
    ...
}
```

- **`@WebMvcTest(AccountController.class)`** starts the web layer with that one controller.
- **`@Import(..)`** adds the security configuration and the service, which the slice would otherwise leave out.
- **`given().mockMvc(mockMvc)`** gives REST Assured the `MockMvc` that Spring built, with the security filter chain in it.

## Standing In for the Token

```java
private static JwtRequestPostProcessor tokenWith(String... scopes) {
    SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
    for (int i = 0; i < scopes.length; i++) {
        authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
    }
    return jwt().authorities(authorities);
}
```

`jwt()` comes from Spring Security's test support, `SecurityMockMvcRequestPostProcessors`. It is a **request post-processor**: it puts an authenticated JWT principal with the given authorities directly on the request. No token is signed, sent or decoded. The test needs no authorization server and no key, and it tests exactly one thing: the authorisation rules.

It does **not** test token validation, such as signature, expiry and audience. [Section 3](module-11-section-03-rest-assured-spring-boot-random-port.md) does that, end to end.

## Testing the Rules

### Step 1: A Reader Can List Accounts

```java
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
```

`postProcessors(..)` applies the post-processor to this request.

### Step 2: Without a Token, 401

```java
@Test
void withoutATokenTheAnswerIs401() {
    accounts()
    .when()
        .get("/api/v1/accounts")
    .then()
        .statusCode(401)
        .header("WWW-Authenticate", startsWith("Bearer"));
}
```

### Step 3: A Reader Can't Open Accounts

```java
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
```

`auth().with(..)` applies a post-processor too. It reads like the `auth()` calls of plain REST Assured, which makes the authentication stand out in the test.

### Step 4: A Writer Can Open Accounts

```java
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
```

> **The code above, ready to run**
>
> All five tests in [`WebMvcSliceTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/src/test/java/com/example/module_11/section_02/WebMvcSliceTest.java) pass with `mvn test -pl module-11 -Dtest=WebMvcSliceTest`. Change a scope in `SecurityConfig` and see which tests catch it.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 11 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/README.md)

## Errors Are Problem Details

```java
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
```

In standalone mode, this `404` had an empty body. The slice includes Spring Boot's error handling, and `spring.mvc.problemdetails.enabled=true` in `application.properties` turns errors into RFC 9457 problem details. Now the format can be tested.

## Common Pitfalls

1. **Forgetting to import the security configuration.** Without `@Import(SecurityConfig.class)`, the slice uses Spring Boot's default security, and the tests check rules that aren't yours.
2. **Forgetting the service.** `@WebMvcTest` doesn't create services. Import it, or provide a mock with `@MockitoBean`.
3. **Mistaking `jwt()` for token validation.** It skips signature, expiry and audience. A slice test proves the rules; only a test with real tokens proves validation.
4. **Scopes without the `SCOPE_` prefix.** `jwt().authorities(new SimpleGrantedAuthority("accounts:read"))` doesn't match `hasAuthority("SCOPE_accounts:read")`. Add the prefix, as `tokenWith(..)` does.
5. **The Spring Boot 3 package.** `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` is gone in Spring Boot 4. Use `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.

## Wrapping Up

`@WebMvcTest` starts a slice of a Spring Boot application: the web layer, with the real Spring MVC and Spring Security configuration, but no server. `given().mockMvc(mockMvc)` hands that `MockMvc` to `RestAssuredMockMvc`. Spring Security's `jwt()` post-processor, applied with `postProcessors(..)` or `auth().with(..)`, stands in for the token, so the tests check the authorisation rules (401, 403 with `insufficient_scope`, the right scope) without an authorization server. Errors arrive as problem details, which standalone mode couldn't show. Token validation itself is for the next section.
