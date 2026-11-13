package br.ufpr.tcc.auth.shared.security;

import java.util.Optional;

/**
 * Porta que a cadeia de seguranca usa para transformar a credencial apresentada em
 * identidade.
 *
 * Ela recebe o texto apresentado, e nao um tipo da funcionalidade de credencial, para que
 * o pacote compartilhado continue sem conhecer funcionalidade nenhuma.
 *
 * Ausencia de resultado e a unica resposta possivel para recusa, e isso e proposital:
 * token desconhecido, malformado, revogado, vencido e de aplicacao desativada precisam ser
 * indistinguiveis para quem chama, e a forma mais segura de garantir isso e a camada de
 * cima nao ter como diferenciar nem por acidente.
 */
public interface ApplicationCallerDirectory {

    Optional<ApplicationCaller> resolve(String presentedToken);
}
