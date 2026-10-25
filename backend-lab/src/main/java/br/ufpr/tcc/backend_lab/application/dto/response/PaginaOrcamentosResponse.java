package br.ufpr.tcc.backend_lab.application.dto.response;

import java.util.List;

public record PaginaOrcamentosResponse(
        List<OrcamentoInternoResponse> items, int page, int pageSize, long totalItems) {}
