package br.ufpr.tcc.auth.notification;

/**
 * Porta de saida para o envio de mensagem.
 *
 * Ela existe separada porque enviar e-mail e dependencia externa: tem tempo limite, falha
 * sozinha, e nao pode derrubar o cadastro que a disparou. Hoje existe uma implementacao
 * que registra o envio; um adaptador de servidor de e-mail entra aqui sem tocar em mais
 * nada.
 */
public interface MessageDelivery {

    void deliver(OutboundMessage message);
}
