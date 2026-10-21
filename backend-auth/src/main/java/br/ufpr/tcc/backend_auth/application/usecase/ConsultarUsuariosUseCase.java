package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.response.UsuarioDetalheResponse;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultarUsuariosUseCase {
    private final UsuarioRepository usuarios;

    public List<UsuarioDetalheResponse> listar() {
        return usuarios.findAllByPerfilIsNotNullOrderByNomeAscIdAsc().stream().map(UsuarioDetalheResponse::fromEntity).toList();
    }

    public UsuarioDetalheResponse obter(Long id) {
        return UsuarioDetalheResponse.fromEntity(usuarios.findByIdAndPerfilIsNotNull(id).orElseThrow(ContaNaoEncontradaException::new));
    }
}
