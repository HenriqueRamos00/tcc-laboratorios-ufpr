package br.ufpr.tcc.auth.notification;

/**
 * Mensagem pronta para sair, publicada dentro da transacao do caso de uso.
 *
 * Ela carrega o identificador de correlacao porque quem envia roda noutra thread e o MDC nao
 * atravessa thread. Sem o valor copiado aqui, a linha de log do envio nao poderia ser ligada a
 * requisicao que a gerou, que e o que a regra de log exige de toda chamada.
 */
record OutboundMessageReady(OutboundMessage message, String correlationId) {
}
