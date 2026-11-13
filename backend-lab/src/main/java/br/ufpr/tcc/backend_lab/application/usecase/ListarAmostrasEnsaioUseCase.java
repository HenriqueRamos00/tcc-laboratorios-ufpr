package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarAmostrasEnsaioUseCase {
    private final AmostraRepository amostras;
    private final OrcamentoRepository orcamentos;
    public ListarAmostrasEnsaioUseCase(AmostraRepository amostras, OrcamentoRepository orcamentos) {
        this.amostras = amostras;
        this.orcamentos = orcamentos;
    }
    @Transactional(readOnly=true)
    public EnsaioDtos.Pagina<EnsaioDtos.AmostraRef> executar(String quoteId, String busca, int pagina, int tamanho) {
        ListarEnsaiosUseCase.validarPagina(pagina, tamanho);
        Long idOrcamento = quoteId == null ? null : ResolvedorReferenciasEnsaio.id(quoteId);
        if (idOrcamento != null && !orcamentos.existsById(idOrcamento)) {
            throw new EnsaioNaoEncontradoException("Orçamento não encontrado");
        }
        String termo = busca == null ? "" : busca.trim();
        var resultado = amostras.pesquisar(ListarEnsaiosUseCase.escaparLike(termo.toLowerCase(Locale.ROOT)),
                idOrcamento,
                PageRequest.of(pagina - 1, tamanho, Sort.by(Sort.Direction.ASC, "codigo").and(Sort.by("id"))));
        return new EnsaioDtos.Pagina<>(resultado.getContent().stream().map(EnsaioDtos::amostra).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }
}
