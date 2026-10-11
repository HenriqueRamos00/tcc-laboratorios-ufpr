package br.ufpr.tcc.backend_lab.presentation.advice;

import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException.Motivo;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.TecnicoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ViolacaoUnicidade;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ApiErrorResponse> handleRegraNegocio(RegraNegocioException exception) {
        return ResponseEntity.status(statusFor(exception.getMotivo()))
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleViolacaoIntegridade(
            DataIntegrityViolationException exception) {
        if (ViolacaoUnicidade.email(exception)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("E-mail já cadastrado"));
        }
        if (ViolacaoUnicidade.cnpj(exception)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("CNPJ já cadastrado"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("Falha ao persistir cadastro"));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            CredenciaisInvalidasException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(TecnicoNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleTecnicoNaoEncontrado(
            TecnicoNaoEncontradoException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailDuplicado(
            EmailJaCadastradoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

	@ExceptionHandler(EquipamentoNaoEncontradoException.class)
	public ResponseEntity<ApiErrorResponse> handleEquipamentoNaoEncontrado(EquipamentoNaoEncontradoException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
	}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ApiErrorResponse(message));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleRequestMalformado(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Requisição inválida"));
    }

    private HttpStatus statusFor(Motivo motivo) {
        return switch (motivo) {
            case DADOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
            case CONFLITO -> HttpStatus.CONFLICT;
            case NAO_AUTENTICADO -> HttpStatus.UNAUTHORIZED;
            case ACESSO_NEGADO -> HttpStatus.FORBIDDEN;
            case NAO_ENCONTRADO -> HttpStatus.NOT_FOUND;
        };
    }
}
