package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.usecase.ObterAnaliseClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterRelatorioPublicadoClienteUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client")
public class AnaliseClienteController {
    private final ObterAnaliseClienteUseCase obterAnalise;
    private final ObterRelatorioPublicadoClienteUseCase obterRelatorio;

    public AnaliseClienteController(ObterAnaliseClienteUseCase obterAnalise,
            ObterRelatorioPublicadoClienteUseCase obterRelatorio) {
        this.obterAnalise = obterAnalise;
        this.obterRelatorio = obterRelatorio;
    }

    @GetMapping("/analyses/{id}")
    public ClienteResultadoDtos.AnalisePublicada analise(Authentication authentication,
            @PathVariable String id) {
        return obterAnalise.executar(usuarioId(authentication), id);
    }

    @GetMapping("/reports/{id}")
    public ClienteResultadoDtos.RelatorioDetalhe relatorio(Authentication authentication,
            @PathVariable String id) {
        return obterRelatorio.executar(usuarioId(authentication), id);
    }

    private static Long usuarioId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Long value) {
            return value;
        }
        throw RegraNegocioException.naoAutenticado("Sessão de cliente inválida");
    }
}
