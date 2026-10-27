package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoTecnicoDetalheResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.PaginaOrcamentosTecnicoResponse;
import br.ufpr.tcc.backend_lab.application.usecase.GetOrcamentoTecnicoUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ListOrcamentosTecnicoUseCase;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/technician/quotes")
@RequiredArgsConstructor
public class OrcamentoTecnicoController {
    private final ListOrcamentosTecnicoUseCase listOrcamentosTecnico;
    private final GetOrcamentoTecnicoUseCase getOrcamentoTecnico;

    @GetMapping
    public PaginaOrcamentosTecnicoResponse listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return listOrcamentosTecnico.execute(search, status, from, to, page, pageSize);
    }

    @GetMapping("/{id}")
    public OrcamentoTecnicoDetalheResponse detalhe(@PathVariable Long id) {
        return getOrcamentoTecnico.execute(id);
    }
}
