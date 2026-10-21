package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.domain.exception.ConflitoServicoException;
import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import br.ufpr.tcc.backend_auth.domain.exception.RecursoNaoEncontradoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.repository.ServicoRepository;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicoService {
    private final ServicoRepository servicos;
    private final Validator validator;

    public void validar(Object request) {
        var erros = validator.validate(request);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros.stream().map(e -> e.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
    }

    public Servico obter(Long id) {
        return servicos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
    }

    public Servico bloquear(Long id) {
        return servicos.bloquear(id).orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
    }

    // Chamado em transações de vínculo/emissão para serializar com desativação e exclusão do serviço.
    public Servico exigirAtivo(Long id) {
        if (id == null) throw new DadosInvalidosException("Serviço é obrigatório");
        Servico servico = servicos.obterParaVinculo(id).orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
        if (!servico.isAtivo()) throw new ConflitoServicoException("Serviço inativo");
        return servico;
    }

    public void verificarCodigo(String codigo, Long id) {
        if (id == null ? servicos.existsByCodigo(codigo) : servicos.existsByCodigoAndIdNot(codigo, id)) {
            throw new ConflitoServicoException("Código de serviço já cadastrado");
        }
    }
}
