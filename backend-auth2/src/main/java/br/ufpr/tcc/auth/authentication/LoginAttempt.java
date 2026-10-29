package br.ufpr.tcc.auth.authentication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Linha do contador de tentativa de login.
 *
 * A chave e o digest de aplicacao mais identificador apresentado, e nao a conta: o
 * identificador apresentado nao fica legivel no banco, porque a tabela de controle nao pode
 * virar lista de quem tentou entrar.
 *
 * A entidade existe para que esta tabela entre na conferencia de esquema da subida, que
 * compara o mapeamento com as colunas de verdade. Antes dela a tabela era a unica do modulo
 * sem essa conferencia, porque so havia comando escrito a mao passando por cima.
 *
 * Nenhum metodo carrega a linha pela chave primaria, e isso e de proposito: todo o trabalho
 * deste contador acontece em comando de conjunto, e carregar para depois gravar e exatamente
 * a corrida que ele existe para nao ter.
 */
@Entity
@Table(name = "LOGIN_ATTEMPT")
class LoginAttempt {

    @Id
    @Column(name = "ATTEMPT_KEY", length = 32)
    private byte[] attemptKey;

    @Column(name = "FAILED_ATTEMPTS")
    private int failedAttempts;

    @Column(name = "LOCKED_UNTIL")
    private OffsetDateTime lockedUntil;

    @Column(name = "LAST_FAILURE_AT")
    private OffsetDateTime lastFailureAt;

    protected LoginAttempt() {
    }
}
