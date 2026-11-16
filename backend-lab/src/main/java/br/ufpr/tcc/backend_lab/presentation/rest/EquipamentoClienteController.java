package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.usecase.ListarAnalisesEquipamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarEquipamentosClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterEquipamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/equipments")
public class EquipamentoClienteController {
    private final ListarEquipamentosClienteUseCase listar;
    private final ObterEquipamentoClienteUseCase obter;
    private final ListarAnalisesEquipamentoClienteUseCase listarAnalises;

    public EquipamentoClienteController(ListarEquipamentosClienteUseCase listar,
            ObterEquipamentoClienteUseCase obter,
            ListarAnalisesEquipamentoClienteUseCase listarAnalises) {
        this.listar = listar;
        this.obter = obter;
        this.listarAnalises = listarAnalises;
    }

    @GetMapping
    public ClienteResultadoDtos.Pagina<ClienteResultadoDtos.EquipamentoCliente> listar(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String search) {
        return listar.executar(usuarioId(authentication), page, pageSize, companyId, search);
    }

    @GetMapping("/{id}")
    public ClienteResultadoDtos.EquipamentoCliente obter(Authentication authentication,
            @PathVariable String id) {
        return obter.executar(usuarioId(authentication), id);
    }

    @GetMapping("/{id}/analyses")
    public ClienteResultadoDtos.Pagina<ClienteResultadoDtos.AnalisePublicada> listarAnalises(
            Authentication authentication, @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long quoteId) {
        return listarAnalises.executar(usuarioId(authentication), id, page, pageSize,
                companyId, quoteId);
    }

    private static Long usuarioId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Long value) {
            return value;
        }
        throw RegraNegocioException.naoAutenticado("Sessão de cliente inválida");
    }
}
