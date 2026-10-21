package br.ufpr.tcc.backend_auth.presentation.advice;

import br.ufpr.tcc.backend_auth.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_auth.domain.exception.ConfiguracaoInicialException;
import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import br.ufpr.tcc.backend_auth.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_auth.domain.exception.ContaNaoEncontradaException;
import br.ufpr.tcc.backend_auth.domain.exception.ConflitoContaException;
import br.ufpr.tcc.backend_auth.infrastructure.persistence.ViolacaoUnicidade;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ResponseEntity<ApiErrorResponse> notFound(ContaNaoEncontradaException exception) {
        return error(404, exception.getMessage());
    }

    @ExceptionHandler(ConflitoContaException.class)
    public ResponseEntity<ApiErrorResponse> accountConflict(ConflitoContaException exception) {
        return error(409, exception.getMessage());
    }

    @ExceptionHandler(ConfiguracaoInicialException.class)
    public ResponseEntity<ApiErrorResponse> setupUnavailable(ConfiguracaoInicialException exception) {
        return error(exception.isConcluida() ? 410 : 404, exception.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ApiErrorResponse> invalidCredentials(CredenciaisInvalidasException exception) {
        return error(401, exception.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ApiErrorResponse> duplicateEmail(EmailJaCadastradoException exception) {
        return error(409, exception.getMessage());
    }

    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ApiErrorResponse> invalidData(DadosInvalidosException exception) {
        return error(400, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        return error(400, exception.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage()).sorted().collect(Collectors.joining(", ")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return error(400, "Requisição inválida");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> unsupportedContentType() {
        return error(415, "Use Content-Type application/json");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integrityViolation(DataIntegrityViolationException exception) {
        if (ViolacaoUnicidade.email(exception)) {
            return error(409, "E-mail já cadastrado");
        }
        return error(500, "Falha ao persistir cadastro");
    }

    private ResponseEntity<ApiErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(new ApiErrorResponse(message));
    }
}
