package br.ufpr.tcc.auth.shared.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusa de pedido invalido, tratada aqui para que o valor submetido nao chegue ao log.
 *
 * Sem este tratador, quem responde pela falha de validacao e o resolvedor padrao do servidor
 * de rotas, e ele registra a excecao inteira em nivel de aviso. A mensagem dessa excecao
 * carrega o **valor recusado**, campo por campo. Medido na aplicacao no ar: senha com um
 * caractere a mais que o teto, e senha colada com sufixo, iam para o log do contentor em
 * texto claro, junto com o endereco de quem digitou e-mail fora do formato. Pela rota de
 * entrada administrativa, que e aberta, isso valia para qualquer um que alcance a porta.
 *
 * A regra de log do modulo lista senha e corpo de requisicao de login entre o que nunca se
 * registra, entao o que sai daqui e o **nome** dos campos recusados, nunca o conteudo deles.
 * Nome de campo basta para quem opera descobrir que a integracao esta mandando corpo errado,
 * e nao conta nada sobre quem mandou.
 *
 * O corpo da resposta sai do mesmo lugar que o das recusas da cadeia de seguranca, porque a
 * matriz de codigos compara respostas irmas inteiras, e dois escritores divergem em silencio.
 */
@RestControllerAdvice
class InvalidRequestAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidRequestAdvice.class);

    /**
     * Espera sugerida ao chamador. Curta de proposito: falta de capacidade aqui dura o tempo
     * de uma conexao voltar ao pool, nao o tempo de alguem consertar algo.
     */
    private static final long RETRY_AFTER_SECONDS = 2L;

    private final ProblemDetailWriter problemDetailWriter;

    InvalidRequestAdvice(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> onInvalidField(MethodArgumentNotValidException refused) {
        List<String> refusedFields = refused.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();
        LOGGER.atWarn()
                .addKeyValue("event", "request_refused_by_validation")
                .addKeyValue("fields", String.join(",", refusedFields))
                .log("Pedido recusado pela validacao");
        return badRequest();
    }

    /**
     * Corpo ilegivel e campo desconhecido entram pelo mesmo caminho, e o segundo e proposital:
     * campo que a rota nao honra e recusado em vez de descartado em silencio.
     *
     * Corpo que passou do teto durante a leitura chega aqui tambem, e nao pode sair como 400.
     * Medido: o conversor de mensagem embrulha o que vem da entrada, inclusive excecao nao
     * verificada, nesta mesma excecao de corpo ilegivel, entao a causa e o unico lugar onde a
     * diferenca entre "corpo errado" e "corpo grande" ainda existe.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> onUnreadableBody(HttpMessageNotReadableException refused) {
        if (wasAboveCeiling(refused)) {
            return payloadTooLarge();
        }
        LOGGER.atWarn()
                .addKeyValue("event", "request_body_refused")
                .log("Corpo do pedido ilegivel ou com campo que a rota nao declara");
        return badRequest();
    }

    private static boolean wasAboveCeiling(Throwable refused) {
        for (Throwable cause = refused; cause != null; cause = cause.getCause()) {
            if (cause instanceof BodyAboveCeilingException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Corpo que passou do teto durante a leitura, no caminho sem tamanho declarado.
     *
     * A resposta e a mesma do caminho com tamanho declarado, 413, e isso importa: o teto nao
     * pode mudar de significado conforme o cabecalho que o chamador escolheu mandar. Medido
     * antes deste tratador: o mesmo corpo de 8 MB respondia 413 com tamanho declarado e 400
     * em pedacos, depois de ter sido lido e interpretado inteiro.
     */
    @ExceptionHandler(BodyAboveCeilingException.class)
    ResponseEntity<ProblemDetail> onBodyAboveCeiling(BodyAboveCeilingException tooLarge) {
        return payloadTooLarge();
    }

    private ResponseEntity<ProblemDetail> payloadTooLarge() {
        LOGGER.atWarn()
                .addKeyValue("event", "request_body_above_ceiling")
                .log("Corpo do pedido passou do teto durante a leitura");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetailWriter.bodyOf(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Corpo da requisicao acima do tamanho aceito"));
    }

    /**
     * Falta de capacidade momentanea, e nao defeito.
     *
     * Medido numa rajada contra a aplicacao no ar: com o pool de conexao cheio, uma requisicao
     * esperou alem do tempo limite e saiu como 500. O processo nao caiu e as outras
     * responderam certo, mas 500 diz ao consumidor que o servico quebrou, quando o que
     * aconteceu foi fila cheia por um instante. O 503 com Retry-After diz a coisa certa.
     *
     * O corpo nao descreve a infraestrutura: nome de pool e contagem de conexao dizem ao
     * chamador o tamanho da instalacao e nao o ajudam em nada.
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ProblemDetail> onCapacityExhausted(DataAccessResourceFailureException full) {
        LOGGER.atWarn()
                .addKeyValue("event", "request_refused_without_capacity")
                .addKeyValue("failure_type", full.getClass().getSimpleName())
                .log("Pedido recusado por falta de capacidade momentanea");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER_SECONDS))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetailWriter.bodyOf(HttpStatus.SERVICE_UNAVAILABLE,
                        "Servico sem capacidade no momento"));
    }

    /**
     * Banco fora de alcance, que e indisponibilidade e nao defeito do pedido.
     *
     * A excecao e outra que a de falta de capacidade, e por isso ela precisa de tratador
     * proprio: a de capacidade nasce do pool cheio e e de acesso a dados, esta nasce de nao
     * haver conexao para abrir a transacao e e de transacao. Medido com o contentor do banco
     * pausado: sem este tratador a rota respondia 500 no formato padrao do framework, com o
     * caminho ecoado no corpo e uma pilha por requisicao no log, enquanto a raiz de saude ja
     * dizia a verdade com 503.
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    ResponseEntity<ProblemDetail> onDatabaseOutOfReach(CannotCreateTransactionException outOfReach) {
        LOGGER.atWarn()
                .addKeyValue("event", "request_refused_without_database")
                .addKeyValue("failure_type", outOfReach.getClass().getSimpleName())
                .log("Pedido recusado porque o banco esta fora de alcance");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(RETRY_AFTER_SECONDS))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetailWriter.bodyOf(HttpStatus.SERVICE_UNAVAILABLE,
                        "Servico sem capacidade no momento"));
    }

    private ResponseEntity<ProblemDetail> badRequest() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetailWriter.bodyOf(HttpStatus.BAD_REQUEST, "Pedido invalido"));
    }
}
