package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardControllerTests {
    private static final String PATH = "/api/admin/dashboard";

    @Autowired MockMvc mockMvc;
    @Autowired ClienteRepository clientes;
    @Autowired EmpresaRepository empresas;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwtService;

    private Usuario administrador;
    private Usuario tecnico;
    private Usuario clienteSemCadastro;
    private String tokenAdministrador;
    private String tokenTecnico;
    private String tokenCliente;
    private int sequenciaEmpresa;
    private int sequenciaOrcamento;

    @BeforeEach
    void prepararUsuarios() {
        administrador = criarUsuario(PerfilUsuario.ADMIN, "admin-dashboard@example.test", true);
        tecnico = criarUsuario(PerfilUsuario.TECNICO, "tecnico-dashboard@example.test", true);
        clienteSemCadastro =
                criarUsuario(PerfilUsuario.CLIENTE, "cliente-dashboard@example.test", true);
        tokenAdministrador = token(administrador);
        tokenTecnico = token(tecnico);
        tokenCliente = token(clienteSemCadastro);
    }

    @Test
    @DisplayName("retorna indicadores zerados quando não há propostas nem clientes cadastrados")
    void retornaIndicadoresZeradosSemDados() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuotes").value(0))
                .andExpect(jsonPath("$.pendingAcceptance").value(0))
                .andExpect(jsonPath("$.activeClients").value(0))
                .andExpect(jsonPath("$.totalQuotedValue").value(0))
                .andExpect(jsonPath("$.totalQuotedValue").isNumber());
    }

    @Test
    @DisplayName("restringe os indicadores a administrador e preserva os status HTTP de segurança")
    void restringeAcessoPorPerfil() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());

        // O filtro interno rejeita tokens CLIENTE antes da autorização da rota.
        mockMvc.perform(get(PATH).header("Authorization", tokenCliente))
                .andExpect(status().isUnauthorized());

        administrador.setAtivo(false);
        usuarios.saveAndFlush(administrador);
        mockMvc.perform(get(PATH).header("Authorization", tokenAdministrador))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("soma propostas de todos os status e conta clientes ativos sem duplicar empresas")
    void agregaTodosOsStatusEContaClientesUmaVez() throws Exception {
        Empresa empresaAtiva = criarEmpresa(true);
        Empresa empresaInativa = criarEmpresa(false);

        criarOrcamento(empresaAtiva, StatusComercialOrcamento.PENDING_ACCEPTANCE, "0.10");
        criarOrcamento(empresaAtiva, StatusComercialOrcamento.IN_PROGRESS, "0.20");
        criarOrcamento(empresaInativa, StatusComercialOrcamento.FINISHED, "0.30");
        criarOrcamento(empresaAtiva, StatusComercialOrcamento.REFUSED, "0.40");
        criarOrcamento(empresaInativa, StatusComercialOrcamento.CANCELLED, "0.50");

        criarCliente(true, "cliente-multiplas-empresas@example.test", empresaAtiva, empresaInativa);
        criarCliente(true, "cliente-sem-empresa@example.test");
        criarCliente(true, "cliente-empresa-inativa@example.test", empresaInativa);
        criarCliente(false, "cliente-inativo@example.test", empresaAtiva, empresaInativa);

        mockMvc.perform(get(PATH).header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuotes").value(5))
                .andExpect(jsonPath("$.pendingAcceptance").value(1))
                .andExpect(jsonPath("$.activeClients").value(3))
                .andExpect(jsonPath("$.totalQuotedValue").value(1.50))
                .andExpect(jsonPath("$.totalQuotedValue").isNumber());
    }

    @Test
    @DisplayName("reflete atualização de status, valor e situação de cliente na consulta seguinte")
    void refleteAtualizacoesNaConsultaSeguinte() throws Exception {
        Empresa empresa = criarEmpresa(true);
        Orcamento pendente =
                criarOrcamento(empresa, StatusComercialOrcamento.PENDING_ACCEPTANCE, "0.10");
        criarOrcamento(empresa, StatusComercialOrcamento.IN_PROGRESS, "0.20");
        Cliente clienteAtivo = criarCliente(true, "cliente-ativo-dashboard@example.test");

        mockMvc.perform(get(PATH).header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuotes").value(2))
                .andExpect(jsonPath("$.pendingAcceptance").value(1))
                .andExpect(jsonPath("$.activeClients").value(1))
                .andExpect(jsonPath("$.totalQuotedValue").value(0.30));

        pendente.setStatus(StatusComercialOrcamento.CANCELLED);
        pendente.setTotalPrice(new BigDecimal("0.30"));
        orcamentos.saveAndFlush(pendente);
        clienteAtivo.getUsuario().setAtivo(false);
        clientes.saveAndFlush(clienteAtivo);

        mockMvc.perform(get(PATH).header("Authorization", tokenAdministrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuotes").value(2))
                .andExpect(jsonPath("$.pendingAcceptance").value(0))
                .andExpect(jsonPath("$.activeClients").value(0))
                .andExpect(jsonPath("$.totalQuotedValue").value(0.50));
    }

    private Usuario criarUsuario(PerfilUsuario perfil, String email, boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setNome(perfil.name());
        usuario.setEmail(email);
        usuario.setSenhaHash("hash-de-teste");
        usuario.setPerfil(perfil);
        usuario.setAtivo(ativo);
        return usuarios.saveAndFlush(usuario);
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.generateToken(usuario);
    }

    private Empresa criarEmpresa(boolean ativa) {
        Empresa empresa = new Empresa();
        empresa.setLegalName("Empresa Dashboard " + (sequenciaEmpresa + 1));
        empresa.setCnpj("0000000000" + String.format("%04d", ++sequenciaEmpresa));
        empresa.setAddress("Rua de teste, 1");
        empresa.setCity("Curitiba");
        empresa.setState("PR");
        empresa.setContactName("Contato de teste");
        empresa.setContactPhone("41999999999");
        empresa.setContactEmail("empresa-dashboard@example.test");
        empresa.setAtivo(ativa);
        return empresas.saveAndFlush(empresa);
    }

    private Orcamento criarOrcamento(
            Empresa empresa, StatusComercialOrcamento status, String totalPrice) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode("DASH-" + ++sequenciaOrcamento);
        orcamento.setName("Orçamento de teste");
        orcamento.setStatus(status);
        orcamento.setStage("Negociação");
        orcamento.setDescription("Descrição de teste");
        orcamento.setTotalPrice(new BigDecimal(totalPrice));
        orcamento.setCreatedDate(OffsetDateTime.now());
        return orcamentos.saveAndFlush(orcamento);
    }

    private Cliente criarCliente(boolean ativo, String email, Empresa... empresasVinculadas) {
        Usuario usuario = criarUsuario(PerfilUsuario.CLIENTE, email, ativo);
        Cliente cliente = new Cliente();
        cliente.setUsuario(usuario);
        cliente.setPhone("41999999999");
        cliente.setPosition("Contato de teste");
        cliente.getEmpresas().addAll(List.of(empresasVinculadas));
        return clientes.saveAndFlush(cliente);
    }
}
