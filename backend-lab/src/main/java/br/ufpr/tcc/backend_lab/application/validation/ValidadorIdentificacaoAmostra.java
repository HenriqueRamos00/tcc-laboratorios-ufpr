package br.ufpr.tcc.backend_lab.application.validation;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import org.springframework.stereotype.Component;

@Component
public class ValidadorIdentificacaoAmostra {
    public Identificacao validar(EnsaioRequest request) {
        if (request == null) throw new DadosEnsaioInvalidosException("Dados do ensaio são obrigatórios");
        if (request.sampleId() != null && request.sampleId().isBlank()) {
            throw new DadosEnsaioInvalidosException("ID da amostra inválido");
        }
        boolean amostraExistente = request.sampleId() != null;
        boolean amostraNova = request.newSample() != null;
        if (amostraExistente == amostraNova) {
            throw new DadosEnsaioInvalidosException("Informe uma amostra existente ou uma nova identificação");
        }
        if (amostraExistente) {
            return new Identificacao(ResolvedorReferenciasEnsaio.id(request.sampleId()), null);
        }

        EnsaioRequest.NewSampleRequest nova = request.newSample();
        Long quoteId = ResolvedorReferenciasEnsaio.id(nova.quoteId());
        String codigo = obrigatorio(nova.code(), "Código da amostra", 80);
        String descricao = obrigatorio(nova.description(), "Descrição da amostra", 255);
        Long equipamentoId = nova.analyzedEquipmentId() == null
                ? null : ResolvedorReferenciasEnsaio.id(nova.analyzedEquipmentId());
        return new Identificacao(null, new NovaAmostra(quoteId, codigo, descricao, equipamentoId));
    }

    private String obrigatorio(String value, String campo, int limite) {
        if (value == null || value.trim().isEmpty()) {
            throw new DadosEnsaioInvalidosException(campo + " é obrigatória");
        }
        String normalizado = value.trim();
        if (normalizado.length() > limite) {
            throw new DadosEnsaioInvalidosException(campo + " excede o limite permitido");
        }
        return normalizado;
    }

    public record Identificacao(Long sampleId, NovaAmostra novaAmostra) { }
    public record NovaAmostra(Long quoteId, String codigo, String descricao, Long equipamentoId) { }
}
