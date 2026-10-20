package br.ufpr.tcc.auth.auth.web.dto;

import jakarta.validation.constraints.NotNull;

public record ApiKeyStatusRequest(@NotNull Boolean enabled) {
}
