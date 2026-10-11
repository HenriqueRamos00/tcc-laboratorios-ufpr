package br.ufpr.tcc.backend_lab.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolvedorVinculosEmpresasClienteTests {
    private EmpresaRepository empresas;
    private ResolvedorVinculosEmpresasCliente resolvedor;

    @BeforeEach
    void prepararResolucao() {
        empresas = mock(EmpresaRepository.class);
        resolvedor = new ResolvedorVinculosEmpresasCliente(empresas);
    }

    @Test
    @DisplayName("preserva vínculo inativo existente ao atualizar cliente")
    void preservaEmpresaInativaJaVinculada() {
        Empresa ativa = empresa(1L, true);
        Empresa inativa = empresa(2L, false);
        when(empresas.findAllById(Set.of(1L, 2L))).thenReturn(List.of(ativa, inativa));

        Set<Empresa> resultado = resolvedor.resolver(List.of("1", "2"), Set.of(inativa));

        assertEquals(Set.of(ativa, inativa), resultado);
    }

    @Test
    @DisplayName("não permite adicionar empresa inativa ao cliente")
    void rejeitaNovaEmpresaInativa() {
        Empresa inativa = empresa(2L, false);
        when(empresas.findAllById(Set.of(2L))).thenReturn(List.of(inativa));

        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(List.of("2"), Set.of()));

        assertEquals("Não é possível adicionar empresa inativa", excecao.getMessage());
    }

    @Test
    @DisplayName("rejeita empresa inexistente antes de alterar vínculos")
    void rejeitaEmpresaInexistente() {
        when(empresas.findAllById(Set.of(9L))).thenReturn(List.of());

        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(List.of("9"), Set.of()));

        assertEquals("Empresa selecionada inexistente", excecao.getMessage());
    }

    @Test
    @DisplayName("rejeita IDs repetidos antes de consultar o repositório")
    void rejeitaIdsRepetidos() {
        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(List.of("1", "1"), Set.of()));

        assertEquals("Empresas repetidas", excecao.getMessage());
        verifyNoInteractions(empresas);
    }

    private Empresa empresa(Long id, boolean ativa) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        empresa.setAtivo(ativa);
        return empresa;
    }
}
