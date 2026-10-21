package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.domain.exception.LoginBloqueadoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BloqueioLoginClienteService {
    private static final int MAX_TENTATIVAS = 5;
    private final long cooldownSeconds;

    public BloqueioLoginClienteService(@Value("${client.login.cooldown-seconds:900}") long cooldownSeconds) {
        if (cooldownSeconds <= 0 || cooldownSeconds > 86400) {
            throw new IllegalArgumentException("O período de espera de login deve estar entre 1 e 86400 segundos");
        }
        this.cooldownSeconds = cooldownSeconds;
    }

    public void verificar(Usuario usuario, Instant agora) {
        if (usuario.getLoginBloqueadoAte() != null) {
            if (usuario.getLoginBloqueadoAte().isAfter(agora)) {
                long millis = Duration.between(agora, usuario.getLoginBloqueadoAte()).toMillis();
                throw new LoginBloqueadoException(Math.max(1, (millis + 999) / 1000));
            }
            limpar(usuario);
        }
    }

    public void registrarFalha(Usuario usuario, Instant agora) {
        usuario.setFalhasLogin(usuario.getFalhasLogin() + 1);
        if (usuario.getFalhasLogin() >= MAX_TENTATIVAS) {
            usuario.setLoginBloqueadoAte(agora.plusSeconds(cooldownSeconds));
            throw new LoginBloqueadoException(cooldownSeconds);
        }
    }

    public void limpar(Usuario usuario) {
        usuario.setFalhasLogin(0);
        usuario.setLoginBloqueadoAte(null);
    }
}
