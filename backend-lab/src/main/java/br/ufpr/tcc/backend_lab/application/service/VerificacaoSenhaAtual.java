package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.LimiteSenhaAtualException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaAtualIncorretaException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificacaoSenhaAtual {
    private static final int MAX_FALHAS = 5;
    private static final int LIMITE_BYTES_BCRYPT = 72;
    private static final Duration JANELA = Duration.ofMinutes(15);
    private final PasswordEncoder passwordEncoder;

    public void verificar(Usuario usuarioBloqueado, String senhaAtual) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime fim = usuarioBloqueado.getLimiteSenhaAtualAte();
        int falhas = fim != null && fim.isAfter(agora) ? usuarioBloqueado.getFalhasSenhaAtual() : 0;
        if (falhas >= MAX_FALHAS) {
            throw new LimiteSenhaAtualException(Duration.between(agora, fim));
        }
        if (senhaAtual != null && senhaAtual.length() <= LIMITE_BYTES_BCRYPT
                && senhaAtual.getBytes(StandardCharsets.UTF_8).length <= LIMITE_BYTES_BCRYPT
                && passwordEncoder.matches(senhaAtual, usuarioBloqueado.getSenhaHash())) {
            usuarioBloqueado.setFalhasSenhaAtual(0);
            usuarioBloqueado.setLimiteSenhaAtualAte(null);
            return;
        }
        if (falhas == 0) {
            usuarioBloqueado.setLimiteSenhaAtualAte(agora.plus(JANELA));
        }
        usuarioBloqueado.setFalhasSenhaAtual(falhas + 1);
        throw new SenhaAtualIncorretaException();
    }
}
