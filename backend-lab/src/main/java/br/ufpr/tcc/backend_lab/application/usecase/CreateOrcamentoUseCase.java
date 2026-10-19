package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoDetalheInternoResponse;
import br.ufpr.tcc.backend_lab.application.mapper.MapeadorItensOrcamento;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorDocumentoOrcamento;
import br.ufpr.tcc.backend_lab.application.validation.ValidadorOrcamento;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ViolacaoUnicidade;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class CreateOrcamentoUseCase {
    private static final String ETAPA_INICIAL = "Negociação";

    private final EmpresaRepository empresaRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    private final MapeadorItensOrcamento mapeadorItensOrcamento;
    private final ValidadorDocumentoOrcamento validadorDocumentoOrcamento;
    private final ValidadorOrcamento validadorOrcamento;

    @Transactional
    public OrcamentoDetalheInternoResponse execute(
            OrcamentoRequest request, MultipartFile documento) {
        if (!StatusComercialOrcamento.PENDING_ACCEPTANCE.codigo().equals(request.status())) {
            throw RegraNegocioException.dadosInvalidos(
                    "Novos orçamentos devem iniciar com status PendingAcceptance");
        }
        if (orcamentoRepository.existsByCode(request.code())) {
            throw RegraNegocioException.conflito("Código de orçamento já cadastrado");
        }

        var empresa = buscarEmpresaAtiva(request.companyId());
        var totalPrice = validadorOrcamento.calcularTotal(request);
        var conteudoDocumento = validadorDocumentoOrcamento.validar(documento);

        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(request.code());
        orcamento.setName(request.name());
        orcamento.setLaboratoryName(request.laboratoryName());
        orcamento.setDescription(request.description());
        orcamento.setScope(request.scope());
        orcamento.setExternalContactName(request.externalContactName());
        orcamento.setExternalContactEmail(request.externalContactEmail());
        orcamento.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        orcamento.setStage(ETAPA_INICIAL);
        orcamento.setTotalPrice(totalPrice);
        orcamento.setCreatedDate(OffsetDateTime.now());
        mapeadorItensOrcamento.substituirItens(orcamento, request.items());

        try {
            orcamentoRepository.saveAndFlush(orcamento);
        } catch (DataIntegrityViolationException exception) {
            if (ViolacaoUnicidade.codigoOrcamento(exception)) {
                throw RegraNegocioException.conflito("Código de orçamento já cadastrado");
            }
            throw exception;
        }
        if (conteudoDocumento != null) {
            salvarDocumento(orcamento, conteudoDocumento);
        }

        return OrcamentoDetalheInternoResponse.from(orcamento, conteudoDocumento != null);
    }

    private Empresa buscarEmpresaAtiva(String companyId) {
        long empresaId;
        try {
            empresaId = Long.parseLong(companyId);
        } catch (NumberFormatException exception) {
            throw RegraNegocioException.dadosInvalidos("Empresa informada é inválida");
        }

        return empresaRepository
                .findById(empresaId)
                .filter(Empresa::isAtivo)
                .orElseThrow(
                        () -> RegraNegocioException.dadosInvalidos("Empresa ativa não encontrada"));
    }

    private void salvarDocumento(Orcamento orcamento, byte[] conteudoDocumento) {
        DocumentoOrcamento documento = new DocumentoOrcamento();
        documento.setOrcamento(orcamento);
        documento.setContent(conteudoDocumento);
        documentoOrcamentoRepository.saveAndFlush(documento);
    }
}
