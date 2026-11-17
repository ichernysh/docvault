package com.docvault.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identity of the caller extracted from the access token: Keycloak subject + tenant claim. */
public record CurrentUser(String userId, String tenantId) {

    public static final String TENANT_CLAIM = "tenant_id";

    public static CurrentUser from(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
        if (tenantId == null || tenantId.isBlank()) {
            // authenticated, but not a member of any tenant -> 403, not 401
            throw new AccessDeniedException("Token has no tenant_id claim");
        }
        return new CurrentUser(jwt.getSubject(), tenantId);
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return from(jwt);
        }
        throw new AccessDeniedException("Unsupported authentication type");
    }
}
