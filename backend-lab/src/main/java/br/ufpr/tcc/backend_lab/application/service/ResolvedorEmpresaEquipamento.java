package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResolvedorEmpresaEquipamento {
    private final EmpresaRepository empresas;

    public Empresa resolver(String id, Empresa atual) {
        Long idNumerico;
        try {
            idNumerico = Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw RegraNegocioException.dadosInvalidos("Identificador de empresa inválido");
        }
        Empresa empresa = empresas.findById(idNumerico)
                .orElseThrow(() -> RegraNegocioException.dadosInvalidos("Empresa selecionada inexistente"));
        if (!empresa.isAtivo() && (atual == null || !empresa.getId().equals(atual.getId()))) {
            throw RegraNegocioException.dadosInvalidos("Não é possível vincular empresa inativa");
        }
        return empresa;
    }
}
