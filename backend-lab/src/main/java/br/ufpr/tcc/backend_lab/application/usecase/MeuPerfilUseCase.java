package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.PatchMeuPerfilRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.MeuPerfilResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.MeuPerfilResponse.EmpresaPerfilResponse;
import br.ufpr.tcc.backend_lab.application.dto.response.MeuPerfilResponse.PasswordPolicyResponse;
import br.ufpr.tcc.backend_lab.application.validation.SenhaValidator;
import br.ufpr.tcc.backend_lab.domain.exception.PerfilInvalidoException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaAtualIncorretaException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.exception.SenhaNovaInvalidaException;
import br.ufpr.tcc.backend_lab.application.service.VerificacaoSenhaAtual;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeuPerfilUseCase {
    private static final int MINIMO_CARACTERES_NOVA_SENHA = 12;
    private static final int LIMITE_CARACTERES_SENHA = 72;
    private static final int LIMITE_BYTES_SENHA_UTF8 = 72;
    private static final PasswordPolicyResponse POLITICA_SENHA =
            new PasswordPolicyResponse(MINIMO_CARACTERES_NOVA_SENHA, LIMITE_CARACTERES_SENHA,
                    LIMITE_BYTES_SENHA_UTF8, true, true, true, true);

    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final PasswordEncoder passwordEncoder;
    private final SenhaValidator senhaValidator;
    private final EmailChangeService emailChangeService;
    private final VerificacaoSenhaAtual verificacaoSenhaAtual;

    @Transactional(readOnly = true)
    public MeuPerfilResponse consultar(Long usuarioId) {
        Usuario usuario = usuarioAtual(usuarioId);
        return responseFor(usuario);
    }

    @Transactional
    public MeuPerfilResponse atualizar(Long usuarioId, PatchMeuPerfilRequest request) {
        Usuario usuario = usuarioParaAtualizacao(usuarioId);
        if (!request.getUnsupportedFields().isEmpty()) {
            throw new PerfilInvalidoException("O perfil contém campos que não podem ser alterados.");
        }

        boolean cliente = usuario.getPerfil() == PerfilUsuario.CLIENTE;
        if (!cliente && (request.getSuppliedFields().contains("phone")
                || request.getSuppliedFields().contains("position"))) {
            throw new PerfilInvalidoException("Este perfil permite alterar somente o nome.");
        }

        usuario.setNome(request.getName().trim());
        if (cliente) {
            Cliente cadastro = cadastroCliente(usuarioId);
            if (request.getSuppliedFields().contains("phone")) {
                cadastro.setPhone(valorObrigatorio(request.getPhone(), "Telefone"));
            }
            if (request.getSuppliedFields().contains("position")) {
                cadastro.setPosition(valorObrigatorio(request.getPosition(), "Cargo"));
            }
            clientes.saveAndFlush(cadastro);
        }
        usuarios.saveAndFlush(usuario);
        return responseFor(usuario);
    }

    @Transactional(noRollbackFor = SenhaAtualIncorretaException.class)
    public void alterarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        validarNovaSenha(novaSenha);
        Usuario usuario = usuarioParaAtualizacao(usuarioId);
        verificacaoSenhaAtual.verificar(usuario, senhaAtual);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.invalidateCredentials();
        usuarios.saveAndFlush(usuario);
        emailChangeService.invalidar(usuarioId);
    }

    private void validarNovaSenha(String novaSenha) {
        try {
            senhaValidator.validar(novaSenha);
        } catch (RegraNegocioException falha) {
            throw new SenhaNovaInvalidaException(falha.getMessage());
        }
        if (novaSenha == null || novaSenha.codePointCount(0, novaSenha.length()) < MINIMO_CARACTERES_NOVA_SENHA
                || novaSenha.codePoints().noneMatch(Character::isUpperCase)
                || novaSenha.codePoints().noneMatch(Character::isLowerCase)
                || novaSenha.codePoints().noneMatch(Character::isDigit)) {
            throw new SenhaNovaInvalidaException(
                    "Use pelo menos 12 caracteres, com letra maiúscula, minúscula e número.");
        }
    }

    private MeuPerfilResponse responseFor(Usuario usuario) {
        List<String> editableFields = usuario.getPerfil() == PerfilUsuario.CLIENTE
                ? List.of("name", "phone", "position") : List.of("name");
        List<EmpresaPerfilResponse> companies = null;
        String phone = null;
        String position = null;
        if (usuario.getPerfil() == PerfilUsuario.CLIENTE) {
            Cliente cliente = cadastroCliente(usuario.getId());
            phone = cliente.getPhone();
            position = cliente.getPosition();
            companies = cliente.getEmpresas().stream()
                    .sorted(Comparator.comparing(Empresa::getId))
                    .map(empresa -> new EmpresaPerfilResponse(
                            empresa.getId().toString(), empresa.getLegalName()))
                    .toList();
        }
        boolean tecnico = usuario.getPerfil() == PerfilUsuario.TECNICO;
        List<String> actions = emailChangeService.disponivelPara(usuario.getPerfil())
                ? List.of("change-email", "change-password")
                : List.of("change-password");
        return new MeuPerfilResponse(
                usuario.getNome(), usuario.getEmail(), usuario.getPerfil().name(), usuario.isAtivo(),
                tecnico ? usuario.getUnidade() : null,
                tecnico ? usuario.getEspecialidade() : null,
                phone, position, editableFields, actions, companies,
                emailChangeService.pendente(usuario.getId()),
                POLITICA_SENHA);
    }

    private Usuario usuarioAtual(Long id) {
        return usuarios.findById(id).filter(Usuario::isAtivo)
                .orElseThrow(() -> new IllegalStateException("Conta autenticada não disponível"));
    }

    private Usuario usuarioParaAtualizacao(Long id) {
        return usuarios.findByIdForUpdate(id).filter(Usuario::isAtivo)
                .orElseThrow(() -> new IllegalStateException("Conta autenticada não disponível"));
    }

    private Cliente cadastroCliente(Long usuarioId) {
        return clientes.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Cadastro de cliente não disponível"));
    }

    private String valorObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new PerfilInvalidoException(campo + " é obrigatório.");
        }
        return valor.trim();
    }
}
