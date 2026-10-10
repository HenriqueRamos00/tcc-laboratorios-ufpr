package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import br.ufpr.tcc.backend_lab.domain.exception.TecnicoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeTecnicoStatusUseCase {
	private final UsuarioRepository usuarioRepository;

	@Transactional
	public TecnicoResponse execute(Long id, TecnicoStatus status) {
		var tecnico = usuarioRepository.findById(id)
				.filter(usuario -> usuario.getPerfil() == PerfilUsuario.TECNICO)
				.orElseThrow(() -> new TecnicoNaoEncontradoException(id));
		tecnico.setAtivo(status.toAtivo());
		return TecnicoResponse.fromEntity(usuarioRepository.save(tecnico));
	}
}
