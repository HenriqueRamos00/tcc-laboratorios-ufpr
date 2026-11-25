package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.MeuPerfilResponse.PendingEmailChangeResponse;
import br.ufpr.tcc.backend_lab.application.service.VerificacaoSenhaAtual;
import br.ufpr.tcc.backend_lab.domain.exception.AlteracaoEmailIndisponivelException;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoAlteracaoEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaUtilizadoException;
import br.ufpr.tcc.backend_lab.domain.exception.FalhaEnvioEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.LimiteAlteracaoEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.PerfilInvalidoException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaAtualIncorretaException;
import br.ufpr.tcc.backend_lab.domain.exception.TokenAlteracaoEmailInvalidoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.AlteracaoEmail;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.AlteracaoEmailRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.config.EmailChangeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailChangeService {
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AlteracaoEmailRepository alteracoes;
    private final UsuarioRepository usuarios;
    private final VerificacaoSenhaAtual verificacaoSenhaAtual;
    private final JavaMailSender mailSender;
    private final EmailChangeProperties emailProperties;
    private final String smtpHost;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String senderAddress;

    public EmailChangeService(AlteracaoEmailRepository alteracoes, UsuarioRepository usuarios,
            VerificacaoSenhaAtual verificacaoSenhaAtual,
            ObjectProvider<JavaMailSender> mailSender,
            EmailChangeProperties emailProperties,
            Environment environment) {
        this.alteracoes = alteracoes;
        this.usuarios = usuarios;
        this.verificacaoSenhaAtual = verificacaoSenhaAtual;
        this.mailSender = mailSender.getIfAvailable();
        this.emailProperties = emailProperties;
        this.smtpHost = environment.getProperty("spring.mail.host", "");
        this.smtpUsername = environment.getProperty("spring.mail.username", "");
        this.smtpPassword = environment.getProperty("spring.mail.password", "");
        this.senderAddress = environment.getProperty("spring.mail.from", smtpUsername);
    }

    public boolean disponivelPara(PerfilUsuario perfil) {
        String link = linkPara(perfil);
        return !smtpHost.isBlank()
                && !smtpUsername.isBlank()
                && !smtpPassword.isBlank()
                && senderAddress != null && !senderAddress.isBlank()
                && mailSender != null
                && linkValido(link);
    }

    @Transactional(noRollbackFor = SenhaAtualIncorretaException.class)
    public PendingEmailChangeResponse solicitar(Long usuarioId, String novoEmail, String senhaAtual) {
        Usuario usuario = usuarioParaAtualizacao(usuarioId);
        exigirDisponibilidade(usuario.getPerfil());

        String emailNormalizado = normalizarEmail(novoEmail);
        if (emailNormalizado.equals(usuario.getEmail().trim().toLowerCase(Locale.ROOT))) {
            throw new PerfilInvalidoException("Informe um e-mail diferente do atual.");
        }
        verificacaoSenhaAtual.verificar(usuario, senhaAtual);
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailJaUtilizadoException();
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        AlteracaoEmail alteracao = alteracoes.encontrarPorUsuarioParaAtualizacao(usuarioId)
                .orElseGet(() -> AlteracaoEmail.paraUsuario(usuarioId));
        Duration tempoRestante = alteracao.registrarSolicitacao(
                agora, MAX_REQUESTS_PER_WINDOW, REQUEST_WINDOW);
        if (!tempoRestante.isZero()) {
            throw new LimiteAlteracaoEmailException(tempoRestante);
        }

        String tokenAtual = novoToken();
        String tokenNovo = novoToken();
        alteracao.iniciar(emailNormalizado, hashToken(tokenAtual), hashToken(tokenNovo),
                agora.plus(TOKEN_TTL));
        alteracoes.saveAndFlush(alteracao);

        enviar(usuario.getEmail(), tokenAtual, usuario.getPerfil(), false);
        enviar(emailNormalizado, tokenNovo, usuario.getPerfil(), true);
        return respostaPendente(alteracao);
    }

    @Transactional
    public void cancelar(Long usuarioId) {
        usuarioParaAtualizacao(usuarioId);
        invalidarPendente(usuarioId);
    }

    @Transactional
    public void invalidar(Long usuarioId) {
        invalidarPendente(usuarioId);
    }

    private void invalidarPendente(Long usuarioId) {
        alteracoes.encontrarPorUsuarioParaAtualizacao(usuarioId)
                .filter(AlteracaoEmail::isPendente)
                .ifPresent(alteracao -> {
                    alteracao.cancelar();
                    alteracoes.saveAndFlush(alteracao);
                });
    }

    @Transactional(readOnly = true)
    public PendingEmailChangeResponse pendente(Long usuarioId) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        return alteracoes.findById(usuarioId)
                .filter(alteracao -> alteracao.estaPendenteEm(agora))
                .map(EmailChangeService::respostaPendente)
                .orElse(null);
    }

    @Transactional
    public String confirmar(String tokenApresentado) {
        String hash = hashToken(tokenApresentado);
        Long usuarioId = alteracoes.encontrarUsuarioPorToken(hash)
                .orElseThrow(TokenAlteracaoEmailInvalidoException::new);
        Usuario usuario = usuarioParaAtualizacao(usuarioId);
        AlteracaoEmail alteracao = alteracoes.encontrarPorTokenParaAtualizacao(hash)
                .orElseThrow(TokenAlteracaoEmailInvalidoException::new);
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (!alteracao.estaPendenteEm(agora) || !alteracao.confirmarToken(hash)) {
            throw new TokenAlteracaoEmailInvalidoException();
        }

        if (!alteracao.isEmailAtualConfirmado() || !alteracao.isEmailNovoConfirmado()) {
            alteracoes.saveAndFlush(alteracao);
            return "awaiting-other-confirmation";
        }

        if (usuarios.existsByEmailIgnoreCase(alteracao.getEmailNovo())) {
            throw new EmailJaUtilizadoException();
        }
        usuario.setEmail(alteracao.getEmailNovo());
        usuario.invalidateCredentials();
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException conflito) {
            throw new EmailJaUtilizadoException();
        }
        alteracao.cancelar();
        alteracoes.saveAndFlush(alteracao);
        return "completed";
    }

    private Usuario usuarioParaAtualizacao(Long usuarioId) {
        return usuarios.findByIdForUpdate(usuarioId).filter(Usuario::isAtivo)
                .orElseThrow(ConflitoAlteracaoEmailException::new);
    }

    private void exigirDisponibilidade(PerfilUsuario perfil) {
        if (!disponivelPara(perfil)) {
            throw new AlteracaoEmailIndisponivelException();
        }
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new PerfilInvalidoException("Informe um endereço de e-mail válido.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void enviar(String destinatario, String token, PerfilUsuario perfil, boolean novoEndereco) {
        if (mailSender == null) {
            throw new AlteracaoEmailIndisponivelException();
        }
        String link = linkPara(perfil).replace("{token}", token);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(destinatario);
        message.setSubject("Confirme a alteração do seu e-mail");
        message.setText((novoEndereco
                ? "Foi solicitado o uso deste endereço como novo e-mail de acesso."
                : "Foi solicitada uma alteração do e-mail de acesso desta conta.")
                + "\n\nConfirme a solicitação neste link, válido por uma hora:\n" + link
                + "\n\nSe você não solicitou essa alteração, ignore esta mensagem.");
        try {
            mailSender.send(message);
        } catch (MailException falha) {
            throw new FalhaEnvioEmailException();
        }
    }

    private String linkPara(PerfilUsuario perfil) {
        return perfil == PerfilUsuario.CLIENTE
                ? emailProperties.getClientLink() : emailProperties.getInternalLink();
    }

    private static boolean linkValido(String link) {
        if (link == null || !link.contains("{token}")) return false;
        try {
            URI uri = URI.create(link.replace("{token}", "token-de-teste"));
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException falha) {
            return false;
        }
    }

    private static String novoToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new TokenAlteracaoEmailInvalidoException();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException("SHA-256 não está disponível", impossivel);
        }
    }

    private static PendingEmailChangeResponse respostaPendente(AlteracaoEmail alteracao) {
        return new PendingEmailChangeResponse(alteracao.getEmailNovo(), alteracao.getExpiraEm(),
                alteracao.isEmailAtualConfirmado(), alteracao.isEmailNovoConfirmado());
    }
}
