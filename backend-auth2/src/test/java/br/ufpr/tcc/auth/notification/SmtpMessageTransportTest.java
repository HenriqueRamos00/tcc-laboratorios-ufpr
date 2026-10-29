package br.ufpr.tcc.auth.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.ufpr.tcc.auth.notification.MessageDeliveryFailedException.Reason;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import br.ufpr.tcc.auth.notification.MessageDeliveryFailedException.Reason;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import br.ufpr.tcc.auth.notification.MessageDeliveryFailedException.Reason;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * O adaptador contra soquete de verdade, sem contentor.
 *
 * O que estes casos provam e a traducao da falha, que e a razao de o adaptador existir em vez
 * de o caso de uso chamar a biblioteca: o que a biblioteca lanca carrega o endereco recusado
 * dentro do texto, e o que atravessa a porta nao pode carregar.
 *
 * O caso do servidor que aceita e nao responde tem tempo limite proprio de proposito: sem os
 * tres tempos limite aplicados na sessao, ele nao falha, ele fica pendurado para sempre, e teste
 * pendurado nao acusa nada.
 */
class SmtpMessageTransportTest {

    private static final String TOKEN = "cnf_tokenDeTesteComQuarentaETresCaracteresNoTotal";
    private static final String RECIPIENT = "pessoa@lactec.exemplo";
    private static final String CONFIRMATION_LINK = "https://portal.exemplo/confirmar?t={token}";
    private static final String RECOVERY_LINK = "https://portal.exemplo/nova-senha?t={token}";

    /**
     * O adaptador apontado para uma porta, com as mesmas propriedades de sessao que o
     * application.yaml declara.
     *
     * Os tempos limite entram curtos para o caso nao demorar, e as duas de STARTTLS entram
     * porque elas sao parte do contrato de quem envia: sem elas aqui, o caso do servidor sem
     * cifra nao reproduz o que a aplicacao publicada faz, e foi exatamente esse engano que me
     * fez classificar a recusa de cifra como falha inesperada.
     */
    private static SmtpMessageTransport transportPointingTo(int port) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(port);
        Properties properties = new Properties();
        properties.put("mail.smtp.connectiontimeout", "1000");
        properties.put("mail.smtp.timeout", "1000");
        properties.put("mail.smtp.writetimeout", "1000");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        mailSender.setJavaMailProperties(properties);
        return new SmtpMessageTransport(mailSender, "nao-responda@lactec.exemplo",
                CONFIRMATION_LINK, RECOVERY_LINK);
    }

    private static OutboundMessage confirmation() {
        return new OutboundMessage(RECIPIENT, OutboundMessage.Purpose.EMAIL_CONFIRMATION, TOKEN);
    }

    @Test
    @DisplayName("servidor que aceita e nao responde falha por tempo limite, e nao fica pendurado")
    void serverThatNeverAnswersFailsByTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            SmtpMessageTransport transport = transportPointingTo(silent.getLocalPort());

            assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
                    assertThatThrownBy(() -> transport.send(confirmation()))
                            .isInstanceOf(MessageDeliveryFailedException.class)
                            .extracting(failure ->
                                    ((MessageDeliveryFailedException) failure).reason())
                            .isEqualTo(Reason.TIMEOUT));
        }
    }

    /**
     * Servidor que atende e nao anuncia STARTTLS precisa ter motivo proprio.
     *
     * Este caso nasceu de uma medicao na aplicacao no ar: com um servidor de e-mail local sem
     * TLS, a recusa saia classificada como falha inesperada, e "inesperada" manda quem opera
     * procurar no lugar errado. A sessao e cifrada ou nao existe, porque o token viaja no
     * corpo; o que precisa ficar claro no log e que foi a cifra que faltou.
     */
    @Test
    @DisplayName("servidor que nao anuncia STARTTLS falha por cifra recusada")
    void serverWithoutStarttlsFailsAsEncryptionRefused() throws Exception {
        try (ServerSocket plainServer = new ServerSocket(0)) {
            Thread conversation = new Thread(() -> speakPlainSmtp(plainServer));
            conversation.setDaemon(true);
            conversation.start();
            SmtpMessageTransport transport = transportPointingTo(plainServer.getLocalPort());

            assertThatThrownBy(() -> transport.send(confirmation()))
                    .isInstanceOf(MessageDeliveryFailedException.class)
                    .extracting(failure -> ((MessageDeliveryFailedException) failure).reason())
                    .isEqualTo(Reason.ENCRYPTION_REFUSED);
        }
    }

    /**
     * Servidor minimo que cumprimenta e responde a saudacao sem oferecer STARTTLS, que e como
     * um servidor de e-mail de desenvolvimento se comporta.
     */
    private static void speakPlainSmtp(ServerSocket server) {
        try (Socket client = server.accept();
                BufferedReader incoming = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                PrintWriter outgoing =
                        new PrintWriter(client.getOutputStream(), true)) {
            outgoing.print("220 servidor-de-teste\r\n");
            outgoing.flush();
            String line = incoming.readLine();
            while (line != null) {
                if (line.toUpperCase(Locale.ROOT).startsWith("EHLO")) {
                    outgoing.print("250-servidor-de-teste\r\n250 SIZE 10240000\r\n");
                    outgoing.flush();
                } else if (line.toUpperCase(Locale.ROOT).startsWith("QUIT")) {
                    outgoing.print("221 tchau\r\n");
                    outgoing.flush();
                    return;
                } else {
                    outgoing.print("250 ok\r\n");
                    outgoing.flush();
                }
                line = incoming.readLine();
            }
        } catch (IOException conversationEnded) {
            return;
        }
    }

    @Test
    @DisplayName("porta sem ninguem escutando falha como conexao recusada")
    void portWithoutAListenerFailsAsConnectionRefused() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        SmtpMessageTransport transport = transportPointingTo(freePort);

        assertThatThrownBy(() -> transport.send(confirmation()))
                .isInstanceOf(MessageDeliveryFailedException.class)
                .extracting(failure -> ((MessageDeliveryFailedException) failure).reason())
                .isEqualTo(Reason.CONNECTION_REFUSED);
    }

    /**
     * A falha que atravessa a porta nao guarda a causa e nao menciona o endereco. Sem isso,
     * qualquer ponto que registrasse a excecao com a pilha levaria o endereco de uma pessoa
     * para o log, porque o texto da falha de envio costuma traze lo.
     */
    @Test
    @DisplayName("a falha que atravessa a porta nao tem causa e nao cita o endereco")
    void failureCrossingThePortCarriesNoCauseAndNoAddress() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        SmtpMessageTransport transport = transportPointingTo(freePort);

        assertThatThrownBy(() -> transport.send(confirmation()))
                .hasNoCause()
                .hasMessageNotContaining(RECIPIENT)
                .hasMessageNotContaining(TOKEN);
    }

    /**
     * O molde do link vem de propriedade, e molde sem o marcador do token faz a mensagem sair
     * bonita e nao confirmar nada. E a falha que ninguem percebe em teste de fluxo e ninguem
     * perdoa em producao, entao ela e recusada na construcao.
     */
    /**
     * Cada proposito leva o seu link, com o token dentro, e nunca o do outro. Ler o corpo
     * exigiria servidor de e-mail, entao o que o caso exercita e a montagem: ela e a decisao,
     * e a conversa de protocolo tem caso proprio nos dois casos de soquete acima.
     */
    @Test
    @DisplayName("cada proposito leva o proprio link, com o token dentro")
    void eachPurposeCarriesItsOwnLink() {
        SmtpMessageTransport transport = transportPointingTo(1);

        String confirmationBody = transport.bodyOf(confirmation());
        String recoveryBody = transport.bodyOf(new OutboundMessage(RECIPIENT,
                OutboundMessage.Purpose.PASSWORD_RECOVERY, TOKEN));

        assertThat(confirmationBody).contains("https://portal.exemplo/confirmar?t=" + TOKEN)
                .doesNotContain("nova-senha").doesNotContain("{token}");
        assertThat(recoveryBody).contains("https://portal.exemplo/nova-senha?t=" + TOKEN)
                .doesNotContain("confirmar").doesNotContain("{token}");
    }

    /**
     * A conversa inteira com um servidor que fala SMTP simples, com o corpo lido do fio.
     *
     * Este e o unico caso que prova a montagem da mensagem do lado de fora: remetente,
     * destinatario, assunto e o link com o token dentro do corpo. Os dublês provam decisao, e
     * o caso do corpo prova a formatacao, mas nenhum dos dois prova que isso chega ao servidor
     * como mensagem valida.
     *
     * Ele e o unico que constroi o adaptador **sem** a exigencia de STARTTLS, e isso e
     * declarado em vez de escondido: um servidor de teste em soquete simples nao tem
     * certificado, e a exigencia de cifra tem caso proprio logo acima. Trocar esta escolha por
     * silencio foi o que me fez, uma vez, classificar a recusa de cifra como falha inesperada.
     */
    @Test
    @DisplayName("a mensagem chega ao servidor com remetente, destinatario, assunto e link")
    void messageReachesAServerThatSpeaksPlainSmtp() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            StringBuilder received = new StringBuilder();
            Thread conversation = new Thread(() -> completeSmtpConversation(server, received));
            conversation.setDaemon(true);
            conversation.start();
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost("127.0.0.1");
            mailSender.setPort(server.getLocalPort());
            Properties properties = new Properties();
            properties.put("mail.smtp.connectiontimeout", "2000");
            properties.put("mail.smtp.timeout", "2000");
            mailSender.setJavaMailProperties(properties);
            SmtpMessageTransport transport = new SmtpMessageTransport(mailSender,
                    "nao-responda@lactec.exemplo", CONFIRMATION_LINK, RECOVERY_LINK);

            transport.send(confirmation());
            conversation.join(5000);

            String onTheWire = received.toString();
            assertThat(onTheWire).contains("MAIL FROM:<nao-responda@lactec.exemplo>")
                    .contains("RCPT TO:<" + RECIPIENT + ">")
                    .contains("Confirme seu endereco")
                    .contains("https://portal.exemplo/confirmar?t=" + TOKEN);
        }
    }

    /** Servidor minimo que completa a conversa e guarda o que recebeu. */
    private static void completeSmtpConversation(ServerSocket server, StringBuilder received) {
        try (Socket client = server.accept();
                BufferedReader incoming = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                PrintWriter outgoing =
                        new PrintWriter(client.getOutputStream(), true)) {
            outgoing.print("220 servidor-de-teste\r\n");
            outgoing.flush();
            String line = incoming.readLine();
            while (line != null) {
                received.append(line).append('\n');
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                    outgoing.print("250-servidor-de-teste\r\n250 SIZE 10240000\r\n");
                } else if (command.startsWith("DATA")) {
                    outgoing.print("354 manda\r\n");
                    outgoing.flush();
                    String body = incoming.readLine();
                    while (body != null && !".".equals(body)) {
                        received.append(body).append('\n');
                        body = incoming.readLine();
                    }
                    outgoing.print("250 recebida\r\n");
                } else if (command.startsWith("QUIT")) {
                    outgoing.print("221 tchau\r\n");
                    outgoing.flush();
                    return;
                } else {
                    outgoing.print("250 ok\r\n");
                }
                outgoing.flush();
                line = incoming.readLine();
            }
        } catch (IOException conversationEnded) {
            return;
        }
    }

    @Test
    @DisplayName("molde de link sem o marcador do token e recusado na construcao")
    void linkTemplateWithoutThePlaceholderIsRefused() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        assertThatThrownBy(() -> new SmtpMessageTransport(mailSender,
                "nao-responda@lactec.exemplo", "https://portal.exemplo/confirmar",
                RECOVERY_LINK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmation-link");
        assertThatThrownBy(() -> new SmtpMessageTransport(mailSender,
                "nao-responda@lactec.exemplo", CONFIRMATION_LINK,
                "https://portal.exemplo/nova-senha"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recovery-link");
    }
}
