package br.ufpr.tcc.backend_lab.presentation.advice;

import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoEdicaoEnsaioException;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoIdentificacaoAmostraException;
import br.ufpr.tcc.backend_lab.domain.exception.DocumentoOrcamentoMuitoGrandeException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException.Motivo;
import br.ufpr.tcc.backend_lab.domain.exception.PerfilInvalidoException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaAtualIncorretaException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaNovaInvalidaException;
import br.ufpr.tcc.backend_lab.domain.exception.AlteracaoEmailIndisponivelException;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoAlteracaoEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaUtilizadoException;
import br.ufpr.tcc.backend_lab.domain.exception.FalhaEnvioEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.LimiteAlteracaoEmailException;
import br.ufpr.tcc.backend_lab.domain.exception.LimiteSenhaAtualException;
import br.ufpr.tcc.backend_lab.domain.exception.TokenAlteracaoEmailInvalidoException;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.TecnicoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ViolacaoUnicidade;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponse> handleEdicaoConcorrente(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("O ensaio foi alterado por outra sessão", "test-version-conflict"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleFalhaPersistencia(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("Falha ao persistir os dados"));
    }

    @ExceptionHandler(DadosEnsaioInvalidosException.class)
    public ResponseEntity<ApiErrorResponse> handleDadosEnsaioInvalidos(DadosEnsaioInvalidosException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(EnsaioNaoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> handleReferenciaEnsaioInexistente(EnsaioNaoEncontradoException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(ConflitoEdicaoEnsaioException.class)
    public ResponseEntity<ApiErrorResponse> handleConflitoEnsaio(ConflitoEdicaoEnsaioException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage(), "test-version-conflict"));
    }

    @ExceptionHandler(ConflitoIdentificacaoAmostraException.class)
    public ResponseEntity<ApiErrorResponse> handleConflitoAmostra(ConflitoIdentificacaoAmostraException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.getMessage(), "sample-code-conflict"));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ApiErrorResponse> handleRegraNegocio(RegraNegocioException exception) {
        return ResponseEntity.status(statusFor(exception.getMotivo()))
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PerfilInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> handlePerfilInvalido(
            PerfilInvalidoException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "invalid-profile"));
    }

    @ExceptionHandler(SenhaAtualIncorretaException.class)
    public ResponseEntity<ApiErrorResponse> handleSenhaAtualIncorreta(
            SenhaAtualIncorretaException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "current-password-incorrect"));
    }

    @ExceptionHandler(SenhaNovaInvalidaException.class)
    public ResponseEntity<ApiErrorResponse> handleSenhaNovaInvalida(
            SenhaNovaInvalidaException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "invalid-password"));
    }

    @ExceptionHandler(AlteracaoEmailIndisponivelException.class)
    public ResponseEntity<ApiErrorResponse> handleAlteracaoEmailIndisponivel(
            AlteracaoEmailIndisponivelException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "action-not-available"));
    }

    @ExceptionHandler(FalhaEnvioEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleFalhaEnvioEmail(
            FalhaEnvioEmailException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "email-delivery-unavailable"));
    }

    @ExceptionHandler(TokenAlteracaoEmailInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenAlteracaoEmailInvalido(
            TokenAlteracaoEmailInvalidoException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "invalid-email-change-token"));
    }

    @ExceptionHandler(ConflitoAlteracaoEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleConflitoAlteracaoEmail(
            ConflitoAlteracaoEmailException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "email-change-conflict"));
    }

    @ExceptionHandler(EmailJaUtilizadoException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailJaUtilizado(
            EmailJaUtilizadoException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(exception.getMessage(), "email-already-in-use"));
    }

    @ExceptionHandler(LimiteAlteracaoEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleLimiteAlteracaoEmail(
            LimiteAlteracaoEmailException exception, HttpServletRequest request) {
        return limitePerfil(exception.getTempoRestante(), exception.getMessage(), request);
    }

    @ExceptionHandler(LimiteSenhaAtualException.class)
    public ResponseEntity<ApiErrorResponse> handleLimiteSenhaAtual(
            LimiteSenhaAtualException exception, HttpServletRequest request) {
        return limitePerfil(exception.getTempoRestante(), exception.getMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> limitePerfil(
            Duration tempoRestante, String mensagem, HttpServletRequest request) {
        long segundos = Math.max(1, (long) Math.ceil(tempoRestante.toMillis() / 1000.0));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(segundos))
                .cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(mensagem, "rate-limit-exceeded"));
    }

    @ExceptionHandler(DocumentoOrcamentoMuitoGrandeException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentoMuitoGrande(
            DocumentoOrcamentoMuitoGrandeException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ApiErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadMuitoGrande(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ApiErrorResponse("O arquivo enviado deve ter no máximo 50 MiB"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartInvalido(MultipartException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MaxUploadSizeExceededException) {
                return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                        .body(new ApiErrorResponse("O arquivo enviado deve ter no máximo 50 MiB"));
            }
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("Requisição multipart inválida"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleViolacaoIntegridade(
            DataIntegrityViolationException exception) {
        if (ViolacaoUnicidade.patrimonioInstrumento(exception)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("O patrimônio já está cadastrado"));
        }
        if (ViolacaoUnicidade.codigoAmostra(exception)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse("O código da amostra já está cadastrado", "sample-code-conflict"));
        }
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
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        String uri = request.getRequestURI();
        String code = uri.endsWith("/password") ? "invalid-password"
                : uri.contains("/profile") ? "invalid-profile" : null;
        return ResponseEntity.badRequest().cacheControl(cacheControlForProfile(request))
                .body(new ApiErrorResponse(message, code));
    }

    private org.springframework.http.CacheControl cacheControlForProfile(HttpServletRequest request) {
        return request.getRequestURI().contains("/profile")
                ? org.springframework.http.CacheControl.noStore()
                : org.springframework.http.CacheControl.empty();
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class
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
