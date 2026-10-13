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
        Empresa empresaAtiva = empresa(1L, true);
        Empresa empresaInativaJaVinculada = empresa(2L, false);
        List<String> idsSelecionados =
                List.of(empresaAtiva.getId().toString(), empresaInativaJaVinculada.getId().toString());
        Set<Empresa> vinculosAtuais = Set.of(empresaInativaJaVinculada);
        Set<Empresa> vinculosEsperados = Set.of(empresaAtiva, empresaInativaJaVinculada);

        when(empresas.findAllById(Set.of(empresaAtiva.getId(), empresaInativaJaVinculada.getId())))
                .thenReturn(List.of(empresaAtiva, empresaInativaJaVinculada));

        Set<Empresa> vinculosResolvidos = resolvedor.resolver(idsSelecionados, vinculosAtuais);

        assertEquals(vinculosEsperados, vinculosResolvidos);
    }

    @Test
    @DisplayName("não permite adicionar empresa inativa ao cliente")
    void rejeitaNovaEmpresaInativa() {
        Empresa empresaInativaSemVinculo = empresa(2L, false);
        List<String> idsSelecionados = List.of(empresaInativaSemVinculo.getId().toString());
        when(empresas.findAllById(Set.of(empresaInativaSemVinculo.getId())))
                .thenReturn(List.of(empresaInativaSemVinculo));

        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(idsSelecionados, Set.of()));

        assertEquals("Não é possível adicionar empresa inativa", excecao.getMessage());
    }

    @Test
    @DisplayName("rejeita empresa inexistente antes de alterar vínculos")
    void rejeitaEmpresaInexistente() {
        Long idEmpresaInexistente = 9L;
        List<String> idsSelecionados = List.of(idEmpresaInexistente.toString());
        when(empresas.findAllById(Set.of(idEmpresaInexistente))).thenReturn(List.of());

        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(idsSelecionados, Set.of()));

        assertEquals("Empresa selecionada inexistente", excecao.getMessage());
    }

    @Test
    @DisplayName("rejeita IDs repetidos antes de consultar o repositório")
    void rejeitaIdsRepetidos() {
        String idEmpresa = "1";
        List<String> idsRepetidos = List.of(idEmpresa, idEmpresa);

        RegraNegocioException excecao =
                assertThrows(
                        RegraNegocioException.class,
                        () -> resolvedor.resolver(idsRepetidos, Set.of()));

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
