package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.service.EmpresaService;
import br.ufpr.tcc.backend_auth.domain.exception.ConflitoContaException;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcluirEmpresaUseCase {
    private final EmpresaRepository empresas;
    private final EmpresaService cadastro;

    @Transactional
    public void execute(Long id) {
        var empresa = cadastro.bloquear(id);
        if (empresas.contarVinculos(id) > 0) {
            throw new ConflitoContaException("Remova as associações com clientes antes de excluir a empresa");
        }
        empresas.delete(empresa);
        empresas.flush();
    }
}
