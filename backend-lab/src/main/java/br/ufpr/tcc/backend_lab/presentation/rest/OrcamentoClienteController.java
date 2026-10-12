package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoResponse;
import br.ufpr.tcc.backend_lab.application.usecase.GetDocumentoOrcamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.GetOrcamentoClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListOrcamentosClienteUseCase;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/client/quotes")
@RequiredArgsConstructor
public class OrcamentoClienteController {
    private final ListOrcamentosClienteUseCase listOrcamentosCliente;
    private final GetOrcamentoClienteUseCase getOrcamentoCliente;
    private final GetDocumentoOrcamentoClienteUseCase getDocumentoOrcamentoCliente;

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
}
