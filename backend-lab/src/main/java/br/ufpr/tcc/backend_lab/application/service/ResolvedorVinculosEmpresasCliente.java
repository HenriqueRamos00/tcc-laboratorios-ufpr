package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResolvedorVinculosEmpresasCliente {
    private final EmpresaRepository empresas;

    public Set<Empresa> resolver(List<String> ids, Set<Empresa> vinculosAtuais) {
        List<Long> idsNumericos = converterIds(ids);
        Set<Long> idsUnicos = new LinkedHashSet<>(idsNumericos);
        if (idsUnicos.size() != idsNumericos.size()) {
            throw RegraNegocioException.dadosInvalidos("Empresas repetidas");
        }

        List<Empresa> encontradas = empresas.findAllById(idsUnicos);
        if (encontradas.size() != idsUnicos.size()) {
            throw RegraNegocioException.dadosInvalidos("Empresa selecionada inexistente");
        }

        Set<Long> idsAtuais =
                vinculosAtuais.stream()
                        .map(Empresa::getId)
                        .collect(Collectors.toCollection(HashSet::new));
        boolean adicionaEmpresaInativa =
                encontradas.stream()
                        .anyMatch(empresa -> !empresa.isAtivo() && !idsAtuais.contains(empresa.getId()));
        if (adicionaEmpresaInativa) {
            throw RegraNegocioException.dadosInvalidos("Não é possível adicionar empresa inativa");
        }
        if (encontradas.stream().noneMatch(Empresa::isAtivo)) {
            throw RegraNegocioException.dadosInvalidos("Selecione ao menos uma empresa ativa");
        }

        return new LinkedHashSet<>(encontradas);
    }

    private List<Long> converterIds(List<String> ids) {
        try {
            return ids.stream().map(Long::valueOf).toList();
        } catch (NumberFormatException exception) {
            throw RegraNegocioException.dadosInvalidos("Identificador de empresa inválido");
        }
    }
}
