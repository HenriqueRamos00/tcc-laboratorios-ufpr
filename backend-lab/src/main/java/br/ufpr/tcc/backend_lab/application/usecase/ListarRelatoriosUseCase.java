package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.application.service.MontadorSnapshotRelatorio;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.DadosRelatorioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import br.ufpr.tcc.backend_lab.domain.repository.RelatorioTecnicoRepository;
import java.util.Set;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarRelatoriosUseCase {
    private static final int MAX_TAMANHO_PAGINA = 100;
    private static final int MAX_BUSCA = 120;
    private final RelatorioTecnicoRepository relatorios;
    private final MontadorSnapshotRelatorio snapshots;
    private final DisponibilidadeRelatorios disponibilidade;

    public ListarRelatoriosUseCase(RelatorioTecnicoRepository relatorios,
            MontadorSnapshotRelatorio snapshots, DisponibilidadeRelatorios disponibilidade) {
        this.relatorios = relatorios;
        this.snapshots = snapshots;
        this.disponibilidade = disponibilidade;
    }

    @Transactional(readOnly = true)
    public RelatorioDtos.Pagina<RelatorioDtos.Item> executar(String busca, String status,
            String publicado, int pagina, int tamanho) {
        validarPaginacao(pagina, tamanho);
        String termo = busca == null ? "" : busca.trim();
        if (termo.length() > MAX_BUSCA) {
            throw new DadosRelatorioInvalidosException("A busca deve ter no máximo 120 caracteres");
        }
        StatusValidacaoRelatorio validationStatus = status == null || status.isBlank()
                ? null : status(status.trim());
        Boolean published = published(publicado);
        var resultado = relatorios.pesquisar(escaparLike(termo), validationStatus, published,
                StatusValidacaoRelatorio.VALIDATED,
                PageRequest.of(pagina - 1, tamanho, Sort.by(Sort.Direction.DESC, "id")));
        var relatoriosPagina = resultado.getContent();
        Set<Long> idsPdfValidos = disponibilidade.idsPdfValidos(
                relatoriosPagina.stream().map(relatorio -> relatorio.getId()).toList());
        var items = relatoriosPagina.stream().map(relatorio -> {
            RelatorioSnapshot snapshot = relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED
                    ? snapshots.conteudo(relatorio) : null;
            return RelatorioDtos.item(relatorio, snapshot,
                    disponibilidade.pdfInternoDisponivel(relatorio, idsPdfValidos));
        }).toList();
        return new RelatorioDtos.Pagina<>(items, pagina, tamanho, resultado.getTotalElements());
    }

    private StatusValidacaoRelatorio status(String valor) {
        for (StatusValidacaoRelatorio candidate : StatusValidacaoRelatorio.values()) {
            if (candidate.codigo().equals(valor)) {
                return candidate;
            }
        }
        throw new DadosRelatorioInvalidosException("Status documental inválido");
    }

    private Boolean published(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return switch (valor) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new DadosRelatorioInvalidosException(
                    "Filtro de publicação inválido");
        };
    }

    private void validarPaginacao(int pagina, int tamanho) {
        if (pagina < 1 || tamanho < 1 || tamanho > MAX_TAMANHO_PAGINA) {
            throw new DadosRelatorioInvalidosException("Paginação inválida");
        }
    }

    private String escaparLike(String value) {
        return value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
