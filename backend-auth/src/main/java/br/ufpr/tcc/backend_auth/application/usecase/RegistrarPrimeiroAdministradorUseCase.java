package br.ufpr.tcc.backend_auth.application.usecase;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorResponse;
import br.ufpr.tcc.backend_auth.domain.exception.ConfiguracaoInicialException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrarPrimeiroAdministradorUseCase {
    private final UsuarioRepository usuarios;
    private final CriarAdministradorUseCase criarAdministrador;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AdministradorResponse execute(CriarAdministradorRequest request) {
        if (usuarios.existsByAdministradorServicoTrue()) {
            throw new ConfiguracaoInicialException(true);
        }
        return criarAdministrador.execute(request);
    }
}
