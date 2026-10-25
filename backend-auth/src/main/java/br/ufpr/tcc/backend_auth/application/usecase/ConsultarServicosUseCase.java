package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.response.ServicoResponse;
import br.ufpr.tcc.backend_auth.application.service.ServicoService;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultarServicosUseCase {
    private final ServicoRepository servicos;
    private final ServicoService cadastro;

    public List<ServicoResponse> listar() {
        return servicos.findAllByOrderByNomeAscIdAsc().stream().map(ServicoResponse::fromEntity).toList();
    }

    public ServicoResponse obter(Long id) {
        return ServicoResponse.fromEntity(cadastro.obter(id));
    }
}
