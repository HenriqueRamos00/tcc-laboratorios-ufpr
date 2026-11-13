package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.AcessoClienteAtual;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarRelatoriosClienteUseCase {
    private final RelatorioTecnicoRepository relatorios;
    private final OrcamentoRepository orcamentos;
    private final AcessoClienteAtual acessoCliente;
    private final DisponibilidadeRelatorios disponibilidade;
    private final MontadorSnapshotRelatorio snapshots;

    public ListarRelatoriosClienteUseCase(RelatorioTecnicoRepository relatorios,
            OrcamentoRepository orcamentos, AcessoClienteAtual acessoCliente,
            DisponibilidadeRelatorios disponibilidade, MontadorSnapshotRelatorio snapshots) {
        this.relatorios = relatorios;
        this.orcamentos = orcamentos;
        this.acessoCliente = acessoCliente;
        this.disponibilidade = disponibilidade;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public List<RelatorioDtos.PublicadoCliente> listar(Long usuarioId) {
        Cliente cliente = acessoCliente.execute(usuarioId);
        List<Long> empresasAtivas = cliente.getEmpresas().stream()
                .filter(empresa -> empresa.isAtivo())
                .map(empresa -> empresa.getId())
                .toList();
        if (empresasAtivas.isEmpty()) {
            return List.of();
        }
        var publicados = relatorios.buscarPublicadosDasEmpresas(empresasAtivas,
                StatusValidacaoRelatorio.VALIDATED);
        Set<Long> idsPdfValidos = disponibilidade.idsPdfValidos(
                publicados.stream().map(relatorio -> relatorio.getId()).toList());
        return publicados.stream()
                .filter(relatorio -> disponibilidade.publicadoDisponivel(relatorio, idsPdfValidos))
                .map(relatorio -> RelatorioDtos.publicadoCliente(
                        relatorio, snapshots.conteudo(relatorio)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RelatorioDtos.ConclusaoCliente listarDoOrcamento(Long usuarioId, Long orcamentoId) {
        var cliente = acessoCliente.execute(usuarioId);
        var orcamento = orcamentos.findById(orcamentoId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Orçamento não encontrado"));
        acessoCliente.autorizar(cliente, orcamento);
        var publicados = relatorios.buscarPublicadosDoOrcamento(orcamentoId, orcamento.getEmpresa().getId(),
                StatusValidacaoRelatorio.VALIDATED);
        Set<Long> idsPdfValidos = disponibilidade.idsPdfValidos(
                publicados.stream().map(relatorio -> relatorio.getId()).toList());
        List<RelatorioDtos.PublicadoCliente> documentos = publicados.stream()
                .filter(relatorio -> disponibilidade.publicadoDisponivel(relatorio, idsPdfValidos))
                .map(relatorio -> RelatorioDtos.publicadoCliente(
                        relatorio, snapshots.conteudo(relatorio)))
                .toList();
        String tipos = documentos.stream().map(RelatorioDtos.PublicadoCliente::testType)
                .distinct().collect(Collectors.joining("; "));
        String resumo = switch (documentos.size()) {
            case 0 -> "Nenhum relatório publicado disponível para consulta e download.";
            case 1 -> "1 relatório publicado disponível para consulta e download.";
            default -> documentos.size() + " relatórios publicados disponíveis para consulta e download.";
        };
        String protocolo = documentos.isEmpty() ? orcamento.getProtocol() : documentos.getFirst().protocol();
        return new RelatorioDtos.ConclusaoCliente(protocolo, tipos, resumo, documentos);
    }
}
