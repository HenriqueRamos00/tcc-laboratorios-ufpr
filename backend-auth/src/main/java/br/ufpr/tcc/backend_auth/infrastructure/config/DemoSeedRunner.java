package br.ufpr.tcc.backend_auth.infrastructure.config;

import br.ufpr.tcc.backend_auth.application.service.TokenOpaco;
import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_auth.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.model.entity.TokenServico;
import br.ufpr.tcc.backend_auth.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import br.ufpr.tcc.backend_auth.domain.repository.TokenServicoRepository;
import br.ufpr.tcc.backend_auth.domain.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {
    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final ServicoRepository servicos;
    private final TokenServicoRepository tokens;
    private final PasswordEncoder encoder;
    private final String serviceToken;
    private final long expirationSeconds;

    public DemoSeedRunner(UsuarioRepository usuarios, EmpresaRepository empresas, ServicoRepository servicos,
            TokenServicoRepository tokens, PasswordEncoder encoder,
            @Value("${demo.seed.service-token:}") String serviceToken,
            @Value("${service.token.expiration-seconds:2592000}") long expirationSeconds) {
        if (!serviceToken.matches("svc_[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("O seed requer BACKEND_AUTH_SERVICE_TOKEN no formato svc_ seguido de 43 caracteres base64url");
        }
        if (expirationSeconds <= 0 || expirationSeconds > 31536000) {
            throw new IllegalArgumentException("A validade do token de serviço deve estar entre 1 e 31536000 segundos");
        }
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.servicos = servicos;
        this.tokens = tokens;
        this.encoder = encoder;
        this.serviceToken = serviceToken;
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Servico portal = servicos.findByCodigo("portal-web").orElseGet(() -> {
            Servico novo = new Servico();
            novo.setNome("Portal Web");
            novo.setCodigo("portal-web");
            return servicos.save(novo);
        });
        Empresa empresa = empresa("EMPRESA DE ENERGIA", "04252011000110");
        Empresa distribuidora = empresa("DISTRIBUIDORA DE ENERGIA", "11222333000181");
        conta("Administrador Lactec", "lactec@lactec.com", null, null);
        conta("Cliente 1", "client1@lactec.com", portal, empresa);
        conta("Cliente 2", "client2@lactec.com", portal, distribuidora);

        TokenServico token = tokens.findByTokenHash(TokenOpaco.hash(serviceToken)).orElseGet(() -> {
            TokenServico novo = new TokenServico();
            novo.setNome("Portal Web - demonstração local");
            novo.setServico(portal);
            novo.setTokenHash(TokenOpaco.hash(serviceToken));
            novo.setCriadoEm(Instant.now());
            return novo;
        });
        if (!Objects.equals(token.getServico().getId(), portal.getId())) {
            throw new IllegalStateException("O token do seed já pertence a outro serviço; configure outro BACKEND_AUTH_SERVICE_TOKEN");
        }
        if (token.getExpiraEm() == null || !token.getExpiraEm().isAfter(Instant.now())) {
            token.setExpiraEm(Instant.now().plusSeconds(expirationSeconds));
            tokens.save(token);
        }
    }

    private Empresa empresa(String nome, String cnpj) {
        return empresas.findByCnpj(cnpj).orElseGet(() -> {
            Empresa nova = new Empresa();
            nova.setNome(nome);
            nova.setCnpj(cnpj);
            return empresas.save(nova);
        });
    }

    private void conta(String nome, String email, Servico servico, Empresa empresa) {
        boolean administrador = servico == null;
        Usuario existente = usuarios.findByEmail(email).orElse(null);
        if (existente != null) {
            if (existente.isAdministradorServico() != administrador
                    || existente.getPerfil() != (administrador ? null : PerfilUsuario.CLIENTE)
                    || !Objects.equals(existente.getServico() == null ? null : existente.getServico().getId(),
                            servico == null ? null : servico.getId())) {
                throw new IllegalStateException("A conta reservada pelo seed já tem outro escopo: " + email);
            }
            return;
        }
        Usuario nova = new Usuario();
        nova.setNome(nome);
        nova.setEmail(email);
        // As senhas públicas são exclusivas do seed local; o cadastro mantém sua política normal.
        nova.setSenhaHash(encoder.encode(email));
        nova.setAdministradorServico(administrador);
        if (!administrador) {
            nova.setPerfil(PerfilUsuario.CLIENTE);
            nova.setServico(servico);
            nova.getEmpresas().add(empresa);
        }
        usuarios.save(nova);
    }
}
