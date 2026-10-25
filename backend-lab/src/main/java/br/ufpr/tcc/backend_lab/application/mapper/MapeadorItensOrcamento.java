package br.ufpr.tcc.backend_lab.application.mapper;

import br.ufpr.tcc.backend_lab.application.dto.request.OrcamentoItemRequest;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.OrcamentoItem;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MapeadorItensOrcamento {
    public void substituirItens(Orcamento orcamento, List<OrcamentoItemRequest> itens) {
        orcamento.getItems().clear();
        for (int indice = 0; indice < itens.size(); indice++) {
            OrcamentoItemRequest itemRequest = itens.get(indice);
            OrcamentoItem item = new OrcamentoItem();
            item.setOrcamento(orcamento);
            item.setPosition(indice);
            item.setTestType(itemRequest.testType());
            item.setSampleCount(itemRequest.sampleCount().intValueExact());
            item.setStandard(itemRequest.standard());
            item.setUnitPrice(itemRequest.unitPrice());
            orcamento.getItems().add(item);
        }
    }
}
