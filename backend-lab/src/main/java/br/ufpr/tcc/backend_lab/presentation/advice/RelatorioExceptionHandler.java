package br.ufpr.tcc.backend_lab.presentation.advice;

import br.ufpr.tcc.backend_lab.domain.exception.ConflitoRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosRelatorioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.ExecucaoRelatorioNaoEncontradaException;
import br.ufpr.tcc.backend_lab.domain.exception.FalhaGeracaoPdfRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RelatorioExceptionHandler {
    @ExceptionHandler(DadosRelatorioInvalidosException.class)
    public ResponseEntity<ApiErrorResponse> handleDadosInvalidos(DadosRelatorioInvalidosException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(exception.getMessage(), "invalid-report-request"));
    }

    @ExceptionHandler(RelatorioNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleNaoEncontrado(RelatorioNaoEncontradoException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage(), "report-not-found"));
    }

    @ExceptionHandler(ExecucaoRelatorioNaoEncontradaException.class)
    public ResponseEntity<ApiErrorResponse> handleExecucaoNaoEncontrada(
            ExecucaoRelatorioNaoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage(), "test-not-found"));
    }

    @ExceptionHandler(ConflitoRelatorioException.class)
    public ResponseEntity<ApiErrorResponse> handleConflito(ConflitoRelatorioException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage(), exception.getCode()));
    }

    @ExceptionHandler(FalhaGeracaoPdfRelatorioException.class)
    public ResponseEntity<ApiErrorResponse> handleFalhaPdf(FalhaGeracaoPdfRelatorioException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(exception.getMessage(), "report-pdf-generation-failed"));
    }
}
