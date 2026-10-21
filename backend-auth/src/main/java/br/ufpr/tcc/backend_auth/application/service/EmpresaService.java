package br.ufpr.tcc.backend_auth.application.service;

import br.ufpr.tcc.backend_auth.application.dto.request.EmpresaRequest;
import br.ufpr.tcc.backend_auth.application.validation.CnpjValidator;
import br.ufpr.tcc.backend_auth.domain.exception.ConflitoContaException;
import br.ufpr.tcc.backend_auth.domain.exception.DadosInvalidosException;
import br.ufpr.tcc.backend_auth.domain.exception.RecursoNaoEncontradoException;
import br.ufpr.tcc.backend_auth.domain.model.entity.Empresa;
import br.ufpr.tcc.backend_auth.domain.repository.EmpresaRepository;
import jakarta.validation.Validator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmpresaService {
    private final EmpresaRepository empresas;
    private final Validator validator;
    private final CnpjValidator cnpjValidator;

    public void validar(EmpresaRequest request, Long id) {
        var erros = validator.validate(request);
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros.stream().map(e -> e.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        cnpjValidator.validar(request.cnpj());
        if (id == null ? empresas.existsByCnpj(request.cnpj()) : empresas.existsByCnpjAndIdNot(request.cnpj(), id)) {
            throw new ConflitoContaException("CNPJ já cadastrado");
        }
    }

    public Empresa obter(Long id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));
    }

    public Empresa bloquear(Long id) {
        return empresas.bloquear(id).orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));
    }

    public Set<Empresa> associar(List<Long> ids) {
        if (ids.isEmpty()) return new LinkedHashSet<>();
        Set<Long> distintos = new LinkedHashSet<>(ids);
        var encontradas = empresas.obterParaVinculo(distintos);
        if (encontradas.size() != distintos.size()) {
            throw new RecursoNaoEncontradoException("Uma ou mais empresas não foram encontradas");
        }
        return new LinkedHashSet<>(encontradas);
    }
}
