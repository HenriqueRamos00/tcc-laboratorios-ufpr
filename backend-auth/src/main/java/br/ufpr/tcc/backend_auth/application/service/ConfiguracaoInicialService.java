package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarAdministradorRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorResponse;
import br.ufpr.tcc.backend_auth.application.usecase.RegistrarPrimeiroAdministradorUseCase;
import br.ufpr.tcc.backend_auth.domain.exception.ConfiguracaoInicialException;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ConfiguracaoInicialService {
    private final UsuarioRepository usuarios;
    private final RegistrarPrimeiroAdministradorUseCase registrar;
    private final String token;

    public ConfiguracaoInicialService(UsuarioRepository usuarios, RegistrarPrimeiroAdministradorUseCase registrar) {
        this.usuarios = usuarios;
        this.registrar = registrar;
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public synchronized Optional<String> url(String baseUrl) {
        if (usuarios.existsByAdministradorServicoTrue()) {
            return Optional.empty();
        }
        return Optional.of(baseUrl.replaceAll("/+$", "") + "/setup?token=" + token);
    }

    public synchronized void validarAcesso(String suppliedToken) {
        if (usuarios.existsByAdministradorServicoTrue()) {
            throw new ConfiguracaoInicialException(true);
        }
        if (suppliedToken == null || suppliedToken.length() != token.length()
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ConfiguracaoInicialException(false);
        }
    }

    // O monitor é liberado depois do commit, impedindo dois cadastros pelo mesmo link.
    public synchronized AdministradorResponse concluir(String suppliedToken, CriarAdministradorRequest request) {
        validarAcesso(suppliedToken);
        return registrar.execute(request);
    }
}
