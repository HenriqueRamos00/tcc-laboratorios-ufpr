package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.application.dto.request.CreateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.CreateEmpresaRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.LoginRequest;
import br.ufpr.tcc.backend_lab.application.dto.request.UpdateClienteRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.CadastroStatus;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeClienteStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.ChangeEmpresaStatusUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.CreateEmpresaUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginClienteUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.LoginUseCase;
import br.ufpr.tcc.backend_lab.application.usecase.UpdateClienteUseCase;
import br.ufpr.tcc.backend_lab.domain.exception.CredenciaisInvalidasException;
import br.ufpr.tcc.backend_lab.domain.exception.EmailJaCadastradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FluxoClienteTests {
    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired ClienteRepository clientes;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired DocumentoOrcamentoRepository documentos;
    @Autowired CreateEmpresaUseCase criarEmpresa;
    @Autowired CreateClienteUseCase criarCliente;
    @Autowired UpdateClienteUseCase editarCliente;
    @Autowired ChangeClienteStatusUseCase statusCliente;
    @Autowired ChangeEmpresaStatusUseCase statusEmpresa;
    @Autowired LoginClienteUseCase loginCliente;
    @Autowired LoginUseCase loginInterno;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    Empresa empresaA, empresaB, empresaSemVinculo;
    Cliente clienteA;
    Orcamento orcamentoA, orcamentoB, orcamentoSemVinculo;
    Usuario administrador, tecnico;
    String tokenClienteA;

    @BeforeEach
    void prepararDados() {
        empresaA = empresa("Empresa A", "00000001000136");
        empresaB = empresa("Empresa B", "00000002000180");
        empresaSemVinculo = empresa("Empresa C", "00000003000125");
        clienteA = cliente("cliente-a@example.test", empresaA);
        orcamentoA = criarOrcamento(empresaA, "QA");
        orcamentoB = criarOrcamento(empresaB, "QB");
        orcamentoSemVinculo = criarOrcamento(empresaSemVinculo, "QC");
        LoginRequest credenciais = new LoginRequest(clienteA.getUsuario().getEmail(), " senha ");
        tokenClienteA = "Bearer " + loginCliente.execute(credenciais).accessToken();
        administrador = usuario(PerfilUsuario.ADMIN, "admin-fluxo@example.test");
        tecnico = usuario(PerfilUsuario.TECNICO, "tecnico-fluxo@example.test");
    }

    @Test
    void listaSomenteOrcamentosDasEmpresasDoCliente() throws Exception {
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("QA"))
                .andExpect(jsonPath("$[0].opportunityId").doesNotExist());
    }

    @Test
    void permiteConsultarOrcamentoEPdfDaEmpresaVinculada() throws Exception {
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opportunityId").doesNotExist());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes("%PDF-1.4 demo".getBytes()));
    }

    @Test
    void bloqueiaOrcamentoEPdfDeEmpresaSemVinculo() throws Exception {
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void orcamentoInexistenteRetornaNaoEncontrado() throws Exception {
        mockMvc.perform(get("/api/client/quotes/{id}", 999999L).header("Authorization", tokenClienteA))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizaAcessoQuandoVinculosDoClienteMudam() throws Exception {
        UpdateClienteRequest duasEmpresas =
                dadosAtualizacaoCliente(List.of(empresaA.getId(), empresaB.getId()), null);
        editarCliente.execute(clienteA.getId(), duasEmpresas);

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoSemVinculo.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
        editarCliente.execute(clienteA.getId(), dadosAtualizacaoCliente(List.of(empresaA.getId()), null));
        mockMvc.perform(
                        get("/api/client/quotes/{id}", orcamentoB.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativacaoDeEmpresaRetiraAcessoEMantemLoginSemEmpresas() throws Exception {
        statusEmpresa.execute(empresaA.getId(), CadastroStatus.INACTIVE);
        var respostaLogin = loginCliente.execute(new LoginRequest("cliente-a@example.test", " senha "));

        assertTrue(respostaLogin.user().companies().isEmpty());
        assertEquals(1, clientes.findById(clienteA.getId()).orElseThrow().getEmpresas().size());
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(
                        get("/api/client/quotes/{id}/document", orcamentoA.getId())
                                .header("Authorization", tokenClienteA))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativacaoDeClienteBloqueiaLoginEProximaRequisicaoComTokenAnterior() throws Exception {
        statusCliente.execute(clienteA.getId(), CadastroStatus.INACTIVE);
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(new LoginRequest("cliente-a@example.test", " senha ")));
        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNaoAcessaGestaoInterna() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", tokenClienteA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInternoNaoAcessaOrcamentosDeCliente() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(get("/api/client/quotes").header("Authorization", tokenAdministrador))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void somenteAdministradorAcessaGestaoDeEmpresas() throws Exception {
        String tokenTecnico = "Bearer " + jwtService.generateToken(tecnico);
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(get("/api/companies").header("Authorization", tokenTecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/companies").header("Authorization", tokenAdministrador))
                .andExpect(status().isOk());
    }

    @Test
    void loginInternoRejeitaCliente() {
        LoginRequest credenciais = new LoginRequest("cliente-a@example.test", " senha ");

        assertThrows(CredenciaisInvalidasException.class, () -> loginInterno.execute(credenciais));
    }

    @Test
    void loginClienteRejeitaAdministradorETecnico() {
        LoginRequest credenciaisAdministrador = new LoginRequest(administrador.getEmail(), " senha ");
        LoginRequest credenciaisTecnico = new LoginRequest(tecnico.getEmail(), " senha ");

        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(credenciaisAdministrador));
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(credenciaisTecnico));
    }

    @Test
    void buscaEmpresaComCnpjMascarado() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        get("/api/companies")
                                .param("search", "00.000.001/0001-36")
                                .header("Authorization", tokenAdministrador))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("LOCAL"));
    }

    @Test
    void buscaClientePorEmpresaSemExporSenha() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        get("/api/clients")
                                .param("search", "Empresa A")
                                .header("Authorization", tokenAdministrador))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist());
    }

    @Test
    void loginAceitaEmailComEspacosEMaiusculas() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":" CLIENTE-A@EXAMPLE.TEST ","password":" senha "}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("Client"))
                .andExpect(jsonPath("$.user.companies[0].id").value(empresaA.getId().toString()))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void senhaOmitidaPreservaHashESenhaNaoSofreTrim() {
        String hash = clienteA.getUsuario().getSenhaHash();
        editarCliente.execute(clienteA.getId(), dadosAtualizacaoCliente(List.of(empresaA.getId()), null));
        assertEquals(hash, clienteA.getUsuario().getSenhaHash());
        assertThrows(
                CredenciaisInvalidasException.class,
                () -> loginCliente.execute(new LoginRequest("cliente-a@example.test", "senha")));
    }

    @Test
    void empresaInexistenteImpedeCriacaoDoCliente() {
        long quantidadeUsuariosAntes = usuarios.count();
        CreateClienteRequest dados = new CreateClienteRequest(
                "Novo cliente",
                "novo@example.test",
                "00000000000",
                "Contato",
                CadastroStatus.ACTIVE,
                List.of(empresaA.getId().toString(), "999999"),
                "senha");

        assertThrows(RegraNegocioException.class, () -> criarCliente.execute(dados));
        assertEquals(quantidadeUsuariosAntes, usuarios.count());
    }

    @Test
    void empresaInexistenteImpedeAlteracaoDosVinculos() {
        UpdateClienteRequest dados = dadosAtualizacaoCliente(List.of(empresaA.getId(), 999999L), null);

        assertThrows(
                RegraNegocioException.class,
                () -> editarCliente.execute(clienteA.getId(), dados));
        assertEquals(1, clienteA.getEmpresas().size());
        assertEquals(empresaA.getId(), clienteA.getEmpresas().iterator().next().getId());
    }

    @Test
    void rejeitaCnpjJaCadastrado() {
        CreateEmpresaRequest dados = dadosEmpresa("Duplicada", "00.000.001/0001-36");

        assertThrows(RegraNegocioException.class, () -> criarEmpresa.execute(dados));
    }

    @Test
    void rejeitaClienteComEmailDeAdministrador() {
        CreateClienteRequest dados = new CreateClienteRequest(
                "Cliente",
                administrador.getEmail().toUpperCase(),
                "00000000000",
                "Contato",
                CadastroStatus.ACTIVE,
                List.of(empresaA.getId().toString()),
                "senha");

        assertThrows(EmailJaCadastradoException.class, () -> criarCliente.execute(dados));
    }

    @Test
    void rejeitaStatusDeEmpresaInvalido() throws Exception {
        String tokenAdministrador = "Bearer " + jwtService.generateToken(administrador);

        mockMvc.perform(
                        patch("/api/companies/{id}/status", empresaA.getId())
                                .header("Authorization", tokenAdministrador)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"status":"INVALID"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    private Empresa empresa(String nome, String cnpj) {
        return empresas.findById(Long.valueOf(criarEmpresa.execute(dadosEmpresa(nome, cnpj)).id()))
                .orElseThrow();
    }

    private CreateEmpresaRequest dadosEmpresa(String nome, String cnpj) {
        return new CreateEmpresaRequest(
                nome,
                cnpj,
                "Endereço Teste, 1",
                "Cidade Teste",
                "PR",
                "Contato",
                "00000000000",
                "contato@example.test",
                CadastroStatus.ACTIVE);
    }

    private Cliente cliente(String email, Empresa empresa) {
        var criado =
                criarCliente.execute(
                        new CreateClienteRequest(
                                email,
                                email,
                                "00000000000",
                                "Cargo",
                                CadastroStatus.ACTIVE,
                                List.of(empresa.getId().toString()),
                                " senha "));
        return clientes.findById(Long.valueOf(criado.id())).orElseThrow();
    }

    private UpdateClienteRequest dadosAtualizacaoCliente(List<Long> idsEmpresas, String senha) {
        return new UpdateClienteRequest(
                "Cliente A",
                "cliente-a@example.test",
                "00000000000",
                "Cargo",
                CadastroStatus.ACTIVE,
                idsEmpresas.stream().map(String::valueOf).toList(),
                senha);
    }

    private Usuario usuario(PerfilUsuario perfil, String email) {
        Usuario usuario = new Usuario();
        usuario.setPerfil(perfil);
        usuario.setEmail(email);
        usuario.setNome(perfil.name());
        usuario.setSenhaHash(passwordEncoder.encode(" senha "));
        return usuarios.saveAndFlush(usuario);
    }

    private Orcamento criarOrcamento(Empresa empresa, String codigo) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(codigo);
        orcamento.setName(codigo);
        orcamento.setDescription("Análise de amostra");
        orcamento.setStage("Em negociação");
        orcamento.setStatus("Em negociação");
        orcamento.setTotalPrice(BigDecimal.TEN);
        orcamento.setCreatedDate(OffsetDateTime.now());
        orcamento = orcamentos.saveAndFlush(orcamento);

        DocumentoOrcamento documento = new DocumentoOrcamento();
        documento.setOrcamento(orcamento);
        documento.setContent("%PDF-1.4 demo".getBytes());
        documentos.saveAndFlush(documento);
        return orcamento;
    }
}
