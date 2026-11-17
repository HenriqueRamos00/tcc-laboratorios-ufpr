package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.ClienteResultadoDtos;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.AcessoHistoricoEquipamentoCliente;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.PaginacaoCliente;
import br.ufpr.tcc.backend_lab.application.service.ProjecaoResultadoCliente;
import br.ufpr.tcc.backend_lab.domain.exception.EquipamentoNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarAnalisesEquipamentoClienteUseCase {
    private final AcessoClienteAtual acessoCliente;
    private final AcessoHistoricoEquipamentoCliente acessoHistorico;
    private final RelatorioTecnicoRepository relatorios;
    private final MontadorSnapshotRelatorio snapshots;
    private final ProjecaoResultadoCliente projecao;

    public ListarAnalisesEquipamentoClienteUseCase(AcessoClienteAtual acessoCliente,
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
    public ClienteResultadoDtos.Pagina<ClienteResultadoDtos.AnalisePublicada> executar(
            Long usuarioId, String equipamentoIdTexto, int pagina, int tamanho,
            Long empresaId, Long orcamentoId) {
        PaginacaoCliente.validar(pagina, tamanho);
        long equipamentoId = idEquipamento(equipamentoIdTexto);
        var cliente = acessoCliente.execute(usuarioId);
        List<Long> empresasAutorizadas = acessoCliente.idsEmpresasAtivas(cliente);
        acessoHistorico.executar(cliente, equipamentoId, empresasAutorizadas);
        if (empresasAutorizadas.isEmpty()
                || (empresaId != null && !empresasAutorizadas.contains(empresaId))) {
            return paginaVazia(pagina, tamanho);
        }
        var resultado = relatorios.buscarHistoricoCliente(equipamentoId, empresasAutorizadas,
                empresaId, orcamentoId, StatusValidacaoRelatorio.VALIDATED,
                PaginacaoCliente.historico(pagina, tamanho));
        List<ClienteResultadoDtos.AnalisePublicada> itens = resultado.getContent().stream()
                .map(relatorio -> projecao.analise(relatorio, snapshots.conteudo(relatorio)))
                .toList();
        return new ClienteResultadoDtos.Pagina<>(itens, pagina, tamanho,
                resultado.getTotalElements());
    }

    private static ClienteResultadoDtos.Pagina<ClienteResultadoDtos.AnalisePublicada> paginaVazia(
            int pagina, int tamanho) {
        return new ClienteResultadoDtos.Pagina<>(List.of(), pagina, tamanho, 0);
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
