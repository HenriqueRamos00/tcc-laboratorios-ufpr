package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_auth.application.service.EmpresaService;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultarEmpresasUseCase {
    private final EmpresaRepository empresas;
    private final EmpresaService cadastro;

    public List<EmpresaResponse> listar() {
        return empresas.findAllByOrderByNomeAscIdAsc().stream().map(EmpresaResponse::fromEntity).toList();
    }

    public EmpresaResponse obter(Long id) {
        return EmpresaResponse.fromEntity(cadastro.obter(id));
    }
}
