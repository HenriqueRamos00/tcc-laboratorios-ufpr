package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginacaoCliente {
    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 100;

    private PaginacaoCliente() { }

    public static void validar(int pagina, int tamanho) {
        if (pagina < 1 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw RegraNegocioException.dadosInvalidos("Paginação inválida");
        }
    }

    public static Pageable equipamentos(int pagina, int tamanho) {
        return PageRequest.of(pagina - 1, tamanho,
                Sort.by(Sort.Direction.ASC, "id"));
    }

    public static Pageable historico(int pagina, int tamanho) {
        return PageRequest.of(pagina - 1, tamanho,
                Sort.by(Sort.Order.desc("publishedAt").nullsLast(),
                        Sort.Order.desc("id")));
    }

    public static int tamanhoPadrao() {
        return TAMANHO_PADRAO;
    }
}
