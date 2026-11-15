package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.PaginacaoCliente;
import br.ufpr.tcc.backend_lab.application.service.ProjecaoResultadoCliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarEquipamentosClienteUseCase {
    private final EquipamentoRepository equipamentos;
    private final RelatorioTecnicoRepository relatorios;
    private final AcessoClienteAtual acessoCliente;
    private final MontadorSnapshotRelatorio snapshots;
    private final ProjecaoResultadoCliente projecao;

    public ListarEquipamentosClienteUseCase(EquipamentoRepository equipamentos,
            RelatorioTecnicoRepository relatorios, AcessoClienteAtual acessoCliente,
            MontadorSnapshotRelatorio snapshots, ProjecaoResultadoCliente projecao) {
        this.equipamentos = equipamentos;
        this.relatorios = relatorios;
        this.acessoCliente = acessoCliente;
        this.snapshots = snapshots;
        this.projecao = projecao;
    }

    @Transactional(readOnly = true)
    public ClienteResultadoDtos.Pagina<ClienteResultadoDtos.EquipamentoCliente> executar(
            Long usuarioId, int pagina, int tamanho, Long empresaId) {
        PaginacaoCliente.validar(pagina, tamanho);
        var cliente = acessoCliente.execute(usuarioId);
        List<Long> empresasAutorizadas = acessoCliente.idsEmpresasAtivas(cliente);
        if (empresasAutorizadas.isEmpty()
                || (empresaId != null && !empresasAutorizadas.contains(empresaId))) {
            return paginaVazia(pagina, tamanho);
        }
        List<Long> empresasDaConsulta = empresaId == null
                ? empresasAutorizadas : List.of(empresaId);

        Page<Equipamento> resultado = equipamentos.listarCliente(empresasAutorizadas,
                empresaId, PaginacaoCliente.equipamentos(pagina, tamanho));
        List<Long> idsEquipamentos = resultado.getContent().stream()
                .map(Equipamento::getId)
                .toList();
        Map<Long, Long> contagens = new HashMap<>();
        Map<Long, ClienteResultadoDtos.AnalisePublicadaRef> recentes = new HashMap<>();
        if (!idsEquipamentos.isEmpty()) {
            relatorios.contarExecucoesPublicadasPorEquipamento(idsEquipamentos,
                    empresasDaConsulta, StatusValidacaoRelatorio.VALIDATED)
                    .forEach(contagem -> contagens.put(
                            contagem.getEquipmentId(), contagem.getAnalysisCount()));
            relatorios.buscarPublicacaoMaisRecentePorEquipamento(idsEquipamentos,
                    empresasDaConsulta, StatusValidacaoRelatorio.VALIDATED)
                    .forEach(relatorio -> {
                        RelatorioSnapshot snapshot = snapshots.conteudo(relatorio);
                        recentes.put(relatorio.getValidatedEquipmentId(),
                                projecao.referencia(relatorio, snapshot));
                    });
        }

        List<ClienteResultadoDtos.EquipamentoCliente> itens = resultado.getContent().stream()
                .map(equipamento -> projetar(equipamento,
                        contagens.getOrDefault(equipamento.getId(), 0L),
                        recentes.get(equipamento.getId())))
                .toList();
        return new ClienteResultadoDtos.Pagina<>(itens, pagina, tamanho, resultado.getTotalElements());
    }

    private ClienteResultadoDtos.EquipamentoCliente projetar(Equipamento equipamento,
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

    private static ClienteResultadoDtos.Pagina<ClienteResultadoDtos.EquipamentoCliente> paginaVazia(
            int pagina, int tamanho) {
        return new ClienteResultadoDtos.Pagina<>(List.of(), pagina, tamanho, 0);
    }
}
