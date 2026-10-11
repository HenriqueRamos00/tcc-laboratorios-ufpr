package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.EmpresaResponse;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ParametroLikeJpa;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ListEmpresasUseCase {
    private static final Pattern BUSCA_CNPJ = Pattern.compile("[0-9./-]+");

    private final EmpresaRepository repository;

    @Transactional(readOnly = true)
    public List<EmpresaResponse> execute(String search, CadastroStatus status) {
        String termoBusca =
                search != null && BUSCA_CNPJ.matcher(search).matches()
                        ? search.replaceAll("[^0-9]", "")
                        : search;
        return repository
                .pesquisar(
                        ParametroLikeJpa.buscaParcial(termoBusca),
                        status == null ? null : status.toAtivo())
                .stream()
                .map(EmpresaResponse::from)
                .toList();
    }
}
