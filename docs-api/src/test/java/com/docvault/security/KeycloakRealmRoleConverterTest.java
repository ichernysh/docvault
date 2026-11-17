package com.docvault.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "none").subject("u");
    }

    @Test
    void realmRolesBecomeUpperCaseRoleAuthorities() {
        var token = jwt().claim("realm_access", Map.of("roles", List.of("editor", "admin"))).build();

        assertThat(converter.convert(token)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EDITOR", "ROLE_ADMIN");
    }

    @Test
    void tokenWithoutRealmAccessGivesNoAuthorities() {
        assertThat(converter.convert(jwt().claim("x", "y").build())).isEmpty();
    }

    @Test
    void realmAccessWithoutRolesGivesNoAuthorities() {
        assertThat(converter.convert(jwt().claim("realm_access", Map.of()).build())).isEmpty();
    }

    @Test
    void nonStringRolesAreIgnored() {
        var token = jwt().claim("realm_access", Map.of("roles", List.of("viewer", 42))).build();

        assertThat(converter.convert(token)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    void resourceAccessRolesAreNotMixedIn() {
        var token = jwt()
                .claim("realm_access", Map.of("roles", List.of("viewer")))
                .claim("resource_access", Map.of("account", Map.of("roles", List.of("manage-account"))))
                .build();

        assertThat(converter.convert(token)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_VIEWER");
    }
}
