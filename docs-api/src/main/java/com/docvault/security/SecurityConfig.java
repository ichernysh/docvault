package com.docvault.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity // turns on @PreAuthorize on controller methods (step 5.4)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // rules are checked top-down, first match wins: specific paths before anyRequest()
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // read "Authorization: Bearer <jwt>", verify signature/iss/exp via the JWKS from application.yml
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // every request carries its own token: nothing to remember on the server
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF abuses browser-sent cookies; we use a header token that the browser never adds by itself
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
