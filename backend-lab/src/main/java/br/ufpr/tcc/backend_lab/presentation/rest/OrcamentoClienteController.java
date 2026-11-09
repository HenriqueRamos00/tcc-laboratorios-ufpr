package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.RecusaOrcamentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.TermoAceiteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.usecase.AceitarOrcamentoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.GetDocumentoOrcamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.GetOrcamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListOrcamentosClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.RecusarOrcamentoUseCase;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/client/quotes")
@RequiredArgsConstructor
public class OrcamentoClienteController {
    private final ListOrcamentosClienteUseCase listOrcamentosCliente;
    private final GetOrcamentoClienteUseCase getOrcamentoCliente;
    private final GetDocumentoOrcamentoClienteUseCase getDocumentoOrcamentoCliente;
    private final AceitarOrcamentoUseCase aceitarOrcamento;
    private final RecusarOrcamentoUseCase recusarOrcamento;

    private Long usuarioId(Authentication authentication) {
        return (Long) authentication.getDetails();
    }

    @GetMapping
    public List<OrcamentoResponse> listar(Authentication auth) {
        return listOrcamentosCliente.execute(usuarioId(auth));
    }

    @GetMapping("/{id}")
    public OrcamentoResponse detalhe(Authentication auth, @PathVariable Long id) {
        return getOrcamentoCliente.execute(usuarioId(auth), id);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> documento(Authentication auth, @PathVariable Long id) {
        byte[] bytes = getDocumentoOrcamentoCliente.execute(usuarioId(auth), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=quote-" + id + ".pdf")
                .body(bytes);
    }

    /** HU-09: aceite pelo Termo de Aceite preenchido no portal. */
    @PostMapping(value = "/{id}/acceptance", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrcamentoResponse aceitarComTermo(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody TermoAceiteRequest termo) {
        return aceitarOrcamento.execute(usuarioId(auth), id, termo);
    }

    /** HU-09: aceite pela ordem de compra anexada (PDF, DOC ou DOCX). */
    @PostMapping(value = "/{id}/purchase-order", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrcamentoResponse aceitarComOrdemDeCompra(
            Authentication auth,
            @PathVariable Long id,
            @RequestPart("document") MultipartFile ordemDeCompra) {
        return aceitarOrcamento.execute(usuarioId(auth), id, ordemDeCompra);
    }

    /** HU-10: recusa com justificativa opcional. */
    @PostMapping(value = "/{id}/rejection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrcamentoResponse recusar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody RecusaOrcamentoRequest recusa) {
        return recusarOrcamento.execute(usuarioId(auth), id, recusa);
    }
}
