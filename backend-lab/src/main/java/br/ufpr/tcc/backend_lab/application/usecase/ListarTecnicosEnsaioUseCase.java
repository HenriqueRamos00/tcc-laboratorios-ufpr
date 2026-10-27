package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarTecnicosEnsaioUseCase {
    private final UsuarioRepository usuarios;
    public ListarTecnicosEnsaioUseCase(UsuarioRepository usuarios) { this.usuarios = usuarios; }
    @Transactional(readOnly=true)
    public List<EnsaioDtos.TecnicoRef> executar() {
        return usuarios.pesquisarTecnicos(PerfilUsuario.TECNICO, null, true).stream()
                .map(usuario -> new EnsaioDtos.TecnicoRef(usuario.getId().toString(), usuario.getNome(), usuario.getUnidade()))
                .toList();
    }
}
