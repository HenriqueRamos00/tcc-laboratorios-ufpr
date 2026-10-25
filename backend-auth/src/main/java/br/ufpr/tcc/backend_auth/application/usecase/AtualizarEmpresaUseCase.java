package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.EmpresaRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_auth.application.service.EmpresaService;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtualizarEmpresaUseCase {
    private final EmpresaRepository empresas;
    private final EmpresaService cadastro;

    @Transactional
    public EmpresaResponse execute(Long id, EmpresaRequest request) {
        var empresa = cadastro.bloquear(id);
        cadastro.validar(request, id);
        empresa.setNome(request.name());
        empresa.setCnpj(request.cnpj());
        return EmpresaResponse.fromEntity(empresas.saveAndFlush(empresa));
    }
}
