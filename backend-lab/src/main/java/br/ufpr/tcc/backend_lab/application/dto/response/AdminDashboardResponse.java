package br.ufpr.tcc.backend_lab.application.dto.response;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long totalQuotes,
        long pendingAcceptance,
        long activeClients,
        BigDecimal totalQuotedValue) {}
