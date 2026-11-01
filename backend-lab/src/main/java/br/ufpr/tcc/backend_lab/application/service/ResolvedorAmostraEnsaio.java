package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorIdentificacaoAmostra;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoIdentificacaoAmostraException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
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

    public Amostra resolver(EnsaioRequest request) {
        var identificacao = validador.validar(request);
        if (identificacao.sampleId() != null) {
            return amostras.findById(identificacao.sampleId())
                    .orElseThrow(() -> new EnsaioNaoEncontradoException("Amostra não encontrada"));
        }

        var nova = identificacao.novaAmostra();
        if (amostras.existsByCodigo(nova.codigo())) {
            throw new ConflitoIdentificacaoAmostraException("O código da amostra já está cadastrado");
        }
        var orcamento = orcamentos.findById(nova.quoteId())
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Orçamento não encontrado"));
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
}
