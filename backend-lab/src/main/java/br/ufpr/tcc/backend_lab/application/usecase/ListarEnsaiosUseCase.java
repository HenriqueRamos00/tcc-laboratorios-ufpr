package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarEnsaiosUseCase {
    private static final int MAX_TAMANHO_PAGINA = 100;
    private final ExecucaoEnsaioRepository ensaios;
    public ListarEnsaiosUseCase(ExecucaoEnsaioRepository ensaios) { this.ensaios = ensaios; }
    @Transactional(readOnly=true)
    public EnsaioDtos.Pagina<EnsaioDtos.Resumo> executar(String busca, int pagina, int tamanho) {
        validarPagina(pagina, tamanho);
        String termo = busca == null ? "" : busca.trim();
        var resultado = ensaios.pesquisar(escaparLike(termo),
                PageRequest.of(pagina - 1, tamanho, Sort.by(Sort.Direction.DESC, "id")));
        return new EnsaioDtos.Pagina<>(resultado.getContent().stream().map(EnsaioDtos::resumo).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }
    static void validarPagina(int pagina, int tamanho) {
        if (pagina < 1 || tamanho < 1 || tamanho > MAX_TAMANHO_PAGINA) {
            throw new DadosEnsaioInvalidosException("Paginação inválida");
        }
    }
    static String escaparLike(String valor) {
        return valor.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
