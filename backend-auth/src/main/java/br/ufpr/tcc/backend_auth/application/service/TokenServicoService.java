package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarTokenServicoRequest;
import br.ufpr.tcc.backend_auth.application.dto.response.TokenServicoEmitidoResponse;
import br.ufpr.tcc.backend_auth.application.dto.response.TokenServicoResponse;
import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.exception.RecursoNaoEncontradoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.TokenServico;
import br.ufpr.tcc.backend_auth.domain.repository.TokenServicoRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenServicoService {
    private final TokenServicoRepository tokens;
    private final ServicoService servicos;
    private final long expirationSeconds;

    public TokenServicoService(TokenServicoRepository tokens, ServicoService servicos,
            @Value("${service.token.expiration-seconds:2592000}") long expirationSeconds) {
        if (expirationSeconds <= 0 || expirationSeconds > 31536000) {
            throw new IllegalArgumentException("A validade do token de serviço deve estar entre 1 e 31536000 segundos");
        }
        this.tokens = tokens;
        this.servicos = servicos;
        this.expirationSeconds = expirationSeconds;
    }

    @Transactional
    public TokenServicoEmitidoResponse emitir(Long servicoId, CriarTokenServicoRequest request) {
        servicos.validar(request);
        var servico = servicos.exigirAtivo(servicoId);
        long validade = request.expiresIn() == null ? expirationSeconds : request.expiresIn();
        String segredo = "svc_" + TokenOpaco.gerar();
        TokenServico token = new TokenServico();
        token.setServico(servico);
        token.setNome(request.name());
        token.setTokenHash(TokenOpaco.hash(segredo));
        token.setCriadoEm(Instant.now());
        token.setExpiraEm(token.getCriadoEm().plusSeconds(validade));
        tokens.saveAndFlush(token);
        return new TokenServicoEmitidoResponse(segredo, "Bearer", validade, TokenServicoResponse.fromEntity(token));
    }

    @Transactional(readOnly = true)
    public List<TokenServicoResponse> listar(Long servicoId) {
        servicos.obter(servicoId);
        return tokens.findAllByServicoIdOrderByCriadoEmDescIdDesc(servicoId).stream().map(TokenServicoResponse::fromEntity).toList();
    }

    @Transactional
    public void revogar(Long servicoId, Long tokenId) {
        servicos.bloquear(servicoId);
        var token = tokens.findByIdAndServicoId(tokenId, servicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Token não encontrado neste serviço"));
        tokens.delete(token);
        tokens.flush();
    }

    @Transactional(readOnly = true)
    public Long autenticar(String segredo) {
        if (segredo == null || !segredo.matches("svc_[A-Za-z0-9_-]{43}")) throw new CredenciaisInvalidasException();
        var token = tokens.findByTokenHash(TokenOpaco.hash(segredo)).orElseThrow(CredenciaisInvalidasException::new);
        if (!token.getExpiraEm().isAfter(Instant.now()) || !token.getServico().isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
        return token.getServico().getId();
    }
}
