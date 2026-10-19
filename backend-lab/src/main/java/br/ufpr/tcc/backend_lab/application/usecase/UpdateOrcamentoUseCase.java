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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UpdateOrcamentoUseCase {
    private final EmpresaRepository empresaRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    private final MapeadorItensOrcamento mapeadorItensOrcamento;
    private final ValidadorDocumentoOrcamento validadorDocumentoOrcamento;
    private final ValidadorOrcamento validadorOrcamento;

    @Transactional
    public OrcamentoDetalheInternoResponse execute(
            Long orcamentoId, OrcamentoRequest request, MultipartFile documento) {
        Orcamento orcamento =
                orcamentoRepository
                        .buscarDetalhePorId(orcamentoId)
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.naoEncontrado(
                                                "Orçamento não encontrado"));

        StatusComercialOrcamento status =
                StatusComercialOrcamento.fromCodigo(request.status())
                        .orElseThrow(
                                () ->
                                        RegraNegocioException.dadosInvalidos(
                                                "Status de orçamento inválido"));
        if (orcamentoRepository.existsByCodeAndIdNot(request.code(), orcamentoId)) {
            throw RegraNegocioException.conflito("Código de orçamento já cadastrado");
        }

        Empresa empresa = resolverEmpresa(request.companyId(), orcamento.getEmpresa());
        var totalPrice = validadorOrcamento.calcularTotal(request);
        var conteudoDocumento = validadorDocumentoOrcamento.validar(documento);

        orcamento.setEmpresa(empresa);
        orcamento.setCode(request.code());
        orcamento.setName(request.name());
        orcamento.setLaboratoryName(request.laboratoryName());
        orcamento.setDescription(request.description());
        orcamento.setScope(request.scope());
        orcamento.setExternalContactName(request.externalContactName());
        orcamento.setExternalContactEmail(request.externalContactEmail());
        orcamento.setStatus(status);
        orcamento.setTotalPrice(totalPrice);
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
            salvarOuSubstituirDocumento(orcamento, conteudoDocumento);
        }

        boolean hasDocument =
                conteudoDocumento != null || documentoOrcamentoRepository.existsById(orcamentoId);
        return OrcamentoDetalheInternoResponse.from(orcamento, hasDocument);
    }

    private Empresa resolverEmpresa(String companyId, Empresa empresaAtual) {
        long empresaId;
        try {
            empresaId = Long.parseLong(companyId);
        } catch (NumberFormatException exception) {
            throw RegraNegocioException.dadosInvalidos("Empresa informada é inválida");
        }

        if (empresaAtual.getId().equals(empresaId)) {
            return empresaAtual;
        }
        return empresaRepository
                .findById(empresaId)
                .filter(Empresa::isAtivo)
                .orElseThrow(
                        () -> RegraNegocioException.dadosInvalidos("Empresa ativa não encontrada"));
    }

    private void salvarOuSubstituirDocumento(Orcamento orcamento, byte[] conteudoDocumento) {
        DocumentoOrcamento documento =
                documentoOrcamentoRepository
                        .findById(orcamento.getId())
                        .orElseGet(DocumentoOrcamento::new);
        documento.setOrcamento(orcamento);
        documento.setContent(conteudoDocumento);
        documentoOrcamentoRepository.saveAndFlush(documento);
    }
}
