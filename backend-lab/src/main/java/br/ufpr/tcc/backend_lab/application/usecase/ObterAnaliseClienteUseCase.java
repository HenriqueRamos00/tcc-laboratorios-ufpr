package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.service.ConsultaRelatorioPublicadoCliente;
import br.ufpr.tcc.backend_lab.application.service.ProjecaoResultadoCliente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterAnaliseClienteUseCase {
    private final ConsultaRelatorioPublicadoCliente consulta;
    private final ProjecaoResultadoCliente projecao;

    public ObterAnaliseClienteUseCase(ConsultaRelatorioPublicadoCliente consulta,
            ProjecaoResultadoCliente projecao) {
        this.consulta = consulta;
        this.projecao = projecao;
    }

    @Transactional(readOnly = true)
    public ClienteResultadoDtos.AnalisePublicada executar(Long usuarioId, String id) {
        var resultado = consulta.executar(usuarioId, id);
        return projecao.analise(resultado.relatorio(), resultado.snapshot());
    }
}
