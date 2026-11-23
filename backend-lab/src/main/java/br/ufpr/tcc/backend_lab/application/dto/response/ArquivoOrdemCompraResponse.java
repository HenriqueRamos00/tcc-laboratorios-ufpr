package br.ufpr.tcc.backend_lab.application.dto.response;

public record ArquivoOrdemCompraResponse(byte[] content, String fileName, String contentType) {}
