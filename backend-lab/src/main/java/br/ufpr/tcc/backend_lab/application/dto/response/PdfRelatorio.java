package br.ufpr.tcc.backend_lab.application.dto.response;

public record PdfRelatorio(byte[] content, String filename, String documentState, long version) { }
