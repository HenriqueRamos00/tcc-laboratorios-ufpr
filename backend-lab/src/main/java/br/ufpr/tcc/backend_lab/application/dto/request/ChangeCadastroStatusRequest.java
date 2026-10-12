package br.ufpr.tcc.backend_lab.application.dto.request;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeCadastroStatusRequest(
        @NotNull(message = "Status é obrigatório") CadastroStatus status) {}
