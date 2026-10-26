package br.ufpr.tcc.auth.shared.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> onUnreadableBody(HttpMessageNotReadableException refused) {
        LOGGER.atWarn()
                .addKeyValue("event", "request_body_refused")
                .log("Corpo do pedido ilegivel ou com campo que a rota nao declara");
        return badRequest();
    }

    private ResponseEntity<ProblemDetail> badRequest() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetailWriter.bodyOf(HttpStatus.BAD_REQUEST, "Pedido invalido"));
    }
}
