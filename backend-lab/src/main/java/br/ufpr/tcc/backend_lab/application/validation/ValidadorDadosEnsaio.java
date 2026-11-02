package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.model.entity.MedicaoEnsaio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ValidadorDadosEnsaio {
    private static final int MAX_TIPO = 200;
    private static final int MAX_TEXTO_LAUDO = 10_000;
    private static final int MAX_MEDICOES = 100;
    private static final int MAX_PRECISAO_DECIMAL = 24;
    private static final int MAX_CASAS_DECIMAIS = 8;
    private static final int MAX_DIGITOS_INTEIROS = 16;

    public Metadados validarMetadados(EnsaioRequest request) {
        if (request == null) throw invalidos("Dados do ensaio são obrigatórios");
        String type = obrigatorio(request.type(), "type", MAX_TIPO);
        String method = obrigatorio(request.method(), "method", 255);
        String standard = obrigatorio(request.standard(), "standard", 255);
        if (request.scheduledDate() == null) throw invalidos("Data agendada é obrigatória");
        if (request.analysisType() != null
                && !Set.of("physical-chemical", "chromatography").contains(request.analysisType())) {
            throw invalidos("Tipo de análise inválido");
        }
        if (request.analysisClassification() != null
                && !Set.of("normal", "alerta", "nao-conforme").contains(request.analysisClassification())) {
            throw invalidos("Classificação da análise inválida");
        }
        if (request.reportText() != null && request.reportText().length() > MAX_TEXTO_LAUDO) {
            throw invalidos("Texto técnico excede 10000 caracteres");
        }
        return new Metadados(type, request.analysisType(), method, standard, request.scheduledDate(),
                request.analysisClassification(), request.reportText());
    }

    public List<MedicaoEnsaio> validarMedicoes(List<EnsaioRequest.MeasurementRequest> requests) {
        if (requests == null) return List.of();
        if (requests.size() > MAX_MEDICOES) throw invalidos("São permitidas até 100 medições");
        Set<String> codes = new HashSet<>();
        List<MedicaoEnsaio> medicoes = new ArrayList<>();
        for (int position = 0; position < requests.size(); position++) {
            EnsaioRequest.MeasurementRequest request = requests.get(position);
            if (request == null) throw invalidos("Medição inválida");
            String code = obrigatorio(request.code(), "code", 80).toLowerCase(Locale.ROOT);
            if (!codes.add(code)) throw invalidos("O código da medição deve ser único");
            if (request.result() == null || request.result().kind() == null) {
                throw invalidos("Resultado da medição é obrigatório");
            }
            MedicaoEnsaio medicao = new MedicaoEnsaio();
            medicao.setCodigo(code);
            medicao.setNome(obrigatorio(request.name(), "name", 200));
            medicao.setPosicao(position);
            medicao.setUnidade(opcional(request.unit(), 40));
            medicao.setMetodo(opcional(request.method(), 255));
            medicao.setLimiteReferencia(opcional(request.referenceLimit(), 255));
            preencherResultado(medicao, request.result());
            medicoes.add(medicao);
        }
        return medicoes;
    }

    private void preencherResultado(MedicaoEnsaio medicao, EnsaioRequest.ResultRequest result) {
        medicao.setTipoResultado(result.kind());
        switch (result.kind()) {
            case "quantity" -> {
                if (result.value() == null || result.text() != null) throw invalidos("Resultado quantity inválido");
                if (!result.value().matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
                    throw invalidos("Quantidade deve ser decimal canônica");
                }
                BigDecimal value = new BigDecimal(result.value());
                int integerDigits = Math.max(value.precision() - value.scale(), 0);
                if (value.precision() > MAX_PRECISAO_DECIMAL
                        || Math.max(value.scale(), 0) > MAX_CASAS_DECIMAIS
                        || integerDigits > MAX_DIGITOS_INTEIROS) {
                    throw invalidos("Quantidade excede a precisão permitida");
                }
                medicao.setQuantidade(value);
            }
            case "not-detected", "not-performed" -> {
                if (result.value() != null || result.text() != null) {
                    throw invalidos("Resultado sem conteúdo não aceita value/text");
                }
            }
            case "text" -> {
                if (result.text() == null || result.text().isBlank()
                        || result.text().length() > 2000 || result.value() != null) {
                    throw invalidos("Resultado textual inválido");
                }
                medicao.setResultadoTextual(result.text());
            }
            default -> throw invalidos("Tipo de resultado inválido");
        }
    }

    private String obrigatorio(String value, String field, int max) {
        if (value == null || value.trim().isEmpty()) throw invalidos(field + " é obrigatório");
        String trimmed = value.trim();
        if (trimmed.length() > max) throw invalidos(field + " excede o limite permitido");
        return trimmed;
    }

    private String opcional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw invalidos("Campo excede o limite permitido");
        return trimmed;
    }

    private DadosEnsaioInvalidosException invalidos(String message) {
        return new DadosEnsaioInvalidosException(message);
    }

    public record Metadados(String type, String analysisType, String method, String standard,
            LocalDate scheduledDate, String classification, String reportText) { }
}
