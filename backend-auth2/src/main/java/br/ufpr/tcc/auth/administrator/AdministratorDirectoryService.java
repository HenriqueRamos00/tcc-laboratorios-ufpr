package br.ufpr.tcc.auth.administrator;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Leitura do cadastro de administradores, sem nada de credencial. */
@Service
public class AdministratorDirectoryService {

    private final AdministratorRegistry registry;

    AdministratorDirectoryService(AdministratorRegistry registry) {
        this.registry = registry;
    }

    public List<AdministratorSummary> list() {
        return registry.list().stream().map(AdministratorDirectoryService::summaryOf).toList();
    }

    /** Identificador que nao e numero e identificador que nao existe sao o mesmo caso. */
    public Optional<AdministratorSummary> find(String identifier) {
        return AdministratorRegistry.identifierOf(identifier)
                .flatMap(registry::findById)
                .map(AdministratorDirectoryService::summaryOf);
    }

    private static AdministratorSummary summaryOf(AdministratorRegistry.AdministratorView view) {
        return new AdministratorSummary(view.id(), view.name(), view.email(), view.active());
    }
}
