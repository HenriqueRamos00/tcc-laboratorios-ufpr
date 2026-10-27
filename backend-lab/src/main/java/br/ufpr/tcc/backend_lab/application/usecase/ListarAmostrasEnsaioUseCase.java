package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarAmostrasEnsaioUseCase {
    private final AmostraRepository amostras;
    public ListarAmostrasEnsaioUseCase(AmostraRepository amostras) { this.amostras = amostras; }
    @Transactional(readOnly=true)
    public EnsaioDtos.Pagina<EnsaioDtos.AmostraRef> executar(String busca, int pagina, int tamanho) {
        ListarEnsaiosUseCase.validarPagina(pagina, tamanho);
        String termo = busca == null ? "" : busca.trim();
        var resultado = amostras.pesquisar(ListarEnsaiosUseCase.escaparLike(termo),
                PageRequest.of(pagina - 1, tamanho, Sort.by(Sort.Direction.ASC, "codigo").and(Sort.by("id"))));
        return new EnsaioDtos.Pagina<>(resultado.getContent().stream().map(EnsaioDtos::amostra).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }
}
