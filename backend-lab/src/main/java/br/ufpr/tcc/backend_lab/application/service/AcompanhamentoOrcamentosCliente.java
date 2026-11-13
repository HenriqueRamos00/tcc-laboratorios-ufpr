package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.EtapaAcompanhamentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository.Andamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcompanhamentoOrcamentosCliente {
    private final ExecucaoEnsaioRepository ensaios;

    public Map<Long, EtapaAcompanhamentoOrcamento> resolver(List<Orcamento> orcamentos) {
        if (orcamentos.isEmpty()) return Map.of();
        var ids = orcamentos.stream().map(Orcamento::getId).toList();
        var porOrcamento = ensaios.consultarAndamentos(ids).stream()
                .collect(Collectors.groupingBy(Andamento::getOrcamentoId));
        return orcamentos.stream().collect(Collectors.toMap(
                Orcamento::getId,
                orcamento -> resolver(orcamento, porOrcamento.getOrDefault(orcamento.getId(), List.of()))));
    }

    private EtapaAcompanhamentoOrcamento resolver(Orcamento orcamento, List<Andamento> execucoes) {
        return switch (orcamento.getStatus()) {
            case REFUSED -> EtapaAcompanhamentoOrcamento.RECUSADO;
            case CANCELLED -> EtapaAcompanhamentoOrcamento.CANCELADO;
            case PENDING_ACCEPTANCE, IN_PROGRESS, FINISHED -> execucoes.isEmpty()
                    ? EtapaAcompanhamentoOrcamento.deTexto(orcamento.getStage())
                    : menorEtapaAtiva(execucoes);
        };
    }

    private EtapaAcompanhamentoOrcamento menorEtapaAtiva(List<Andamento> execucoes) {
        return execucoes.stream()
                .filter(ensaio -> ensaio.getStatus() != StatusEnsaio.CANCELLED)
                .map(this::etapaDoEnsaio)
                .min(Enum::compareTo)
                .orElse(EtapaAcompanhamentoOrcamento.CANCELADO);
    }

    private EtapaAcompanhamentoOrcamento etapaDoEnsaio(Andamento ensaio) {
        return switch (ensaio.getStatus()) {
            case INCOMPLETE -> EtapaAcompanhamentoOrcamento.EM_EXECUCAO;
            case COMPLETE -> EtapaAcompanhamentoOrcamento.COMPLETO;
            case VALIDATED -> EtapaAcompanhamentoOrcamento.VALIDADO;
            case FINALIZED -> EtapaAcompanhamentoOrcamento.FINALIZADO;
            case DRAFTING_REPORT -> EtapaAcompanhamentoOrcamento.ELABORANDO_RELATORIO;
            case REPORT_PUBLISHED -> EtapaAcompanhamentoOrcamento.RELATORIO_PUBLICADO;
            case CANCELLED -> EtapaAcompanhamentoOrcamento.CANCELADO;
        };
    }
}
