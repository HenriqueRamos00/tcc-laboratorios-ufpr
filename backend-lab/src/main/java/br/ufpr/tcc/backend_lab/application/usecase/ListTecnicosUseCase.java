package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.TecnicoStatus;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListTecnicosUseCase {
	private final UsuarioRepository usuarioRepository;

	@Transactional(readOnly = true)
	public List<TecnicoResponse> execute(String search, TecnicoStatus status) {
		String termo = search == null || search.isBlank() ? null : search.trim().toLowerCase();
		Boolean ativo = status == null ? null : status.toAtivo();
		return usuarioRepository.pesquisarTecnicos(PerfilUsuario.TECNICO, termo, ativo)
				.stream().map(TecnicoResponse::fromEntity).toList();
	}
}
