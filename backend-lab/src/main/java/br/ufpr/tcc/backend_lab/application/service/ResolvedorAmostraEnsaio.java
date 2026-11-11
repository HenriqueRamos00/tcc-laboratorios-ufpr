package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorIdentificacaoAmostra;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoIdentificacaoAmostraException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ResolvedorAmostraEnsaio {
    private final AmostraRepository amostras;
    private final OrcamentoRepository orcamentos;
    private final EquipamentoRepository equipamentos;
    private final ValidadorIdentificacaoAmostra validador;

    public ResolvedorAmostraEnsaio(AmostraRepository amostras, OrcamentoRepository orcamentos,
            EquipamentoRepository equipamentos, ValidadorIdentificacaoAmostra validador) {
        this.amostras = amostras;
        this.orcamentos = orcamentos;
        this.equipamentos = equipamentos;
        this.validador = validador;
    }

    public Amostra resolver(EnsaioRequest request, Long orcamentoOriginalId) {
        var identificacao = validador.validar(request);
        if (identificacao.sampleId() != null) {
            Amostra amostra = amostras.findById(identificacao.sampleId())
                    .orElseThrow(() -> new EnsaioNaoEncontradoException("Amostra não encontrada"));
            var orcamento = buscarOrcamentoVinculado(
                    amostra.getOrcamento().getId(), orcamentoOriginalId);
            amostra.setOrcamento(orcamento);
            return amostra;
        }

        var nova = identificacao.novaAmostra();
        var orcamento = buscarOrcamentoVinculado(nova.quoteId(), orcamentoOriginalId);
        if (amostras.existsByCodigo(nova.codigo())) {
            throw new ConflitoIdentificacaoAmostraException("O código da amostra já está cadastrado");
        }
        Amostra amostra = new Amostra();
        amostra.setCodigo(nova.codigo());
        amostra.setDescricao(nova.descricao());
        amostra.setOrcamento(orcamento);
        if (nova.equipamentoId() != null) {
            var equipamento = equipamentos.findById(nova.equipamentoId())
                    .orElseThrow(() -> new EquipamentoNaoEncontradoException(nova.equipamentoId()));
            if (!equipamento.getEmpresa().getId().equals(orcamento.getEmpresa().getId())) {
                throw new DadosEnsaioInvalidosException(
                        "O equipamento analisado deve pertencer à empresa do orçamento");
            }
            amostra.setEquipamentoAnalisado(equipamento);
        }
        return amostras.save(amostra);
    }

    private Orcamento buscarOrcamentoVinculado(Long orcamentoId, Long orcamentoOriginalId) {
        StatusComercialOrcamento statusAtual = orcamentos.buscarStatusComBloqueio(orcamentoId)
                .flatMap(StatusComercialOrcamento::fromCodigo)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Orçamento não encontrado"));
        Orcamento orcamento = orcamentos.findById(orcamentoId)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Orçamento não encontrado"));
        boolean preservaVinculo = Objects.equals(orcamentoId, orcamentoOriginalId);
        if (!preservaVinculo && !statusAtual.aceitaNovosEnsaios()) {
            throw RegraNegocioException.conflito("O orçamento não está disponível para novos ensaios");
        }
        return orcamento;
    }
}
