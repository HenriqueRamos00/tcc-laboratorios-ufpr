package br.ufpr.tcc.backend_lab.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alteracoes_email", indexes = {
        @Index(name = "uk_alteracoes_email_token_atual", columnList = "token_atual_hash", unique = true),
        @Index(name = "uk_alteracoes_email_token_novo", columnList = "token_novo_hash", unique = true)
})
@Getter
@NoArgsConstructor
public class AlteracaoEmail {
    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "email_novo", length = 254)
    private String emailNovo;

    @Column(name = "token_atual_hash", length = 64)
    private String tokenAtualHash;

    @Column(name = "token_novo_hash", length = 64)
    private String tokenNovoHash;

    @Column(name = "expira_em")
    private OffsetDateTime expiraEm;

    @Column(name = "email_atual_confirmado", nullable = false)
    private boolean emailAtualConfirmado;

    @Column(name = "email_novo_confirmado", nullable = false)
    private boolean emailNovoConfirmado;

    @Column(name = "pendente", nullable = false)
    private boolean pendente;

    @Column(name = "inicio_janela_solicitacoes")
    private OffsetDateTime inicioJanelaSolicitacoes;

    @Column(name = "quantidade_solicitacoes", nullable = false)
    private int quantidadeSolicitacoes;

    public static AlteracaoEmail paraUsuario(Long usuarioId) {
        AlteracaoEmail alteracao = new AlteracaoEmail();
        alteracao.usuarioId = usuarioId;
        return alteracao;
    }

    public Duration registrarSolicitacao(OffsetDateTime agora, int limite, Duration janela) {
        if (inicioJanelaSolicitacoes == null
                || !agora.isBefore(inicioJanelaSolicitacoes.plus(janela))) {
            inicioJanelaSolicitacoes = agora;
            quantidadeSolicitacoes = 0;
        }
        if (quantidadeSolicitacoes >= limite) {
            return Duration.between(agora, inicioJanelaSolicitacoes.plus(janela));
        }
        quantidadeSolicitacoes++;
        return Duration.ZERO;
    }

    public void iniciar(String novoEmail, String hashTokenAtual, String hashTokenNovo,
            OffsetDateTime expiracao) {
        emailNovo = novoEmail;
        tokenAtualHash = hashTokenAtual;
        tokenNovoHash = hashTokenNovo;
        expiraEm = expiracao;
        emailAtualConfirmado = false;
        emailNovoConfirmado = false;
        pendente = true;
    }

    public boolean estaPendenteEm(OffsetDateTime agora) {
        return pendente && expiraEm != null && expiraEm.isAfter(agora);
    }

    public boolean confirmarToken(String hashToken) {
        if (tokenAtualHash != null && tokenAtualHash.equals(hashToken)) {
            tokenAtualHash = null;
            emailAtualConfirmado = true;
            return true;
        }
        if (tokenNovoHash != null && tokenNovoHash.equals(hashToken)) {
            tokenNovoHash = null;
            emailNovoConfirmado = true;
            return true;
        }
        return false;
    }

    public void cancelar() {
        emailNovo = null;
        tokenAtualHash = null;
        tokenNovoHash = null;
        expiraEm = null;
        emailAtualConfirmado = false;
        emailNovoConfirmado = false;
        pendente = false;
    }
}
