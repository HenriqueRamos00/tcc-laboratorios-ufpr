package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorDetalheResponse;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultarAdministradoresUseCase {
    private final UsuarioRepository usuarios;

    public List<AdministradorDetalheResponse> listar() {
        return usuarios.findAllByAdministradorServicoTrueOrderByNomeAscIdAsc().stream().map(AdministradorDetalheResponse::fromEntity).toList();
    }

    public AdministradorDetalheResponse obter(Long id) {
        return AdministradorDetalheResponse.fromEntity(usuarios.findByIdAndAdministradorServicoTrue(id).orElseThrow(ContaNaoEncontradaException::new));
    }
}
