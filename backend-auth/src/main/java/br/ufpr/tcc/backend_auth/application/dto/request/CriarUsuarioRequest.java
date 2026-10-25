package br.ufpr.tcc.backend_auth.application.dto.request;

import br.ufpr.tcc.backend_auth.application.validation.Normalizacao;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CriarUsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        @Schema(example = "Cliente de exemplo") String name,
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
        @Schema(example = "cliente@example.com", format = "email") String email,
        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 72, message = "Senha deve ter entre 8 e 72 caracteres")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Pelo menos 8 caracteres, incluindo maiúscula, minúscula e número; máximo de 72 bytes UTF-8, preservando espaços.",
                example = "Uma senha forte 123!") String password,
        @NotNull(message = "Perfil é obrigatório") PerfilUsuario role,
        @NotNull(message = "Serviço é obrigatório") @Positive
        @Schema(type = "string", example = "1", description = "ID do serviço ao qual o cliente pertence.") Long serviceId,
        @Schema(description = "IDs das empresas autorizadas. Omitido, null ou [] significa nenhuma empresa; PUT substitui a lista inteira.")
        List<@NotNull @Positive Long> companyIds) {
    public CriarUsuarioRequest {
        name = name == null ? null : name.trim();
        email = Normalizacao.email(email);
        companyIds = companyIds == null ? List.of() : companyIds;
    }

    @Override
    public String toString() {
        return "CriarUsuarioRequest[name=" + name + ", email=" + email
                + ", password=[REDACTED], role=" + role + "]";
    }
}
