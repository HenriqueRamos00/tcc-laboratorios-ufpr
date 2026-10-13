package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Cliente;
import br.ufpr.tcc.backend_lab.domain.model.entity.Orcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.ClienteRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcessoClienteAtual {
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;

    public Cliente execute(Long userId) {
        var usuario =
                usuarios.findById(userId)
                        .filter(Usuario::isAtivo)
                        .orElseThrow(
                                () -> RegraNegocioException.naoAutenticado("Não autenticado"));
        if (usuario.getPerfil() != PerfilUsuario.CLIENTE) {
            throw RegraNegocioException.acessoNegado("Sem permissão");
        }
        return clientes.findByUsuarioId(userId)
                .orElseThrow(
                        () -> RegraNegocioException.acessoNegado("Sem cadastro de cliente"));
    }

    public void autorizar(Cliente cliente, Orcamento quote) {
        if (!quote.getEmpresa().isAtivo()
                || cliente.getEmpresas().stream()
                        .noneMatch(e -> e.getId().equals(quote.getEmpresa().getId()))) {
            throw RegraNegocioException.acessoNegado("Sem permissão para este orçamento");
        }
    }
}
