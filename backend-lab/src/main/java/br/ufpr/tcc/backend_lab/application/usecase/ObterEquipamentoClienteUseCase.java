package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.AcessoHistoricoEquipamentoCliente;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.ProjecaoResultadoCliente;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObterEquipamentoClienteUseCase {
    private final AcessoClienteAtual acessoCliente;
    private final AcessoHistoricoEquipamentoCliente acessoHistorico;
    private final RelatorioTecnicoRepository relatorios;
    private final MontadorSnapshotRelatorio snapshots;
    private final ProjecaoResultadoCliente projecao;

    public ObterEquipamentoClienteUseCase(AcessoClienteAtual acessoCliente,
            AcessoHistoricoEquipamentoCliente acessoHistorico,
            RelatorioTecnicoRepository relatorios, MontadorSnapshotRelatorio snapshots,
            ProjecaoResultadoCliente projecao) {
        this.acessoCliente = acessoCliente;
        this.acessoHistorico = acessoHistorico;
        this.relatorios = relatorios;
        this.snapshots = snapshots;
        this.projecao = projecao;
    }

    @Transactional(readOnly = true)
    public ClienteResultadoDtos.EquipamentoCliente executar(Long usuarioId, String equipamentoIdTexto) {
        long equipamentoId = idEquipamento(equipamentoIdTexto);
        var cliente = acessoCliente.execute(usuarioId);
        List<Long> empresasAutorizadas = acessoCliente.idsEmpresasAtivas(cliente);
        var cadastro = acessoHistorico.executar(cliente, equipamentoId, empresasAutorizadas);
        if (cadastro.isPresent()) {
            Equipamento equipamento = cadastro.get();
            long contagem = contar(equipamentoId, empresasAutorizadas);
            var recente = relatorios.buscarPublicacaoMaisRecentePorEquipamento(
                    List.of(equipamentoId), empresasAutorizadas,
                    StatusValidacaoRelatorio.VALIDATED).stream()
                    .findFirst()
                    .map(relatorio -> projecao.referencia(relatorio, snapshots.conteudo(relatorio)))
                    .orElse(null);
            return equipamento(equipamento, contagem, recente);
        }

        var relatorio = relatorios.buscarPublicacaoMaisRecentePorEquipamento(
                List.of(equipamentoId), empresasAutorizadas,
                StatusValidacaoRelatorio.VALIDATED).stream()
                .findFirst()
                .orElseThrow(() -> new EquipamentoNaoEncontradoException(equipamentoId));
        RelatorioSnapshot snapshot = snapshots.conteudo(relatorio);
        var equipamentoSnapshot = snapshot.test() == null
                ? null : snapshot.test().analyzedEquipment();
        var empresaSnapshot = projecao.empresa(snapshot.company());
        var referenciaEquipamento = equipamentoSnapshot == null
                ? new ClienteResultadoDtos.EquipamentoRef(Long.toString(equipamentoId), null, null, null)
                : projecao.equipamento(equipamentoSnapshot);
        long contagem = contar(equipamentoId, empresasAutorizadas);
        var recente = projecao.referencia(relatorio, snapshot);
        return new ClienteResultadoDtos.EquipamentoCliente(
                referenciaEquipamento.id(), empresaSnapshot, referenciaEquipamento.serialNumber(),
                referenciaEquipamento.tag(), referenciaEquipamento.type(), null, null,
                null, null, null, null, null, contagem, recente);
    }

    private long contar(long equipamentoId, List<Long> empresasAutorizadas) {
        if (empresasAutorizadas.isEmpty()) {
            return 0;
        }
        return relatorios.contarExecucoesPublicadasPorEquipamento(List.of(equipamentoId),
                empresasAutorizadas, StatusValidacaoRelatorio.VALIDATED).stream()
                .mapToLong(item -> item.getAnalysisCount())
                .sum();
    }

    private static ClienteResultadoDtos.EquipamentoCliente equipamento(Equipamento equipamento,
            long contagem, ClienteResultadoDtos.AnalisePublicadaRef recente) {
        var empresa = equipamento.getEmpresa();
        return new ClienteResultadoDtos.EquipamentoCliente(
                equipamento.getId().toString(),
                new ClienteResultadoDtos.EmpresaRef(empresa.getId().toString(), empresa.getLegalName()),
                equipamento.getNumeroSerie(), equipamento.getTag(), equipamento.getTipo().name(),
                equipamento.getSubestacao(), equipamento.getTensaoPrimaria(),
                equipamento.getFabricante(), equipamento.getAnoFabricacao(),
                equipamento.getPotenciaMva(), equipamento.getTipoOleo(),
                equipamento.getVolumeOleoLitros(), contagem, recente);
    }

    private static long idEquipamento(String id) {
        try {
            long valor = Long.parseLong(id);
            if (valor > 0) {
                return valor;
            }
        } catch (NumberFormatException ignored) {
            // IDs inválidos recebem a mesma resposta que um cadastro ausente.
        }
        throw new EquipamentoNaoEncontradoException(-1L);
    }
}
