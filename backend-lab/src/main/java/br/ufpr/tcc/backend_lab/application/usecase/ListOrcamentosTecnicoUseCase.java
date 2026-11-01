package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.OrcamentoTecnicoResumoResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.PaginaOrcamentosTecnicoResponse;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusOrcamentoTecnico;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.ParametroLikeJpa;
import br.ufpr.tcc.backend_lab.infrastructure.persistence.OrcamentoTecnicoConsultaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ListOrcamentosTecnicoUseCase {
    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final long OFFSET_MAXIMO_REPOSITORIO = Integer.MAX_VALUE;
    private static final ZoneId FUSO_DATAS = ZoneId.of("America/Sao_Paulo");
    private static final Pattern FORMATO_DATA = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final OrcamentoTecnicoConsultaRepository consultaTecnica;

    @Transactional(readOnly = true)
    public PaginaOrcamentosTecnicoResponse execute(
            String search, String status, String from, String to, int page, int pageSize) {
        long offset = page > 0 && pageSize > 0 ? (long) (page - 1) * pageSize : -1;
        if (page < 1
                || pageSize < 1
                || pageSize > TAMANHO_MAXIMO_PAGINA
                || offset > OFFSET_MAXIMO_REPOSITORIO) {
            throw RegraNegocioException.dadosInvalidos(
                    "Página inválida ou tamanho da página fora do intervalo de 1 a 100");
        }

        StatusOrcamentoTecnico filtroStatus = validarStatus(status);
        LocalDate dataInicial = parseData(from);
        LocalDate dataFinal = parseData(to);
        if (dataInicial != null && dataFinal != null && dataInicial.isAfter(dataFinal)) {
            throw RegraNegocioException.dadosInvalidos(
                    "A data inicial deve ser anterior ou igual à data final");
        }
        if (!anoDentroDoContrato(dataInicial) || !anoDentroDoContrato(dataFinal)) {
            throw RegraNegocioException.dadosInvalidos(
                    "O ano da data deve ter quatro dígitos entre 0001 e 9999");
        }

        var limites = limitesDoPeriodo(dataInicial, dataFinal);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<OrcamentoTecnicoConsultaRepository.ProjecaoOrcamentoTecnico> resultado =
                consultaTecnica.pesquisar(
                        ParametroLikeJpa.buscaParcial(search),
                        filtroStatus == null ? null : filtroStatus.codigo(),
                        limites.inicio(),
                        limites.fimExclusivo(),
                        pageable);

        List<OrcamentoTecnicoResumoResponse> items =
                resultado.getContent().stream()
                        .map(
                                orcamento ->
                                        new OrcamentoTecnicoResumoResponse(
                                                orcamento.id().toString(),
                                                orcamento.code(),
                                                orcamento.name(),
                                                orcamento.companyName(),
                                                orcamento.createdDate(),
                                                orcamento.technicalStatus()))
                        .toList();
        return new PaginaOrcamentosTecnicoResponse(
                items, page, pageSize, resultado.getTotalElements());
    }

    private StatusOrcamentoTecnico validarStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return StatusOrcamentoTecnico.fromCodigo(status)
                .orElseThrow(
                        () -> RegraNegocioException.dadosInvalidos("Status técnico inválido"));
    }

    private boolean anoDentroDoContrato(LocalDate data) {
        return data == null || (data.getYear() >= 1 && data.getYear() <= 9999);
    }

    private LocalDate parseData(String valor) {
        if (valor == null) {
            return null;
        }
        if (!FORMATO_DATA.matcher(valor).matches()) {
            throw RegraNegocioException.dadosInvalidos("Data deve seguir o formato YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException exception) {
            throw RegraNegocioException.dadosInvalidos("Período de criação inválido");
        }
    }

    private LimitesPeriodo limitesDoPeriodo(LocalDate from, LocalDate to) {
        try {
            OffsetDateTime inicio =
                    from == null ? null : from.atStartOfDay(FUSO_DATAS).toOffsetDateTime();
            OffsetDateTime fimExclusivo =
                    to == null
                            ? null
                            : to.plusDays(1).atStartOfDay(FUSO_DATAS).toOffsetDateTime();
            return new LimitesPeriodo(inicio, fimExclusivo);
        } catch (DateTimeException | ArithmeticException exception) {
            throw RegraNegocioException.dadosInvalidos("Período de criação inválido");
        }
    }

    private record LimitesPeriodo(OffsetDateTime inicio, OffsetDateTime fimExclusivo) {}
}
