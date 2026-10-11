package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResponse;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ParametroLikeJpa;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListClientesUseCase {
    private final ClienteRepository repository;

    @Transactional(readOnly = true)
    public List<ClienteResponse> execute(String search, CadastroStatus status) {
        return repository
                .pesquisar(
                        ParametroLikeJpa.buscaParcial(search),
                        status == null ? null : status.toAtivo())
                .stream()
                .map(ClienteResponse::from)
                .toList();
    }
}
