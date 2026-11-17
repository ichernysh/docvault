package com.docvault.document;

public enum Permission {
    READ, WRITE;

    public boolean allowsWrite() {
        return this == WRITE;
    }
}
