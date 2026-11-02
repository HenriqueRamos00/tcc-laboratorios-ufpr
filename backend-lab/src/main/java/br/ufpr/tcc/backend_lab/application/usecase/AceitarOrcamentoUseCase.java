package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.TermoAceiteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.mapper.MapeadorTermoAceite;
import br.ufpr.tcc.backend_lab.application.service.AcompanhamentoOrcamentosCliente;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorOrcamentoParaDecisao;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorOrdemCompra;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.AceiteOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.OrdemCompraOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.FormaAceiteOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.AceiteOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrdemCompraOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.TermoAceiteOrcamentoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-09: o cliente aceita o orçamento de uma de duas formas, preenchendo o Termo de Aceite ou
 * anexando a ordem de compra. As duas seguem o mesmo caminho e só diferem no que é guardado
 * como comprovante. Tudo acontece numa transação: o orçamento muda de etapa e o comprovante
 * é gravado juntos, ou nada é gravado.
 */
@Service
@RequiredArgsConstructor
public class AceitarOrcamentoUseCase {
    private final ResolvedorOrcamentoParaDecisao resolvedorOrcamento;
    private final AcompanhamentoOrcamentosCliente acompanhamento;
    private final AceiteOrcamentoRepository aceiteRepository;
    private final TermoAceiteOrcamentoRepository termoRepository;
    private final OrdemCompraOrcamentoRepository ordemCompraRepository;
    private final MapeadorTermoAceite mapeadorTermo;
    private final ValidadorOrdemCompra validadorOrdemCompra;

    /** Aceite pelo Termo de Aceite preenchido no portal. */
    @Transactional
    public OrcamentoResponse execute(Long usuarioId, Long orcamentoId, TermoAceiteRequest termo) {
        exigirEmailQuandoAutorizaResultadosPorEmail(termo.approval());

        AceiteOrcamento aceite =
                registrarAceite(usuarioId, orcamentoId, FormaAceiteOrcamento.TERMO_DE_ACEITE);
        termoRepository.save(mapeadorTermo.paraEntidade(aceite, termo));
        return resposta(aceite);
    }

    /** Aceite pela ordem de compra que o cliente já possui (PDF, DOC ou DOCX). */
    @Transactional
    public OrcamentoResponse execute(Long usuarioId, Long orcamentoId, MultipartFile arquivo) {
        var ordemCompraValidada = validadorOrdemCompra.validar(arquivo);

        AceiteOrcamento aceite =
                registrarAceite(usuarioId, orcamentoId, FormaAceiteOrcamento.ORDEM_DE_COMPRA);
        var ordemCompra = new OrdemCompraOrcamento();
        ordemCompra.setAceite(aceite);
        ordemCompra.setFileName(ordemCompraValidada.fileName());
        ordemCompra.setContentType(ordemCompraValidada.contentType());
        ordemCompra.setContent(ordemCompraValidada.content());
        ordemCompraRepository.save(ordemCompra);
        return resposta(aceite);
    }

    private AceiteOrcamento registrarAceite(
            Long usuarioId, Long orcamentoId, FormaAceiteOrcamento forma) {
        var decisao = resolvedorOrcamento.resolver(usuarioId, orcamentoId);
        var orcamento = decisao.orcamento();

        // A regra de quando o aceite é permitido fica no próprio orçamento.
        orcamento.registrarAceite();

        var aceite = new AceiteOrcamento();
        aceite.setOrcamento(orcamento);
        aceite.setCliente(decisao.cliente());
        aceite.setMethod(forma);
        aceite.setAcceptedAt(OffsetDateTime.now());
        return aceiteRepository.save(aceite);
    }

    // HU-09, critério 4: o e-mail só é obrigatório se o cliente autoriza o envio por e-mail.
    private void exigirEmailQuandoAutorizaResultadosPorEmail(TermoAceiteRequest.Aprovacao aprovacao) {
        boolean semEmail = aprovacao.resultsEmail() == null || aprovacao.resultsEmail().isBlank();
        if (aprovacao.allowsResultsByEmail() && semEmail) {
            throw RegraNegocioException.dadosInvalidos(
                    "Informe o e-mail para envio dos resultados");
        }
    }

    private OrcamentoResponse resposta(AceiteOrcamento aceite) {
        var orcamento = aceite.getOrcamento();
        var etapa = acompanhamento.resolver(List.of(orcamento)).get(orcamento.getId());
        return OrcamentoResponse.from(orcamento, etapa);
    }
}
