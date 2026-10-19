package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoRequest;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ValidadorOrcamento {
    private static final int PRECISAO_VALOR_ORCAMENTO = 18;
    private static final int CASAS_DECIMAIS_VALOR_ORCAMENTO = 2;

    public BigDecimal calcularTotal(OrcamentoRequest request) {
        BigDecimal totalCalculado =
                request.items().stream()
                        .map(
                                item ->
                                        item.unitPrice()
                                                .multiply(item.sampleCount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        //TODO: verificar se essas validações podem ser simplificadas, exemplo: truncar/arredondar o valor
        BigDecimal totalPersistivel;
        try {
            totalPersistivel =
                    totalCalculado.setScale(
                            CASAS_DECIMAIS_VALOR_ORCAMENTO, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw RegraNegocioException.dadosInvalidos(
                    "A soma dos itens deve ter no máximo duas casas decimais");
        }

        if (totalPersistivel.precision() > PRECISAO_VALOR_ORCAMENTO) {
            throw RegraNegocioException.dadosInvalidos(
                    "A soma dos itens excede a precisão permitida para o orçamento");
        }
        if (totalPersistivel.compareTo(request.totalPrice()) != 0) {
            throw RegraNegocioException.dadosInvalidos(
                    "Valor total deve corresponder à soma dos itens do orçamento");
        }

        return totalPersistivel;
    }
}
