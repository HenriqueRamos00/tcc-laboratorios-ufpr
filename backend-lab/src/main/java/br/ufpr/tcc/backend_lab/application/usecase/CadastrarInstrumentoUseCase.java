package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.InstrumentoRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.repository.InstrumentoLaboratorialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CadastrarInstrumentoUseCase {
    private final InstrumentoLaboratorialRepository instrumentos;

    public CadastrarInstrumentoUseCase(InstrumentoLaboratorialRepository instrumentos) {
        this.instrumentos = instrumentos;
    }

    @Transactional
    public EnsaioDtos.InstrumentoRef executar(InstrumentoRequest request) {
        String patrimonio = request.inventoryNumber().trim();
        if (instrumentos.existsByNumeroPatrimonio(patrimonio)) {
            throw RegraNegocioException.conflito("O patrimônio já está cadastrado");
        }
        var instrumento = new InstrumentoLaboratorial();
        instrumento.setNumeroPatrimonio(patrimonio);
        instrumento.setNome(request.name().trim());
        String certificado = request.calibrationCertificate();
        instrumento.setCertificadoCalibracao(certificado == null || certificado.isBlank() ? null : certificado.trim());
        instrumento.setCalibracaoValidaAte(request.calibrationValidUntil());
        return EnsaioDtos.instrumento(instrumentos.saveAndFlush(instrumento));
    }
}
