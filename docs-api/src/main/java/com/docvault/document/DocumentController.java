package com.docvault.document;

import com.docvault.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    // TODO(you, step 5): role gate, e.g. @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DocumentRequest request) {
        return service.create(CurrentUser.from(jwt), request);
    }

    // any authenticated tenant member; the service returns only documents visible to the caller
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.from(jwt));
    }

    // TODO(you, step 5): @PreAuthorize("@documentAccess.canRead(#id, authentication)")
    @GetMapping("/{id}")
    public DocumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentUser.from(jwt), id);
    }

    // TODO(you, step 5): role gate + @documentAccess.canWrite(#id, authentication)
    @PutMapping("/{id}")
    public DocumentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody DocumentRequest request) {
        return service.update(CurrentUser.from(jwt), id, request);
    }

    // TODO(you, step 5): @documentAccess.isOwner(#id, authentication)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.from(jwt), id);
    }

    // TODO(you, step 5): @documentAccess.isOwner(#id, authentication)
    @GetMapping("/{id}/shares")
    public List<ShareResponse> shares(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.listShares(CurrentUser.from(jwt), id);
    }

    // TODO(you, step 5): @documentAccess.isOwner(#id, authentication)
    @PutMapping("/{id}/shares")
    public ShareResponse share(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody ShareRequest request) {
        return service.share(CurrentUser.from(jwt), id, request);
    }

    // TODO(you, step 5): @documentAccess.isOwner(#id, authentication)
    @DeleteMapping("/{id}/shares/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String userId) {
        service.unshare(CurrentUser.from(jwt), id, userId);
    }
}
