package br.ufpr.tcc.auth.notification;

/**
 * Porta de saida para o envio de mensagem.
 *
 * Ela existe separada porque enviar e-mail e dependencia externa: tem tempo limite, falha
 * sozinha, e nao pode derrubar o cadastro que a disparou. Quem a implementa nao fala com
 * servidor nenhum: anota a intencao dentro da transacao do caso de uso e devolve na hora. A
 * conversa com o servidor fica em MessageTransport, depois do commit, noutra thread e com
 * disjuntor.
 *
 * O caso de uso nao trata falha de envio e nao tem como tratar: no instante em que ele
 * chama, ninguem sabe ainda se a transacao vai ser confirmada.
 */
public interface MessageDelivery {

    void deliver(OutboundMessage message);
}
