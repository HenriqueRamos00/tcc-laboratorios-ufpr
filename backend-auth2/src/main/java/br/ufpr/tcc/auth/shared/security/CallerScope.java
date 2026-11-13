package br.ufpr.tcc.auth.shared.security;

import java.util.Optional;

/**
 * Traduz quem chamou em alcance de consulta.
 *
 * Aplicacao enxerga a propria aplicacao e nada mais. Administrador atravessa aplicacoes, e
 * por isso o alcance dele e a ausencia de filtro, representada por vazio em vez de um
 * valor especial: numero magico de "todas" acaba virando filtro de verdade no dia em que
 * alguem compara sem perceber.
 */
public final class CallerScope {

    private CallerScope() {
    }

    public static Optional<Long> applicationScopeOf(Object principal) {
        return principal instanceof ApplicationCaller caller
                ? Optional.of(caller.applicationId())
                : Optional.empty();
    }

    public static boolean isAdministrator(Object principal) {
        return principal instanceof AdministratorCaller;
    }
}
