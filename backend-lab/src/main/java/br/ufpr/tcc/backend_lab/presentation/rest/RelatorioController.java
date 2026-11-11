package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.ValidarRelatorioRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.PublicarRelatorioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.PdfRelatorio;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.usecase.ListarRelatoriosUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterPdfRelatorioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterRelatorioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ValidarRelatorioUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.PublicarRelatorioUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.DadosRelatorioInvalidosException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class RelatorioController {
    private final ListarRelatoriosUseCase listar;
    private final ObterRelatorioUseCase obter;
    private final ValidarRelatorioUseCase validar;
    private final PublicarRelatorioUseCase publicar;
    private final ObterPdfRelatorioUseCase pdf;

    public RelatorioController(ListarRelatoriosUseCase listar, ObterRelatorioUseCase obter,
            ValidarRelatorioUseCase validar, PublicarRelatorioUseCase publicar,
            ObterPdfRelatorioUseCase pdf) {
        this.listar = listar;
        this.obter = obter;
        this.validar = validar;
        this.publicar = publicar;
        this.pdf = pdf;
    }

    @GetMapping
    public RelatorioDtos.Pagina<RelatorioDtos.Item> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String validationStatus,
            @RequestParam(required = false) String published,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return listar.executar(search, validationStatus, published, page, pageSize);
    }

    @GetMapping("/{id}")
    public RelatorioDtos.Detalhe obter(@PathVariable String id) {
        return obter.executar(id);
    }

    @PostMapping("/{id}/validation")
    public RelatorioDtos.Validacao validar(@PathVariable String id,
            @Valid @RequestBody ValidarRelatorioRequest request, Authentication authentication) {
        Long tecnicoId = authentication != null && authentication.getDetails() instanceof Long value
                ? value : null;
        if (tecnicoId == null) {
            throw new DadosRelatorioInvalidosException("Técnico autenticado não identificado");
        }
        return validar.executar(id, request, tecnicoId);
    }

    @PostMapping("/{id}/publication")
    public RelatorioDtos.Publicacao publicar(@PathVariable String id,
            @Valid @RequestBody PublicarRelatorioRequest request, Authentication authentication) {
        Long tecnicoId = authentication != null && authentication.getDetails() instanceof Long value
                ? value : null;
        if (tecnicoId == null) {
            throw new DadosRelatorioInvalidosException("Técnico autenticado não identificado");
        }
        return publicar.executar(id, request, tecnicoId);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        PdfRelatorio documento = pdf.executar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(documento.filename()).build().toString())
                .header("X-Report-Document-State", documento.documentState())
                .header("X-Report-Version", Long.toString(documento.version()))
                .cacheControl(CacheControl.noStore())
                .body(documento.content());
    }
}
