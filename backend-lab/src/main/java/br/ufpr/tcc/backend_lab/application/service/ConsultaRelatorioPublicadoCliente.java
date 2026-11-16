package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.domain.exception.RelatorioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConsultaRelatorioPublicadoCliente {
    private final RelatorioTecnicoRepository relatorios;
    private final AcessoClienteAtual acessoCliente;
    private final DisponibilidadeRelatorios disponibilidade;
    private final MontadorSnapshotRelatorio snapshots;

    public ConsultaRelatorioPublicadoCliente(RelatorioTecnicoRepository relatorios,
            AcessoClienteAtual acessoCliente, DisponibilidadeRelatorios disponibilidade,
            MontadorSnapshotRelatorio snapshots) {
        this.relatorios = relatorios;
        this.acessoCliente = acessoCliente;
        this.disponibilidade = disponibilidade;
        this.snapshots = snapshots;
    }

    public Resultado executar(Long usuarioId, String reportId) {
        Cliente cliente = acessoCliente.execute(usuarioId);
        long id = idRelatorio(reportId);
        RelatorioTecnico relatorio = relatorios.findById(id)
                .orElseThrow(ConsultaRelatorioPublicadoCliente::naoEncontrado);
        List<Long> pdfsValidos = List.of(id);
        if (!disponibilidade.publicadoDisponivel(relatorio,
                disponibilidade.idsPdfValidos(pdfsValidos))) {
            throw naoEncontrado();
        }
        if (relatorio.getValidatedCompany() == null || relatorio.getValidatedQuote() == null) {
            throw naoEncontrado();
        }
        acessoCliente.autorizar(cliente, relatorio.getValidatedCompany());
        RelatorioSnapshot snapshot = snapshots.conteudo(relatorio);
        return new Resultado(relatorio, snapshot);
    }

    private static long idRelatorio(String id) {
        try {
            long valor = Long.parseLong(id);
            if (valor < 1) throw new NumberFormatException();
            return valor;
        } catch (NumberFormatException exception) {
            throw naoEncontrado();
        }
    }

    private static RelatorioNaoEncontradoException naoEncontrado() {
        return new RelatorioNaoEncontradoException("Relatório publicado não encontrado");
    }

    public record Resultado(RelatorioTecnico relatorio, RelatorioSnapshot snapshot) { }
}
