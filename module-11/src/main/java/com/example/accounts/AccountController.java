package com.example.accounts;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /api/v1/accounts}, {@code GET /api/v1/accounts/{id}} and {@code POST /api/v1/accounts}.
 * Security (which scope each needs) is configured in {@link SecurityConfig}, not here.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<Account> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable("id") String id) {
        return service.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account " + id + " does not exist"));
    }

    @PostMapping
    public ResponseEntity<Account> open(@RequestBody NewAccount request) {
        if (request.iban() == null || !request.iban().matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")
                || request.owner() == null || request.owner().isBlank()
                || request.currency() == null || !request.currency().matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "iban, owner and currency are required");
        }
        Account account = service.open(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }
}
