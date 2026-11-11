package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Module 11's API: a Spring Boot 4 accounts service, protected as an OAuth 2.0 resource server.
 * <p>
 * It sits in the root package {@code com.example} so that tests in {@code com.example.module_11...} find it when
 * Spring looks for the {@code @SpringBootConfiguration} in their package and its parents.
 */
@SpringBootApplication
public class AccountsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountsApplication.class, args);
    }
}
