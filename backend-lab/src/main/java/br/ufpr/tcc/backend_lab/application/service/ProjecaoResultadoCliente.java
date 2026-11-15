package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjecaoResultadoCliente {
    public ClienteResultadoDtos.AnalisePublicadaRef referencia(RelatorioTecnico relatorio,
            RelatorioSnapshot snapshot) {
        var test = snapshot.test();
        return new ClienteResultadoDtos.AnalisePublicadaRef(
                id(relatorio), id(relatorio), test.id(), test.code(), id(relatorio),
                snapshot.type(), snapshot.analysisType(), test.scheduledDate(),
                relatorio.getValidatedAt(), relatorio.getPublishedAt(),
                test.analysisClassification(), test.reportText(), true);
    }

    public ClienteResultadoDtos.AnalisePublicada analise(RelatorioTecnico relatorio,
            RelatorioSnapshot snapshot) {
        var test = snapshot.test();
        var equipamento = equipamento(test.analyzedEquipment());
        List<ClienteResultadoDtos.Medicao> medicoes = test.measurements().stream()
                .map(ProjecaoResultadoCliente::medicao)
                .toList();
        return new ClienteResultadoDtos.AnalisePublicada(
                id(relatorio), id(relatorio), test.id(), test.code(), id(relatorio),
                empresa(snapshot.company()), orcamento(snapshot.quote()), amostra(snapshot.sample()),
                equipamento, snapshot.type(), snapshot.analysisType(), test.scheduledDate(),
                relatorio.getValidatedAt(), relatorio.getPublishedAt(),
                test.analysisClassification(), test.reportText(), true,
                caminhoPdf(relatorio), medicoes);
    }

    public ClienteResultadoDtos.RelatorioDetalhe relatorio(RelatorioTecnico relatorio,
            RelatorioSnapshot snapshot) {
        var test = snapshot.test();
        return new ClienteResultadoDtos.RelatorioDetalhe(
                id(relatorio), id(relatorio), id(relatorio), test.id(), test.code(), id(relatorio),
                empresa(snapshot.company()), orcamento(snapshot.quote()), amostra(snapshot.sample()),
                equipamento(test.analyzedEquipment()), snapshot.type(), snapshot.analysisType(),
                test.scheduledDate(), relatorio.getValidatedAt(), relatorio.getPublishedAt(),
                test.analysisClassification(), test.reportText(), true, caminhoPdf(relatorio));
    }

    public ClienteResultadoDtos.EmpresaRef empresa(RelatorioDtos.EmpresaRef value) {
        return new ClienteResultadoDtos.EmpresaRef(value.id(), value.name());
    }

    public ClienteResultadoDtos.EquipamentoRef equipamento(RelatorioDtos.EquipamentoRef value) {
        return value == null ? null : new ClienteResultadoDtos.EquipamentoRef(
                value.id(), value.serialNumber(), value.tag(), null);
    }

    private ClienteResultadoDtos.OrcamentoRef orcamento(RelatorioDtos.OrcamentoRef value) {
        return new ClienteResultadoDtos.OrcamentoRef(value.id(), value.protocol());
    }

    private ClienteResultadoDtos.AmostraRef amostra(RelatorioDtos.AmostraRef value) {
        return new ClienteResultadoDtos.AmostraRef(value.id(), value.code(), value.description());
    }

    private static ClienteResultadoDtos.Medicao medicao(RelatorioDtos.Medicao value) {
        RelatorioDtos.Resultado result = value.result();
        return new ClienteResultadoDtos.Medicao(value.code(), value.name(), value.unit(), value.method(),
                value.referenceLimit(), new ClienteResultadoDtos.Resultado(
                        result.kind(), result.value(), result.text()));
    }

    private static String id(RelatorioTecnico relatorio) {
        return relatorio.getId().toString();
    }

    private static String caminhoPdf(RelatorioTecnico relatorio) {
        return "/api/client/reports/" + id(relatorio) + "/file";
    }
}
