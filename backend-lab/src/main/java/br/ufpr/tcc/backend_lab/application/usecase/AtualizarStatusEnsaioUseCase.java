package br.ufpr.tcc.backend_lab.application.usecase;

import br.ufpr.tcc.backend_lab.application.dto.request.AtualizarStatusEnsaioRequest;
import br.ufpr.tcc.backend_lab.application.dto.response.EnsaioDtos;
import br.ufpr.tcc.backend_lab.application.service.ResolvedorReferenciasEnsaio;
import br.ufpr.tcc.backend_lab.application.service.DisponibilidadeRelatorios;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoRelatorioException;
import br.ufpr.tcc.backend_lab.domain.exception.ConflitoEdicaoEnsaioException;
import br.ufpr.tcc.backend_lab.domain.exception.DadosEnsaioInvalidosException;
import br.ufpr.tcc.backend_lab.domain.exception.EnsaioNaoEncontradoException;
import br.ufpr.tcc.backend_lab.domain.exception.RegraNegocioException;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.HistoricoStatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusEnsaio;
import br.ufpr.tcc.backend_lab.domain.repository.ExecucaoEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.HistoricoStatusEnsaioRepository;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtualizarStatusEnsaioUseCase {
    private static final int LIMITE_JUSTIFICATIVA = 2000;

    private final ExecucaoEnsaioRepository ensaios;
    private final HistoricoStatusEnsaioRepository historico;
    private final UsuarioRepository usuarios;
    private final DisponibilidadeRelatorios disponibilidadeRelatorios;

    public AtualizarStatusEnsaioUseCase(ExecucaoEnsaioRepository ensaios,
            HistoricoStatusEnsaioRepository historico, UsuarioRepository usuarios,
            DisponibilidadeRelatorios disponibilidadeRelatorios) {
        this.ensaios = ensaios;
        this.historico = historico;
        this.usuarios = usuarios;
        this.disponibilidadeRelatorios = disponibilidadeRelatorios;
    }

    @Transactional
    public EnsaioDtos.StatusAtualizado executar(String id, AtualizarStatusEnsaioRequest request,
            Long tecnicoId) {
        validarRequest(request);
        Long chave = ResolvedorReferenciasEnsaio.id(id);
        ExecucaoEnsaio ensaio = ensaios.buscarParaEditar(chave)
                .orElseThrow(() -> new EnsaioNaoEncontradoException("Ensaio não encontrado"));
        if (request.version() != ensaio.getVersion()) {
            throw new ConflitoEdicaoEnsaioException("O ensaio foi alterado por outra sessão");
        }

        if (tecnicoId == null || tecnicoId < 1) {
            throw RegraNegocioException.naoAutenticado("Sessão inválida");
        }
        var tecnico = usuarios.findById(tecnicoId)
                .orElseThrow(() -> RegraNegocioException.naoAutenticado("Sessão inválida"));
        if (!tecnico.isAtivo() || tecnico.getPerfil() != PerfilUsuario.TECNICO) {
            throw RegraNegocioException.acessoNegado("Somente técnicos ativos podem atualizar o status");
        }

        StatusEnsaio novoStatus = resolverNovoStatus(request);
        if ("reject".equals(request.action()) && ensaio.getStatus() == StatusEnsaio.INCOMPLETE) {
            throw RegraNegocioException.conflito("Um ensaio incompleto não pode ser rejeitado");
        }
        if (ensaio.getStatus() == StatusEnsaio.CANCELLED
                && ("reject".equals(request.action()) || "cancel".equals(request.action()))) {
            throw RegraNegocioException.conflito("A ação não está disponível para um ensaio cancelado");
        }
        if (novoStatus == ensaio.getStatus()) {
            return new EnsaioDtos.StatusAtualizado(ensaio.getId().toString(),
                    ensaio.getStatus().codigo(), null, null, ensaio.getVersion(), false);
        }
        if (novoStatus == StatusEnsaio.REPORT_PUBLISHED) {
            if (!disponibilidadeRelatorios.existePublicadoDisponivel(ensaio.getId(), ensaio)) {
                throw new ConflitoRelatorioException(
                        "O relatório só pode ser publicado quando um PDF validado estiver disponível para o cliente",
                        "published-report-unavailable");
            }
        }

        OffsetDateTime alteradoEm = OffsetDateTime.now(ZoneOffset.UTC);
        StatusEnsaio statusAnterior = ensaio.getStatus();
        ensaio.setStatus(novoStatus);
        ensaio.setAtualizadoEm(alteradoEm);
        ExecucaoEnsaio salvo = ensaios.saveAndFlush(ensaio);

        HistoricoStatusEnsaio evento = new HistoricoStatusEnsaio();
        evento.setExecucao(salvo);
        evento.setStatusAnterior(statusAnterior);
        evento.setStatusNovo(novoStatus);
        evento.setAction(request.action());
        evento.setAlteradoEm(alteradoEm);
        evento.setTecnico(tecnico);
        evento.setJustificativa(justificativa(request));
        historico.saveAndFlush(evento);

        return new EnsaioDtos.StatusAtualizado(salvo.getId().toString(), salvo.getStatus().codigo(),
                alteradoEm, EnsaioDtos.tecnico(tecnico), salvo.getVersion(), true);
    }

    private static void validarRequest(AtualizarStatusEnsaioRequest request) {
        if (request == null) {
            throw new DadosEnsaioInvalidosException("Dados de atualização do status são obrigatórios");
        }
        if (request.version() == null || request.version() < 0) {
            throw new DadosEnsaioInvalidosException("Versão do ensaio é obrigatória");
        }
        if (request.action() == null || request.action().isBlank()) {
            throw new DadosEnsaioInvalidosException("Ação de atualização do status é obrigatória");
        }
        if (request.justification() != null && request.justification().length() > LIMITE_JUSTIFICATIVA) {
            throw new DadosEnsaioInvalidosException("A justificativa deve ter no máximo 2000 caracteres");
        }
    }

    private static StatusEnsaio resolverNovoStatus(AtualizarStatusEnsaioRequest request) {
        String justificativa = justificativa(request);
        return switch (request.action()) {
            case "set-status" -> {
                StatusEnsaio destino = resolverDestino(request.targetStatus());
                if (destino == StatusEnsaio.CANCELLED) {
                    throw new DadosEnsaioInvalidosException(
                            "Use a ação cancelar com justificativa para cancelar o ensaio");
                }
                yield destino;
            }
            case "reject" -> {
                exigirJustificativa(justificativa, "Rejeitar");
                if (request.targetStatus() != null && !request.targetStatus().isBlank()) {
                    throw new DadosEnsaioInvalidosException("Rejeitar não aceita status de destino");
                }
                yield StatusEnsaio.INCOMPLETE;
            }
            case "cancel" -> {
                exigirJustificativa(justificativa, "Cancelar");
                if (request.targetStatus() != null && !request.targetStatus().isBlank()) {
                    throw new DadosEnsaioInvalidosException("Cancelar não aceita status de destino");
                }
                yield StatusEnsaio.CANCELLED;
            }
            default -> throw new DadosEnsaioInvalidosException("Ação de atualização do status desconhecida");
        };
    }

    private static StatusEnsaio resolverDestino(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new DadosEnsaioInvalidosException("Status de destino é obrigatório");
        }
        try {
            return StatusEnsaio.deCodigo(codigo);
        } catch (IllegalArgumentException exception) {
            throw new DadosEnsaioInvalidosException("Status de destino desconhecido");
        }
    }

    private static String justificativa(AtualizarStatusEnsaioRequest request) {
        return request.justification() == null ? null : request.justification().trim();
    }

    private static void exigirJustificativa(String justificativa, String acao) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new DadosEnsaioInvalidosException("Justificativa é obrigatória para " + acao.toLowerCase());
        }
    }
}
