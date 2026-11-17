package br.ufpr.tcc.auth.credential;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.DatabaseProbe;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import br.ufpr.tcc.auth.credential.model.ServiceToken;
import br.ufpr.tcc.auth.shared.database.OracleIntegrationTestBase;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prova a regra 6 do Lactec na camada de dados: so aplicacao registrada e ativa chama a
 * API, e a situacao e lida junto com a credencial.
 *
 * Os quatro motivos de recusa precisam ser indistinguiveis para quem chama. Aqui eles sao
 * provados um a um como ausencia de resultado, que e a unica forma de a camada de cima nao
 * conseguir diferenciar nem por acidente.
 */
@Transactional
class ApplicationTokenDirectoryIntegrationTest extends OracleIntegrationTestBase {

    @Autowired
    private ApplicationTokenDirectory directory;

    @Autowired
    private DatabaseProbe database;

    /**
     * Identificador de uma sequencia propria da classe, e nao derivado do relogio nem do
     * resumo do codigo: as duas formas anteriores podiam colidir com outra classe, e quem
     * salvava disso era apenas o @Transactional desta, que e protecao por acidente.
     */
    private static final AtomicLong NEXT =
            new AtomicLong(970_000L);

    private long createApplication(String code, boolean active) {
        long id = NEXT.incrementAndGet();
        database.statement("INSERT INTO APPLICATION (ID, CODE, NAME, ACTIVE, CREATED_AT, UPDATED_AT)"
                        + " VALUES (:id, :code, :name, :active, :now, :now)")
                .with("id", id).with("code", code).with("name", "Aplicacao " + code)
                .with("active", active).with("now", OffsetDateTime.now()).run();
        return id;
    }

    private ServiceToken issueToken(long applicationId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        ServiceToken token = ServiceToken.generate();
        database.statement("INSERT INTO APPLICATION_TOKEN"
                        + " (ID, APPLICATION_ID, NAME, TOKEN_DIGEST, CREATED_AT, EXPIRES_AT, REVOKED_AT)"
                        + " VALUES (:id, :applicationId, :name, :digest, :now, :expiresAt, :revokedAt)")
                .with("id", NEXT.incrementAndGet())
                .with("applicationId", applicationId).with("name", "token de teste")
                .with("digest", token.digest().asBytes()).with("now", OffsetDateTime.now())
                .with("expiresAt", expiresAt).with("revokedAt", revokedAt).run();
        return token;
    }

    @Test
    @DisplayName("token valido de aplicacao ativa identifica a aplicacao")
    void validTokenOfActiveApplicationIdentifiesTheCaller() {
        long applicationId = createApplication("portal-valido", true);
        ServiceToken token = issueToken(applicationId, null, null);

        assertThat(directory.resolve(token.clearValue()))
                .get()
                .satisfies(caller -> {
                    assertThat(caller.applicationId()).isEqualTo(applicationId);
                    assertThat(caller.applicationCode()).isEqualTo("portal-valido");
                });
    }

    @Test
    @DisplayName("token malformado nao identifica ninguem, e nao estoura")
    void malformedTokenResolvesToNobody() {
        assertThat(directory.resolve("nao-e-um-token")).isEmpty();
        assertThat(directory.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("token desconhecido nao identifica ninguem")
    void unknownTokenResolvesToNobody() {
        assertThat(directory.resolve(ServiceToken.generate().clearValue())).isEmpty();
    }

    @Test
    @DisplayName("token revogado nao identifica ninguem")
    void revokedTokenResolvesToNobody() {
        long applicationId = createApplication("portal-revogado", true);
        ServiceToken token = issueToken(applicationId, null, OffsetDateTime.now().minusMinutes(1));

        assertThat(directory.resolve(token.clearValue())).isEmpty();
    }

    @Test
    @DisplayName("token vencido nao identifica ninguem")
    void expiredTokenResolvesToNobody() {
        long applicationId = createApplication("portal-vencido", true);
        ServiceToken token = issueToken(applicationId, OffsetDateTime.now().minusMinutes(1), null);

        assertThat(directory.resolve(token.clearValue())).isEmpty();
    }

    // Desativar a aplicacao corta o acesso dela na hora, sem revogar token um a um e
    // sem esperar vencimento, porque a situacao e lida junto com a credencial.
    @Test
    @DisplayName("aplicacao desativada nao identifica ninguem, mesmo com token valido")
    void tokenOfDeactivatedApplicationResolvesToNobody() {
        long applicationId = createApplication("portal-desativado", false);
        ServiceToken token = issueToken(applicationId, null, null);

        assertThat(directory.resolve(token.clearValue())).isEmpty();
    }
}
