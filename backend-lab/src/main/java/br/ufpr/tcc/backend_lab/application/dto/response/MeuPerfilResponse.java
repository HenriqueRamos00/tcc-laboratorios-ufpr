package br.ufpr.tcc.backend_lab.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeuPerfilResponse(
        String name,
        String email,
        String role,
        Boolean active,
        String unit,
        String specialty,
        String phone,
        String position,
        List<String> editableFields,
        List<String> actions,
        List<EmpresaPerfilResponse> companies,
        PendingEmailChangeResponse pendingEmailChange,
        PasswordPolicyResponse passwordPolicy) {

    public record EmpresaPerfilResponse(String id, String name) {}

    public record PendingEmailChangeResponse(
            String newEmail,
            java.time.OffsetDateTime expiresAt,
            boolean currentEmailConfirmed,
            boolean newEmailConfirmed) {}

    public record PasswordPolicyResponse(
            int minCodePoints,
            int maxCharacters,
            int maxUtf8Bytes,
            boolean requiresUppercase,
            boolean requiresLowercase,
            boolean requiresDigit,
            boolean rejectsBlank) {}
}
