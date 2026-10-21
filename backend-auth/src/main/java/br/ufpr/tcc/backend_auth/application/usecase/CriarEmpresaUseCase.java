package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.EmpresaRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_auth.application.service.EmpresaService;
import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CriarEmpresaUseCase {
    private final EmpresaRepository empresas;
    private final EmpresaService cadastro;

    @Transactional
    public EmpresaResponse execute(EmpresaRequest request) {
        cadastro.validar(request, null);
        Empresa empresa = new Empresa();
        empresa.setNome(request.name());
        empresa.setCnpj(request.cnpj());
        return EmpresaResponse.fromEntity(empresas.saveAndFlush(empresa));
    }
}
