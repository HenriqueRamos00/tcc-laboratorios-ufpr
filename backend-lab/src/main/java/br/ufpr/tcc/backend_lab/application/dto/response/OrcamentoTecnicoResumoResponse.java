package br.ufpr.tcc.backend_lab.application.dto.response;

import java.time.OffsetDateTime;

public record OrcamentoTecnicoResumoResponse(
        String id,
        String code,
        String name,
        String companyName,
        OffsetDateTime createdDate,
        String technicalStatus) {}
