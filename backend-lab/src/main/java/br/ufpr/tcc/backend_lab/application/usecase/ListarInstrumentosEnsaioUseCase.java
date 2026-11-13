package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.repository.InstrumentoLaboratorialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarInstrumentosEnsaioUseCase {
    private final InstrumentoLaboratorialRepository instrumentos;
    public ListarInstrumentosEnsaioUseCase(InstrumentoLaboratorialRepository instrumentos) { this.instrumentos = instrumentos; }
    @Transactional(readOnly=true)
    public List<EnsaioDtos.InstrumentoRef> executar() {
        return instrumentos.findAllByOrderByNumeroPatrimonioAsc().stream().map(EnsaioDtos::instrumento).toList();
    }
}
