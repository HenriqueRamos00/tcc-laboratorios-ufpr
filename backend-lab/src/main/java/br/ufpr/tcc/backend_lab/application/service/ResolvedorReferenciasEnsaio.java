package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.request.EnsaioRequest;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.model.entity.Amostra;
import br.ufpr.tcc.backend_lab.domain.model.entity.Equipamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.InstrumentoLaboratorial;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.AmostraRepository;
import br.ufpr.tcc.backend_lab.domain.repository.EquipamentoRepository;
import br.ufpr.tcc.backend_lab.domain.repository.InstrumentoLaboratorialRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ResolvedorReferenciasEnsaio {
    private static final Pattern ID_POSITIVO = Pattern.compile("[1-9][0-9]*");
    private final AmostraRepository amostras;
    private final UsuarioRepository usuarios;
    private final InstrumentoLaboratorialRepository instrumentos;
    private final EquipamentoRepository equipamentos;

    public ResolvedorReferenciasEnsaio(AmostraRepository amostras, UsuarioRepository usuarios,
            InstrumentoLaboratorialRepository instrumentos, EquipamentoRepository equipamentos) {
        this.amostras = amostras;
        this.usuarios = usuarios;
        this.instrumentos = instrumentos;
        this.equipamentos = equipamentos;
    }

    public Referencias resolver(EnsaioRequest request, Usuario responsavelAnterior) {
        Amostra amostra = amostras.findById(id(request.sampleId()))
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Amostra não encontrada"));
        validarEmpresaDoEquipamento(amostra);
        Usuario responsavel = usuarios.findById(id(request.responsibleId()))
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Técnico não encontrado"));
        if (responsavel.getPerfil() != PerfilUsuario.TECNICO) {
            throw new EnsaioNaoEncontradoException("Técnico não encontrado");
        }
        boolean mantendoResponsavelInativo = responsavelAnterior != null
                && responsavelAnterior.getId().equals(responsavel.getId());
        if (!responsavel.isAtivo() && !mantendoResponsavelInativo) {
            throw new DadosEnsaioInvalidosException("O técnico selecionado está inativo");
        }
        Set<Long> idsInstrumentos = new LinkedHashSet<>();
        if (request.equipmentIds() != null) {
            for (String id : request.equipmentIds()) idsInstrumentos.add(id(id));
        }
        List<InstrumentoLaboratorial> selecionados = instrumentos.findAllById(idsInstrumentos);
        if (selecionados.size() != idsInstrumentos.size()) {
            throw new EnsaioNaoEncontradoException("Instrumento laboratorial não encontrado");
        }
        return new Referencias(amostra, responsavel, selecionados);
    }

    private void validarEmpresaDoEquipamento(Amostra amostra) {
        Equipamento analisado = amostra.getEquipamentoAnalisado();
        if (analisado != null && !analisado.getEmpresa().getId().equals(amostra.getOrcamento().getEmpresa().getId())) {
            throw new DadosEnsaioInvalidosException(
                    "O equipamento analisado deve pertencer à empresa do orçamento da amostra");
        }
    }

    public static Long id(String value) {
        try {
            if (value == null || !ID_POSITIVO.matcher(value).matches()) throw new NumberFormatException();
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new DadosEnsaioInvalidosException("ID inválido");
        }
    }

    public record Referencias(Amostra amostra, Usuario responsavel, List<InstrumentoLaboratorial> instrumentos) { }
}
