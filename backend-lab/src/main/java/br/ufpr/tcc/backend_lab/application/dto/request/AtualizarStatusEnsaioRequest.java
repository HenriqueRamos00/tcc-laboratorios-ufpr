package br.ufpr.tcc.backend_lab.application.dto.request;

public record AtualizarStatusEnsaioRequest(
        String action,
        String targetStatus,
        String justification,
        Long version) { }
