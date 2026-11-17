package com.docvault.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 100_000) String content) {
}
