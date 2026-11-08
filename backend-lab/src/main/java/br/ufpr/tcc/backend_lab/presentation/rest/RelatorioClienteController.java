package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.PdfRelatorio;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.usecase.ListarRelatoriosClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ObterPdfRelatorioClienteUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client")
public class RelatorioClienteController {
    private final ListarRelatoriosClienteUseCase listar;
    private final ObterPdfRelatorioClienteUseCase obterPdf;

    public RelatorioClienteController(ListarRelatoriosClienteUseCase listar,
            ObterPdfRelatorioClienteUseCase obterPdf) {
        this.listar = listar;
        this.obterPdf = obterPdf;
    }

    @GetMapping("/reports")
    public List<RelatorioDtos.PublicadoCliente> listar(Authentication authentication) {
        return listar.listar(usuarioId(authentication));
    }

    @GetMapping("/quotes/{quoteId}/reports")
    public RelatorioDtos.ConclusaoCliente listarDoOrcamento(Authentication authentication,
            @PathVariable Long quoteId) {
        return listar.listarDoOrcamento(usuarioId(authentication), quoteId);
    }

    @GetMapping("/reports/{id}/file")
    public ResponseEntity<byte[]> arquivo(Authentication authentication, @PathVariable String id) {
        PdfRelatorio documento = obterPdf.executar(usuarioId(authentication), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(documento.filename()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(documento.content());
    }

    private static Long usuarioId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Long value) {
            return value;
        }
        throw RegraNegocioException.naoAutenticado("Sessão de cliente inválida");
    }
}
