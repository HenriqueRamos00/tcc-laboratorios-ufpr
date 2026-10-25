package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.dto.response.AdministradorResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.LoginAdministradorResponse;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.model.entity.SessaoAdministrativa;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.SessaoAdministrativaRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenAdministrativoService {
    private final SessaoAdministrativaRepository sessoes;
    private final long expirationSeconds;

    public TokenAdministrativoService(SessaoAdministrativaRepository sessoes,
            @Value("${admin.token.expiration-seconds:3600}") long expirationSeconds) {
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("A validade do token administrativo deve ser positiva");
        }
        this.sessoes = sessoes;
        this.expirationSeconds = expirationSeconds;
    }

    @Transactional
    public LoginAdministradorResponse emitir(Usuario usuario) {
        Instant now = Instant.now();
        sessoes.excluirExpiradas(now);
        String token = TokenOpaco.gerar();
        SessaoAdministrativa sessao = new SessaoAdministrativa();
        sessao.setTokenHash(TokenOpaco.hash(token));
        sessao.setUsuario(usuario);
        sessao.setExpiraEm(now.plusSeconds(expirationSeconds));
        sessoes.saveAndFlush(sessao);
        return new LoginAdministradorResponse(token, "Bearer", expirationSeconds, AdministradorResponse.fromEntity(usuario));
    }

    @Transactional(readOnly = true)
    public AdministradorResponse autenticar(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new CredenciaisInvalidasException();
        }
        SessaoAdministrativa sessao = sessoes.findById(TokenOpaco.hash(token)).orElseThrow(CredenciaisInvalidasException::new);
        Usuario usuario = sessao.getUsuario();
        if (!sessao.getExpiraEm().isAfter(Instant.now()) || !usuario.isAtivo() || !usuario.isAdministradorServico()) {
            throw new CredenciaisInvalidasException();
        }
        return AdministradorResponse.fromEntity(usuario);
    }
}
