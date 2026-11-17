package br.ufpr.tcc.auth.shared.database;

import br.ufpr.tcc.auth.credential.model.AdministrativeToken;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abre uma sessao administrativa direto no banco, para as suites que precisam da credencial
 * do portal administrativo mas nao estao provando a entrada do administrador.
 *
 * Cadastro de conta de cliente e de empresa passou a ser do administrador, entao quase toda
 * suite que prepara conta precisa desta credencial. Passar pela rota de entrada em cada
 * preparacao gastaria a verificacao de senha, que e cara por desenho, e amarraria suites
 * que nada tem a ver com a entrada ao contador de tentativas dela.
 *
 * A faixa de identificadores e propria, longe das faixas que cada suite reserva para si.
 */
public final class AdministrativeSessions {

    private static final AtomicLong NEXT = new AtomicLong(880_000_000L);

    private AdministrativeSessions() {
    }

    /** Cadastra um administrador ativo e devolve o token de uma sessao aberta dele. */
    public static String open(DatabaseProbe database) {
        long administratorId = NEXT.incrementAndGet();
        String email = "admin.suite" + administratorId + "@lactec.exemplo";
        OffsetDateTime now = OffsetDateTime.now();
        database.statement("INSERT INTO ADMINISTRATOR (ID, NAME, EMAIL, EMAIL_NORMALIZED, ACTIVE,"
                        + " PASSWORD_HASH, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, 'Administrador da suite', :email, :email, TRUE,"
                        + " 'forma-guardada-que-nao-autentica', :now, :now)")
                .with("id", administratorId).with("email", email).with("now", now).run();
        AdministrativeToken token = AdministrativeToken.generate();
        database.statement("INSERT INTO ADMINISTRATIVE_SESSION (ID, ADMINISTRATOR_ID,"
                        + " TOKEN_DIGEST, CREATED_AT, EXPIRES_AT)"
                        + " VALUES (:id, :administratorId, :digest, :now, :expiresAt)")
                .with("id", NEXT.incrementAndGet()).with("administratorId", administratorId)
                .with("digest", token.digest().asBytes()).with("now", now)
                .with("expiresAt", now.plusHours(1)).run();
        return token.clearValue();
    }
}
