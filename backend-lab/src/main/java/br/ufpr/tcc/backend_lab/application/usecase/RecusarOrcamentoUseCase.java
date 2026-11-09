package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.RecusaOrcamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.service.AcompanhamentoOrcamentosCliente;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorOrcamentoParaDecisao;
import br.ufpr.tcc.backend_lab.domain.model.entity.RecusaOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.RecusaOrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** HU-10: o cliente recusa o orçamento, com justificativa opcional. A recusa é definitiva. */
@Service
@RequiredArgsConstructor
public class RecusarOrcamentoUseCase {
    private final ResolvedorOrcamentoParaDecisao resolvedorOrcamento;
    private final AcompanhamentoOrcamentosCliente acompanhamento;
    private final RecusaOrcamentoRepository recusaRepository;

    @Transactional
    public OrcamentoResponse execute(
            Long usuarioId, Long orcamentoId, RecusaOrcamentoRequest request) {
        var decisao = resolvedorOrcamento.resolver(usuarioId, orcamentoId);
        var orcamento = decisao.orcamento();

        // A regra de quando a recusa é permitida fica no próprio orçamento.
        orcamento.registrarRecusa();

        var recusa = new RecusaOrcamento();
        recusa.setOrcamento(orcamento);
        recusa.setCliente(decisao.cliente());
        recusa.setJustification(justificativaOuNulo(request.justification()));
        recusa.setRefusedAt(OffsetDateTime.now());
        recusaRepository.save(recusa);

        var etapa = acompanhamento.resolver(List.of(orcamento)).get(orcamento.getId());
        return OrcamentoResponse.from(orcamento, etapa);
    }

    private static String justificativaOuNulo(String justificativa) {
        return justificativa == null || justificativa.isBlank() ? null : justificativa;
    }
}
