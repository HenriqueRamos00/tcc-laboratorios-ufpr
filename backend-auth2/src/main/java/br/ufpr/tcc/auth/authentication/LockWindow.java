package br.ufpr.tcc.auth.authentication;

import java.time.OffsetDateTime;

/**
 * O prazo de bloqueio de uma chave, lido junto com o relogio do banco.
 *
 * Os dois valores saem da mesma consulta de proposito. Comparar o prazo guardado com o
 * relogio da aplicacao faria duas instancias discordarem sobre quando o bloqueio expira, e a
 * diferenca apareceria como conta que destranca mais cedo numa maquina do que na outra.
 *
 * E publico porque a expressao de construtor da consulta o instancia por reflexao. Nenhum
 * pacote de fora o menciona.
 */
public record LockWindow(OffsetDateTime lockedUntil, OffsetDateTime databaseNow) {
}
