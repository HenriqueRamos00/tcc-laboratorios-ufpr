package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarEquipamentosAnalisadosEnsaioUseCase {
    private final EquipamentoRepository equipamentos;
    private final OrcamentoRepository orcamentos;

    public ListarEquipamentosAnalisadosEnsaioUseCase(EquipamentoRepository equipamentos,
            OrcamentoRepository orcamentos) {
        this.equipamentos = equipamentos;
        this.orcamentos = orcamentos;
    }

    @Transactional(readOnly = true)
    public EnsaioDtos.Pagina<EnsaioDtos.EquipamentoAnalisado> executar(
            String quoteId, String busca, int pagina, int tamanho) {
        ListarEnsaiosUseCase.validarPagina(pagina, tamanho);
        Long idOrcamento = ResolvedorReferenciasEnsaio.id(quoteId);
        var orcamento = orcamentos.findById(idOrcamento)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Orçamento não encontrado"));
        String termo = busca == null ? "" : ListarEnsaiosUseCase.escaparLike(
                busca.trim().toLowerCase(Locale.ROOT));
        var resultado = equipamentos.pesquisarAnalisados(orcamento.getEmpresa().getId(), termo,
                PageRequest.of(pagina - 1, tamanho,
                        Sort.by(Sort.Direction.ASC, "tag").and(Sort.by("id"))));
        return new EnsaioDtos.Pagina<>(resultado.getContent().stream()
                .map(item -> new EnsaioDtos.EquipamentoAnalisado(item.getId().toString(),
                        item.getTag(), item.getNumeroSerie())).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }
}
