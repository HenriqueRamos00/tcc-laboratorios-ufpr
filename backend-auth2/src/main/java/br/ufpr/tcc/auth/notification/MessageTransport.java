package br.ufpr.tcc.auth.notification;

/**
 * Porta de saida para colocar a mensagem no fio.
 *
 * Ela e separada de MessageDelivery porque as duas respondem perguntas diferentes.
 * MessageDelivery e o que o caso de uso pede, e acontece dentro da transacao dele.
 * MessageTransport e a conversa com o servidor, que acontece depois do commit, noutra thread
 * e com tempo limite. Uma classe so faria o cadastro esperar o servidor de e-mail.
 *
 * A porta nao declara nenhum tipo da biblioteca de e-mail de proposito. Quem implementa
 * traduz a falha dela para MessageDeliveryFailedException e joga fora o texto original,
 * porque o texto da falha de envio carrega o endereco de quem ia receber.
 */
public interface MessageTransport {

    void send(OutboundMessage message);
}
