package br.ufpr.tcc.backend_lab.presentation.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.ufpr.tcc.backend_lab.domain.model.entity.DocumentoOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.OrcamentoItem;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusComercialOrcamento;
import br.ufpr.tcc.backend_lab.domain.repository.DocumentoOrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_lab.domain.repository.OrcamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import br.ufpr.tcc.backend_lab.infrastructure.security.JwtService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;

@SpringBootTest
@AutoConfigureMockMvc
class OrcamentoAtomicidadeTests {
    @Autowired MockMvc mockMvc;
    @Autowired EmpresaRepository empresas;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean DocumentoOrcamentoRepository documentos;

    @Test
    @DisplayName("reverte orçamento, itens e PDF quando a gravação do documento falha")
    void reverteEdicaoQuandoFalhaAoPersistirPdf() throws Exception {
        String sufixo = Long.toString(System.nanoTime());
        Empresa empresa = criarEmpresa("Empresa de teste " + sufixo, sufixo);
        Usuario administrador = criarAdministrador("admin-atomicidade-" + sufixo + "@example.test");
        String token = "Bearer " + jwtService.generateToken(administrador);
        String codigoOriginal = "ATOMICIDADE-" + sufixo;
        byte[] pdfOriginal = pdfDeTeste(420);
        Orcamento orcamento = criarOrcamentoComItem(empresa, codigoOriginal);
        criarDocumento(orcamento, pdfOriginal);

        try {
            doThrow(new DataIntegrityViolationException("falha simulada ao salvar documento"))
                    .when(documentos)
                    .saveAndFlush(any(DocumentoOrcamento.class));

            long quantidadeItensAntes = contarItens();
            String codigoCriacaoFalha = "ATOMICIDADE-NOVA-" + sufixo;
            mockMvc.perform(
                            multipart("/api/quotes")
                                    .file(parteJson(codigoCriacaoFalha, empresa.getId(), "PendingAcceptance"))
                                    .file(new MockMultipartFile(
                                            "document", "novo.pdf", MediaType.APPLICATION_PDF_VALUE, pdfDeTeste(500)))
                                    .header("Authorization", token))
                    .andExpect(status().isInternalServerError());
            assertFalse(orcamentos.existsByCode(codigoCriacaoFalha));
            assertEquals(quantidadeItensAntes, contarItens());

            mockMvc.perform(
                            multipart("/api/quotes/{id}", orcamento.getId())
                                    .file(parteJson(codigoOriginal, empresa.getId(), "PendingAcceptance"))
                                    .file(new MockMultipartFile(
                                            "document", "substituto.pdf", MediaType.APPLICATION_PDF_VALUE, pdfDeTeste(500)))
                                    .with(request -> {
                                        request.setMethod("PUT");
                                        return request;
                                    })
                                    .header("Authorization", token))
                    .andExpect(status().isInternalServerError());

            Orcamento persistido = orcamentos.buscarDetalhePorId(orcamento.getId()).orElseThrow();
            assertEquals(codigoOriginal, persistido.getCode());
            assertEquals("Nome anterior", persistido.getName());
            assertEquals(StatusComercialOrcamento.PENDING_ACCEPTANCE, persistido.getStatus());
            assertEquals("Etapa anterior", persistido.getStage());
            assertEquals(1, persistido.getItems().size());
            assertEquals("Ensaio anterior", persistido.getItems().getFirst().getTestType());
            assertArrayEquals(
                    pdfOriginal, documentos.findById(orcamento.getId()).orElseThrow().getContent());
        } finally {
            documentos.deleteById(orcamento.getId());
            documentos.flush();
            orcamentos.deleteById(orcamento.getId());
            orcamentos.flush();
            usuarios.delete(administrador);
            usuarios.flush();
            empresas.delete(empresa);
            empresas.flush();
        }
    }

    private Empresa criarEmpresa(String nome, String identificador) {
        Empresa empresa = new Empresa();
        empresa.setLegalName(nome);
        empresa.setCnpj(String.format("%014d", Math.abs(System.nanoTime()) % 100_000_000_000_000L));
        empresa.setAddress("Rua de teste, 1");
        empresa.setCity("Curitiba");
        empresa.setState("PR");
        empresa.setContactName("Contato legado");
        empresa.setContactPhone("41999990000");
        empresa.setContactEmail("empresa-" + identificador + "@example.test");
        return empresas.saveAndFlush(empresa);
    }

    private Usuario criarAdministrador(String email) {
        Usuario usuario = new Usuario();
        usuario.setNome("Administrador de teste");
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode("senha de teste"));
        usuario.setPerfil(PerfilUsuario.ADMIN);
        return usuarios.saveAndFlush(usuario);
    }

    private Orcamento criarOrcamentoComItem(Empresa empresa, String codigo) {
        Orcamento orcamento = new Orcamento();
        orcamento.setEmpresa(empresa);
        orcamento.setCode(codigo);
        orcamento.setName("Nome anterior");
        orcamento.setDescription("Descrição anterior");
        orcamento.setStatus(StatusComercialOrcamento.PENDING_ACCEPTANCE);
        orcamento.setStage("Etapa anterior");
        orcamento.setTotalPrice(new BigDecimal("12.00"));
        orcamento.setCreatedDate(OffsetDateTime.now().minusDays(2));

        OrcamentoItem item = new OrcamentoItem();
        item.setOrcamento(orcamento);
        item.setPosition(0);
        item.setTestType("Ensaio anterior");
        item.setSampleCount(2);
        item.setStandard("Norma anterior");
        item.setUnitPrice(new BigDecimal("6.00"));
        orcamento.getItems().add(item);
        return orcamentos.saveAndFlush(orcamento);
    }

    private void criarDocumento(Orcamento orcamento, byte[] conteudo) {
        DocumentoOrcamento documento = new DocumentoOrcamento();
        documento.setOrcamento(orcamento);
        documento.setContent(conteudo);
        documentos.saveAndFlush(documento);
    }

    private MockMultipartFile parteJson(String code, Long companyId, String status) {
        String json = """
                {
                  "code": "%s",
                  "companyId": "%s",
                  "name": "Nome atualizado",
                  "laboratoryName": "Laboratório atualizado",
                  "description": "Descrição atualizada",
                  "scope": "Escopo atualizado",
                  "externalContactName": "Contato próprio",
                  "externalContactEmail": "contato@example.test",
                  "status": "%s",
                  "totalPrice": 15.00,
                  "items": [{
                    "testType": "Ensaio novo",
                    "sampleCount": 3,
                    "standard": null,
                    "unitPrice": 5.00
                  }]
                }
                """.formatted(code, companyId, status);
        return new MockMultipartFile(
                "quote", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private long contarItens() {
        return jdbcTemplate.queryForObject("select count(*) from itens_orcamento", Long.class);
    }

    private byte[] pdfDeTeste(int tamanho) {
        byte[] conteudo = new byte[tamanho];
        byte[] assinatura = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, conteudo, 0, assinatura.length);
        Arrays.fill(conteudo, assinatura.length, conteudo.length, (byte) 'x');
        return conteudo;
    }
}
