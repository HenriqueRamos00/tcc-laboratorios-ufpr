package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarOrcamentosReferenciaEnsaioUseCase {
    private final OrcamentoRepository orcamentos;

    public ListarOrcamentosReferenciaEnsaioUseCase(OrcamentoRepository orcamentos) {
        this.orcamentos = orcamentos;
    }

    @Transactional(readOnly = true)
    public EnsaioDtos.Pagina<EnsaioDtos.OrcamentoRef> executar(String busca, int pagina, int tamanho) {
        ListarEnsaiosUseCase.validarPagina(pagina, tamanho);
        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        String criterio = termo.isEmpty() ? null : "%" + ListarEnsaiosUseCase.escaparLike(termo) + "%";
        var resultado = orcamentos.pesquisarReferenciasEnsaio(criterio,
                PageRequest.of(pagina - 1, tamanho,
                        Sort.by(Sort.Direction.ASC, "code").and(Sort.by("id"))));
        return new EnsaioDtos.Pagina<>(resultado.getContent().stream()
                .map(EnsaioDtos::orcamento).toList(), pagina, tamanho, resultado.getTotalElements());
    }
}
