package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoDetalheInternoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.PaginaOrcamentosResponse;
import br.ufpr.tcc.backend_lab.application.usecase.CreateOrcamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.GetDocumentoOrcamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.GetOrcamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListOrcamentosUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateOrcamentoUseCase;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class OrcamentoController {
    private final ListOrcamentosUseCase listOrcamentos;
    private final GetOrcamentoUseCase getOrcamento;
    private final GetDocumentoOrcamentoUseCase getDocumentoOrcamento;
    private final CreateOrcamentoUseCase createOrcamento;
    private final UpdateOrcamentoUseCase updateOrcamento;

    @GetMapping
    public PaginaOrcamentosResponse listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return listOrcamentos.execute(search, status, page, pageSize);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrcamentoDetalheInternoResponse> criarJson(
            @Valid @RequestBody OrcamentoRequest request) {
        return criar(request, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrcamentoDetalheInternoResponse> criarMultipart(
            @Valid @RequestPart("quote") OrcamentoRequest request,
            @RequestPart(value = "document", required = false) MultipartFile documento) {
        return criar(request, documento);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrcamentoDetalheInternoResponse atualizarJson(
            @PathVariable Long id, @Valid @RequestBody OrcamentoRequest request) {
        return updateOrcamento.execute(id, request, null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrcamentoDetalheInternoResponse atualizarMultipart(
            @PathVariable Long id,
            @Valid @RequestPart("quote") OrcamentoRequest request,
            @RequestPart(value = "document", required = false) MultipartFile documento) {
        return updateOrcamento.execute(id, request, documento);
    }

    @GetMapping("/{id}")
    public OrcamentoDetalheInternoResponse detalhe(@PathVariable Long id) {
        return getOrcamento.execute(id);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> documento(@PathVariable Long id) {
        byte[] bytes = getDocumentoOrcamento.execute(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=quote-" + id + ".pdf")
                .body(bytes);
    }

    private ResponseEntity<OrcamentoDetalheInternoResponse> criar(
            OrcamentoRequest request, MultipartFile documento) {
        OrcamentoDetalheInternoResponse detalhe = createOrcamento.execute(request, documento);
        return ResponseEntity.created(URI.create("/api/quotes/" + detalhe.id())).body(detalhe);
    }
}
