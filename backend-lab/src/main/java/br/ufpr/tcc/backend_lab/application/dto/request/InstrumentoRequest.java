package br.ufpr.tcc.backend_lab.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record InstrumentoRequest(
        @NotBlank(message = "Informe o patrimônio")
        @Size(max = 80, message = "O patrimônio aceita até 80 caracteres") String inventoryNumber,
        @NotBlank(message = "Informe o nome do instrumento")
        @Size(max = 160, message = "O nome aceita até 160 caracteres") String name,
        @Size(max = 160, message = "O certificado aceita até 160 caracteres") String calibrationCertificate,
        LocalDate calibrationValidUntil) { }
