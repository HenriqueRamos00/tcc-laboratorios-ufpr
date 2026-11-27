package br.ufpr.tcc.auth.account.usecase;

import java.time.OffsetDateTime;

/** Troca de e-mail aguardando confirmacao, com o que cada endereco ja confirmou. */
public record PendingEmailChange(String newEmail, OffsetDateTime expiresAt,
        boolean currentEmailConfirmed, boolean newEmailConfirmed) {
}
