package br.ufpr.tcc.backend_lab.presentation.advice;

import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CredenciaisInvalidasException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(CredenciaisInvalidasException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiErrorResponse(exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(new ApiErrorResponse(message));
	}
}
