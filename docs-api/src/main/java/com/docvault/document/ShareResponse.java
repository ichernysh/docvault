package com.docvault.document;

public record ShareResponse(String userId, Permission permission) {

    static ShareResponse from(DocumentShare s) {
        return new ShareResponse(s.getId().userId(), s.getPermission());
    }
}
