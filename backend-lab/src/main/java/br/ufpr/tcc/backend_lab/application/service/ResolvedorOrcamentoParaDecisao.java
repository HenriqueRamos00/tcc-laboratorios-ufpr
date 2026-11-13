package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.repository.AceiteOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RecusaOrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Passos comuns ao aceite e à recusa: identificar o cliente, travar o orçamento, conferir se
 * o cliente pode decidi-lo e se ele ainda não foi decidido. Deve ser chamado dentro da
 * transação do caso de uso, para a trava valer até a decisão ser gravada.
 */
@Component
@RequiredArgsConstructor
public class ResolvedorOrcamentoParaDecisao {
    private final AcessoClienteAtual acessoClienteAtual;
    private final OrcamentoRepository orcamentoRepository;
    private final AceiteOrcamentoRepository aceiteRepository;
    private final RecusaOrcamentoRepository recusaRepository;

    public record OrcamentoParaDecisao(Cliente cliente, Orcamento orcamento) {}

    public OrcamentoParaDecisao resolver(Long usuarioId, Long orcamentoId) {
        Cliente cliente = acessoClienteAtual.execute(usuarioId);
        Orcamento orcamento =
                orcamentoRepository
                        .buscarParaDecisao(orcamentoId)
                        .orElseThrow(
                                () -> RegraNegocioException.naoEncontrado("Orçamento não encontrado"));
        acessoClienteAtual.autorizar(cliente, orcamento);

        // O administrador pode reabrir o status de um orçamento já decidido; mesmo assim a
        // decisão do cliente é única e não é sobrescrita.
        if (aceiteRepository.existsById(orcamentoId) || recusaRepository.existsById(orcamentoId)) {
            throw RegraNegocioException.conflito("Este orçamento já recebeu a decisão do cliente");
        }
        return new OrcamentoParaDecisao(cliente, orcamento);
    }
}
