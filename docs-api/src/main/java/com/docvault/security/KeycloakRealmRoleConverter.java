package com.docvault.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns Keycloak realm roles from the token (claim {@code realm_access.roles}, e.g. "editor")
 * into Spring authorities ("ROLE_EDITOR").
 *
 * <p>Contract with {@code DocumentAccess}: it looks for the authority {@code ROLE_ADMIN}.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of(); // no roles in the token: authenticated, but without any rights
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (String) role)
                // Locale.ROOT: "i".toUpperCase() must not become "İ" on a Turkish machine
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableList());
    }
}
