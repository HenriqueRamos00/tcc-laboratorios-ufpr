package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.AtualizarStatusEnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.usecase.AtualizarEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.AtualizarStatusEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CriarEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarAmostrasEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarEnsaiosUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarHistoricoStatusEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarEquipamentosAnalisadosEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarInstrumentosEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarOrcamentosReferenciaEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListarTecnicosEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterEnsaioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.TemplateMedicaoUseCase;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
public class EnsaioController {
    private final ListarEnsaiosUseCase listarEnsaios;
    private final ObterEnsaioUseCase obterEnsaio;
    private final CriarEnsaioUseCase criarEnsaio;
    private final AtualizarEnsaioUseCase atualizarEnsaio;
    private final AtualizarStatusEnsaioUseCase atualizarStatusEnsaio;
    private final ListarHistoricoStatusEnsaioUseCase listarHistoricoStatus;
    private final ListarAmostrasEnsaioUseCase listarAmostras;
    private final ListarOrcamentosReferenciaEnsaioUseCase listarOrcamentos;
    private final ListarEquipamentosAnalisadosEnsaioUseCase listarEquipamentosAnalisados;
    private final ListarTecnicosEnsaioUseCase listarTecnicos;
    private final ListarInstrumentosEnsaioUseCase listarInstrumentos;
    private final TemplateMedicaoUseCase templates;

    public EnsaioController(ListarEnsaiosUseCase listarEnsaios, ObterEnsaioUseCase obterEnsaio,
            CriarEnsaioUseCase criarEnsaio, AtualizarEnsaioUseCase atualizarEnsaio,
            ListarAmostrasEnsaioUseCase listarAmostras, ListarTecnicosEnsaioUseCase listarTecnicos,
            ListarInstrumentosEnsaioUseCase listarInstrumentos, TemplateMedicaoUseCase templates,
            ListarOrcamentosReferenciaEnsaioUseCase listarOrcamentos,
            ListarEquipamentosAnalisadosEnsaioUseCase listarEquipamentosAnalisados,
            AtualizarStatusEnsaioUseCase atualizarStatusEnsaio,
            ListarHistoricoStatusEnsaioUseCase listarHistoricoStatus) {
        this.listarEnsaios = listarEnsaios;
        this.obterEnsaio = obterEnsaio;
        this.criarEnsaio = criarEnsaio;
        this.atualizarEnsaio = atualizarEnsaio;
        this.atualizarStatusEnsaio = atualizarStatusEnsaio;
        this.listarHistoricoStatus = listarHistoricoStatus;
        this.listarAmostras = listarAmostras;
        this.listarOrcamentos = listarOrcamentos;
        this.listarEquipamentosAnalisados = listarEquipamentosAnalisados;
        this.listarTecnicos = listarTecnicos;
        this.listarInstrumentos = listarInstrumentos;
        this.templates = templates;
    }

    @GetMapping
    public EnsaioDtos.Pagina<EnsaioDtos.Resumo> listar(@RequestParam(required=false) String search,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return listarEnsaios.executar(search, page, pageSize);
    }

    @GetMapping("/{id}")
    public EnsaioDtos.Detalhe obter(@PathVariable String id) { return obterEnsaio.executar(id); }

    @PostMapping
    public ResponseEntity<EnsaioDtos.Detalhe> criar(@RequestBody EnsaioRequest request) {
        EnsaioDtos.Detalhe detalhe = criarEnsaio.executar(request);
        return ResponseEntity.created(URI.create("/api/tests/" + detalhe.id())).body(detalhe);
    }

    @PutMapping("/{id}")
    public EnsaioDtos.Detalhe atualizar(@PathVariable String id, @RequestBody EnsaioRequest request) {
        return atualizarEnsaio.executar(id, request);
    }

    @PatchMapping("/{id}/status")
    public EnsaioDtos.StatusAtualizado atualizarStatus(@PathVariable String id,
            @RequestBody AtualizarStatusEnsaioRequest request, Authentication authentication) {
        Long tecnicoId = authentication != null && authentication.getDetails() instanceof Long value
                ? value : null;
        return atualizarStatusEnsaio.executar(id, request, tecnicoId);
    }

    @GetMapping("/{id}/status-history")
    public List<EnsaioDtos.HistoricoStatus> historicoStatus(@PathVariable String id) {
        return listarHistoricoStatus.executar(id);
    }

    @GetMapping("/references/samples")
    public EnsaioDtos.Pagina<EnsaioDtos.AmostraRef> amostras(@RequestParam(required=false) String quoteId,
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return listarAmostras.executar(quoteId, search, page, pageSize);
    }

    @GetMapping("/references/quotes")
    public EnsaioDtos.Pagina<EnsaioDtos.OrcamentoRef> orcamentos(
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return listarOrcamentos.executar(search, page, pageSize);
    }

    @GetMapping("/references/analyzed-equipment")
    public EnsaioDtos.Pagina<EnsaioDtos.EquipamentoAnalisado> equipamentosAnalisados(
            @RequestParam String quoteId, @RequestParam(required=false) String search,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return listarEquipamentosAnalisados.executar(quoteId, search, page, pageSize);
    }

    @GetMapping("/references/technicians")
    public List<EnsaioDtos.TecnicoRef> tecnicos() { return listarTecnicos.executar(); }

    @GetMapping("/references/instruments")
    public List<EnsaioDtos.InstrumentoRef> instrumentos() { return listarInstrumentos.executar(); }

    @GetMapping("/measurement-templates/{analysisType}")
    public EnsaioDtos.Template template(@PathVariable String analysisType) { return templates.obter(analysisType); }
}
