package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.AplicadorDadosEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import java.time.Year;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarEnsaioUseCase {
    private static final ZoneId FUSO_CODIGO_ENSAIO = ZoneId.of("America/Sao_Paulo");
    private final ExecucaoEnsaioRepository ensaios;
    private final AplicadorDadosEnsaio aplicador;

    public CriarEnsaioUseCase(ExecucaoEnsaioRepository ensaios, AplicadorDadosEnsaio aplicador) {
        this.ensaios = ensaios;
        this.aplicador = aplicador;
    }

    @Transactional
    public EnsaioDtos.Detalhe executar(EnsaioRequest request) {
        ExecucaoEnsaio ensaio = new ExecucaoEnsaio();
        ensaio.setStatus(StatusEnsaio.INCOMPLETE);
        aplicador.aplicar(ensaio, request, false);
        ensaios.saveAndFlush(ensaio);
        ensaio.setCodigo("E-" + ensaio.getId() + "/" + Year.now(FUSO_CODIGO_ENSAIO).getValue());
        return EnsaioDtos.detalhe(ensaios.saveAndFlush(ensaio));
    }
}
