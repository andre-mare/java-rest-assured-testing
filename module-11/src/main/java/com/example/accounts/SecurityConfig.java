package com.example.accounts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The service is an OAuth 2.0 resource server:
 * <ul>
 *   <li>reading accounts needs the scope {@code accounts:read} (authority {@code SCOPE_accounts:read})</li>
 *   <li>opening one needs {@code accounts:write}</li>
 *   <li>tokens are JWTs signed with RS256, verified with the public key at {@code accounts.jwt.public-key-location},
 *       and must have the audience {@code accounts.jwt.audience}</li>
 * </ul>
 * Without a token the answer is 401 with {@code WWW-Authenticate: Bearer}; without the scope, 403 with
 * {@code error="insufficient_scope"} (Spring Security follows RFC 6750).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts", "/api/v1/accounts/**").hasAuthority("SCOPE_accounts:read")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAuthority("SCOPE_accounts:write")
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());                           // a token API without cookies: no CSRF risk
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${accounts.jwt.public-key-location}") Resource publicKey,
                          @Value("${accounts.jwt.audience}") String audience) throws IOException, GeneralSecurityException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(publicKey)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),                       // expiry and not-before
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience))));
        return decoder;
    }

    private static RSAPublicKey readPublicKey(Resource resource) throws IOException, GeneralSecurityException {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        }
    }
}
