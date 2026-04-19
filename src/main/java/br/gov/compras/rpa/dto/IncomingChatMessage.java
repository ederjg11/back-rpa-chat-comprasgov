package br.gov.compras.rpa.dto;

import jakarta.validation.constraints.NotBlank;

public record IncomingChatMessage(
        @NotBlank String sourceMessageId,
        @NotBlank String content
) {
}
