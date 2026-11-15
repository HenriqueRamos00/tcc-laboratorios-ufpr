package br.ufpr.tcc.backend_lab.presentation.rest;

import br.ufpr.tcc.backend_lab.application.dto.request.InstrumentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.usecase.CadastrarInstrumentoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests/references/instruments")
public class InstrumentoController {
    private final CadastrarInstrumentoUseCase cadastrarInstrumento;

    public InstrumentoController(CadastrarInstrumentoUseCase cadastrarInstrumento) {
        this.cadastrarInstrumento = cadastrarInstrumento;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnsaioDtos.InstrumentoRef cadastrar(@Valid @RequestBody InstrumentoRequest request) {
        return cadastrarInstrumento.executar(request);
    }
}
