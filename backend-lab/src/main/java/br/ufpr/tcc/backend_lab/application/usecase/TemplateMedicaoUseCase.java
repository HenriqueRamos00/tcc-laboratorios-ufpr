package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateMedicaoUseCase {
    public EnsaioDtos.Template obter(String tipo) {
        return switch (tipo) {
            case "physical-chemical" -> fisicoQuimico();
            case "chromatography" -> cromatografia();
            default -> throw new DadosEnsaioInvalidosException("Tipo de análise desconhecido");
        };
    }

    private EnsaioDtos.Template fisicoQuimico() {
        return new EnsaioDtos.Template("physical-chemical", List.of(
                linha("neutralization-index", "Índice de Neutralização (mg KOH/g óleo)", "mg KOH/g", "NBR 14248"),
                linha("water-content", "Teor de Água (ppm m/m) - Medido", "ppm", "NBR 10710"),
                linha("density", "Densidade a 20/4°C (g/mL)", "g/mL", "NBR 14065"),
                linha("power-factor-100c", "Fator de Potência a 100°C (%)", "%", "NBR 12133"),
                linha("dielectric-strength", "Rigidez Dielétrica - Calota (kV)", "kV", "NBR/IEC 60156"),
                linha("interfacial-tension", "Tensão Interfacial (dina/cm)", "dina/cm", "NBR 6234")));
    }

    private EnsaioDtos.Template cromatografia() {
        return new EnsaioDtos.Template("chromatography", List.of(
                gas("h2", "Hidrogênio"), gas("o2", "Oxigênio"), gas("n2", "Nitrogênio"),
                gas("ch4", "Metano"), gas("co", "Monóxido de Carbono"), gas("co2", "Dióxido de Carbono"),
                gas("c2h4", "Etileno"), gas("c2h6", "Etano"), gas("c2h2", "Acetileno"),
                gas("combustible-gases", "Total de Gases Combustíveis"), gas("total-gases", "Total de Gases")));
    }

    private EnsaioDtos.TemplateLinha linha(String code, String name, String unit, String method) {
        return new EnsaioDtos.TemplateLinha(code, name, unit, method, null);
    }

    private EnsaioDtos.TemplateLinha gas(String code, String name) {
        return linha(code, name, "ppm", null);
    }
}
