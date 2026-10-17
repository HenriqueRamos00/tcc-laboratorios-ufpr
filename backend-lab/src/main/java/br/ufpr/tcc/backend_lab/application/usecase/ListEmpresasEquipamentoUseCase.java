package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EmpresaEquipamentoResponse;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEmpresasEquipamentoUseCase {
    private final EmpresaRepository empresas;

    @Transactional(readOnly = true)
    public List<EmpresaEquipamentoResponse> execute() {
        return empresas.pesquisar(null, null).stream().map(EmpresaEquipamentoResponse::from).toList();
    }
}
