package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.AcompanhamentoOrcamentosCliente;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListOrcamentosClienteUseCase {
    private final OrcamentoRepository orcamentoRepository;
    private final AcessoClienteAtual acessoClienteAtual;
    private final AcompanhamentoOrcamentosCliente acompanhamento;

    @Transactional(readOnly = true)
    public List<OrcamentoResponse> execute(Long usuarioId) {
        acessoClienteAtual.execute(usuarioId);
        var orcamentos = orcamentoRepository.listarAcessiveisPorUsuario(usuarioId);
        var etapas = acompanhamento.resolver(orcamentos);
        return orcamentos.stream()
                .map(orcamento -> OrcamentoResponse.from(orcamento, etapas.get(orcamento.getId())))
                .toList();
    }
}
