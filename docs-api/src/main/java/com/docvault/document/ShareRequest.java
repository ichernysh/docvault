package com.docvault.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareRequest(@NotBlank String userId, @NotNull Permission permission) {
}
