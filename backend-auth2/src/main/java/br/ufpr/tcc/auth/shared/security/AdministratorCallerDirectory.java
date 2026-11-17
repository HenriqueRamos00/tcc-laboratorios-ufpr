package br.ufpr.tcc.auth.shared.security;

import java.util.Optional;

/** Porta que transforma a credencial administrativa apresentada em identidade. */
public interface AdministratorCallerDirectory {

    Optional<AdministratorCaller> resolve(String presentedToken);
}
