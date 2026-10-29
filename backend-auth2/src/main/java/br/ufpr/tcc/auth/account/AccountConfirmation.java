package br.ufpr.tcc.auth.account;

import br.ufpr.tcc.auth.credential.model.ConfirmationToken;
import br.ufpr.tcc.auth.notification.OutboundMessage;
import br.ufpr.tcc.auth.shared.identifier.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Credencial de uso unico que vai no e-mail, para confirmar o cadastro ou para trocar a
 * senha esquecida.
 *
 * O que viaja na mensagem e segredo e o que fica gravado e o digest dele, pela mesma regra
 * do token de aplicacao: quem perde o e-mail pede outro, e ninguem recupera o anterior.
 *
 * O proposito faz parte do que e verificado, e nao e etiqueta: um link de confirmacao de
 * cadastro chega a quem acabou de se inscrever, e ele nao pode trocar a senha de ninguem.
 *
 * O indice unico parcial UX_ACCOUNT_CONFIRMATION_PENDING, que garante no maximo um link
 * pendente por conta e por proposito, nao e declarado aqui: ele e indice de funcao, e a
 * conferencia de esquema do Hibernate so enxerga o que esta mapeado. Quem o descreve e a
 * migracao V008.
 */
@Entity
@Table(name = "ACCOUNT_CONFIRMATION",
        uniqueConstraints = @UniqueConstraint(name = "UK_ACCOUNT_CONFIRMATION_DIGEST",
                columnNames = "TOKEN_DIGEST"),
        indexes = @Index(name = "IX_ACCOUNT_CONFIRMATION_ACCOUNT", columnList = "CLIENT_ACCOUNT_ID"))
class AccountConfirmation {

    @Id
    @Tsid
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLIENT_ACCOUNT_ID")
    private Long clientAccountId;

    @Column(name = "PURPOSE", length = 20)
    @Enumerated(EnumType.STRING)
    private OutboundMessage.Purpose purpose;

    @Column(name = "TOKEN_DIGEST", length = 32)
    private byte[] tokenDigest;

    @Column(name = "CREATED_AT")
    private OffsetDateTime createdAt;

    @Column(name = "EXPIRES_AT")
    private OffsetDateTime expiresAt;

    @Column(name = "USED_AT")
    private OffsetDateTime usedAt;

    protected AccountConfirmation() {
    }

    static AccountConfirmation issuedFor(long clientAccountId, ConfirmationToken token,
            OutboundMessage.Purpose purpose, Duration lifetime) {
        AccountConfirmation confirmation = new AccountConfirmation();
        OffsetDateTime now = OffsetDateTime.now();
        confirmation.clientAccountId = clientAccountId;
        confirmation.purpose = purpose;
        confirmation.tokenDigest = token.digest().asBytes();
        confirmation.createdAt = now;
        confirmation.expiresAt = now.plus(lifetime);
        return confirmation;
    }
}
