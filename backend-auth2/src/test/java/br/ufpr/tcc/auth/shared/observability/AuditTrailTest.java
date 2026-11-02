package br.ufpr.tcc.auth.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import br.ufpr.tcc.auth.shared.security.AdministratorCaller;
import br.ufpr.tcc.auth.shared.security.ApplicationCaller;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A trilha de auditoria lida como quem vai consultar o log depois.
 *
 * O que estes casos protegem e a regra 4 do Lactec nas duas metades: o evento precisa dizer
 * quem fez e o que foi afetado, e precisa nao dizer senha, forma guardada de senha nem valor
 * de token.
 */
class AuditTrailTest {

    private final AuditTrail auditTrail = new AuditTrail();
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void captureLogEvents() {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(AuditTrail.class);
        captured.start();
        logger.addAppender(captured);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void releaseLogEvents() {
        logger.detachAppender(captured);
        captured.stop();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private Map<String, String> keyValuesOfTheOnlyEvent() {
        assertThat(captured.list).hasSize(1);
        return captured.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    @Test
    @DisplayName("evento de administrador diz quem fez, com o identificador da credencial")
    void administratorEventSaysWhoDidIt() {
        publishCaller(new AdministratorCaller(900_001L, "Dono do cadastro"));

        auditTrail.administratorChanged("created", "900002");

        assertThat(keyValuesOfTheOnlyEvent())
                .containsEntry("event", "administrator_created")
                .containsEntry("actor", "administrador:900001")
                .containsEntry("administrator_id", "900002");
    }

    /**
     * Chamada sem credencial tambem e chamada, e tambem e registrada. Sem este caso, uma
     * leitura de contexto vazio derrubaria a requisicao no lugar de registra la.
     */
    @Test
    @DisplayName("sem credencial no contexto, o evento sai dizendo que nao havia credencial")
    void eventWithoutCredentialSaysSoInsteadOfFailing() {
        auditTrail.administratorChanged("removed", "900003");

        assertThat(keyValuesOfTheOnlyEvent()).containsEntry("actor", "sem-credencial");
    }

    @Test
    @DisplayName("evento de aplicacao diz qual aplicacao agiu, e nao o nome dela")
    void applicationEventSaysWhichApplicationActed() {
        publishCaller(new ApplicationCaller(700_001L, "portal-web"));

        auditTrail.applicationTokenChanged("issued", "700001", "700002");

        Map<String, String> keyValues = keyValuesOfTheOnlyEvent();
        assertThat(keyValues).containsEntry("actor", "aplicacao:700001")
                .containsEntry("token_id", "700002");
        assertThat(keyValues.values()).doesNotContain("portal-web");
    }

    /**
     * O instante, a correlacao e a origem que a regra 4 exige nao sao campos do evento: eles
     * vem do contexto que o filtro de requisicao publica, e este caso prova que chegam.
     */
    @Test
    @DisplayName("o evento carrega a correlacao e a origem da requisicao")
    void eventCarriesTheRequestContext() {
        MdcKeys.publish(MdcKeys.CORRELATION_ID, "requisicao-de-teste-01");
        MdcKeys.publish(MdcKeys.ORIGIN, "203.0.113.7");

        auditTrail.clientSignInAttempted(700_001L, "recusada");

        assertThat(captured.list).hasSize(1);
        assertThat(captured.list.getFirst().getMDCPropertyMap())
                .containsEntry(MdcKeys.CORRELATION_ID, "requisicao-de-teste-01")
                .containsEntry(MdcKeys.ORIGIN, "203.0.113.7");
    }

    /**
     * O endereco submetido nao entra na linha. Ele e dado pessoal, e registrar endereco em
     * tentativa recusada transformaria o log na lista de quem tentou entrar no Lactec, que e
     * exatamente a informacao que a resposta HTTP se esforca para nao entregar.
     */
    @Test
    @DisplayName("a tentativa de entrada registra o desfecho e nao o endereco submetido")
    void signInAttemptRecordsTheOutcomeAndNotTheSubmittedAddress() {
        auditTrail.clientSignInAttempted(700_001L, "recusada");

        Map<String, String> keyValues = keyValuesOfTheOnlyEvent();
        assertThat(keyValues).containsEntry("event", "client_sign_in_attempted")
                .containsEntry("outcome", "recusada")
                .containsEntry("application_id", "700001");
        assertThat(keyValues).doesNotContainKey("email");
    }

    /**
     * Guarda estrutural, no mesmo espirito da guarda de comparacao em tempo constante: o que
     * ela protege nao e comportamento observavel, e sim a ausencia de uma linha que alguem
     * pode acrescentar de boa fe amanha.
     *
     * Nenhum teste de comportamento pega "registrar o segredo do token", porque a linha de log
     * continuaria saindo e o teste continuaria verde. O que pega e ler o fonte e exigir que
     * ele nao mencione o valor em claro, a senha nem a forma guardada dela.
     */
    @Test
    @DisplayName("o fonte da trilha nao menciona senha, forma guardada nem valor de token")
    void auditTrailSourceNeverMentionsASecret() throws Exception {
        String source = Files.readString(Path.of("src/main/java/br/ufpr/tcc/auth/shared/"
                + "observability/AuditTrail.java"));
        String code = source.lines()
                .filter(line -> !line.strip().startsWith("*") && !line.strip().startsWith("/*"))
                .collect(Collectors.joining("\n"));

        assertThat(code).doesNotContain("clearValue").doesNotContain("passwordHash")
                .doesNotContain("accessToken").doesNotContain("password");
    }

    private static void publishCaller(Object principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_TESTE"))));
        SecurityContextHolder.setContext(context);
    }
}
