package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoInternoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.PaginaOrcamentosResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ParametroLikeJpa;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListOrcamentosUseCase {
    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final long OFFSET_MAXIMO_REPOSITORIO = Integer.MAX_VALUE;

    private final OrcamentoRepository orcamentoRepository;
    private final DocumentoOrcamentoRepository documentoOrcamentoRepository;
    //TODO: verificar se é possível remover essa lógica  de paginação do usecase
    @Transactional(readOnly = true)
    public PaginaOrcamentosResponse execute(String search, String status, int page, int pageSize) {
        long offset = page > 0 && pageSize > 0 ? (long) (page - 1) * pageSize : -1;
        if (page < 1
                || pageSize < 1
                || pageSize > TAMANHO_MAXIMO_PAGINA
                || offset > OFFSET_MAXIMO_REPOSITORIO) {
            throw RegraNegocioException.dadosInvalidos(
                    "Página inválida ou tamanho da página fora do intervalo de 1 a 100");
        }
        if (status != null
                && !status.isBlank()
                && StatusComercialOrcamento.fromCodigo(status).isEmpty()) {
            throw RegraNegocioException.dadosInvalidos("Status de orçamento inválido");
        }

        String termoBusca = ParametroLikeJpa.buscaParcial(search);
        StatusComercialOrcamento filtroStatus =
                status == null || status.isBlank()
                        ? null
                        : StatusComercialOrcamento.fromCodigo(status).orElseThrow();
        var pageable =
                PageRequest.of(
                        page - 1,
                        pageSize,
                        Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id")));
        var resultado = orcamentoRepository.pesquisarInternos(termoBusca, filtroStatus, pageable);
        var idsPagina = resultado.getContent().stream().map(orcamento -> orcamento.getId()).toList();
        var idsComDocumento =
                idsPagina.isEmpty()
                        ? Set.<Long>of()
                        : documentoOrcamentoRepository.buscarIdsOrcamentosComDocumento(idsPagina)
                                .stream()
                                .collect(Collectors.toSet());
        var items =
                resultado.getContent().stream()
                        .map(
                                orcamento ->
                                        OrcamentoInternoResponse.from(
                                                orcamento,
                                                idsComDocumento.contains(orcamento.getId())))
                        .toList();
        return new PaginaOrcamentosResponse(items, page, pageSize, resultado.getTotalElements());
    }
}
