package br.ufpr.tcc.auth.account.usecase;

import java.util.List;

/**
 * O perfil que o autoatendimento devolve: os dados da conta, o que o cliente pode editar, as
 * acoes disponiveis, as empresas que ele representa, a troca de e-mail pendente e a politica
 * de senha que a troca de senha cobra.
 */
public record SelfServiceProfile(String name, String email, String role, boolean active,
        boolean emailConfirmed, List<String> editableFields, List<String> actions,
        List<SelfServiceCompany> companies, PendingEmailChange pendingEmailChange,
        SelfServicePasswordPolicy passwordPolicy) {

    public record SelfServiceCompany(String id, String cnpj) {
    }

    public record SelfServicePasswordPolicy(int minCodePoints, int maxCharacters,
            Integer maxUtf8Bytes, boolean requiresUppercase, boolean requiresLowercase,
            boolean requiresDigit, boolean rejectsBlank) {
    }
}
