package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AcessoHistoricoEquipamentoCliente {
    private final EquipamentoRepository equipamentos;
    private final RelatorioTecnicoRepository relatorios;
    private final AcessoClienteAtual acessoCliente;

    public AcessoHistoricoEquipamentoCliente(EquipamentoRepository equipamentos,
            RelatorioTecnicoRepository relatorios, AcessoClienteAtual acessoCliente) {
        this.equipamentos = equipamentos;
        this.relatorios = relatorios;
        this.acessoCliente = acessoCliente;
    }

    /** Retorna o cadastro atual autorizado ou vazio quando só existe histórico autorizado. */
    public Optional<Equipamento> executar(Cliente cliente, Long equipamentoId,
            Collection<Long> empresasAutorizadas) {
        Optional<Equipamento> cadastro = equipamentos.findById(equipamentoId);
        if (cadastro.isPresent()
                && acessoCliente.estaVinculadoA(cliente, cadastro.get().getEmpresa())) {
            return cadastro;
        }
        if (!empresasAutorizadas.isEmpty()
                && relatorios.existePublicacaoAutorizadaDoEquipamento(
                        equipamentoId, empresasAutorizadas, StatusValidacaoRelatorio.VALIDATED)) {
            return Optional.empty();
        }
        if (cadastro.isPresent()) {
            acessoCliente.autorizar(cliente, cadastro.get().getEmpresa());
        }
        if (relatorios.existePublicacaoDisponivelDoEquipamento(
                equipamentoId, StatusValidacaoRelatorio.VALIDATED)) {
            throw RegraNegocioException.acessoNegado("Sem permissão para este equipamento");
        }
        throw new EquipamentoNaoEncontradoException(equipamentoId);
    }
}
