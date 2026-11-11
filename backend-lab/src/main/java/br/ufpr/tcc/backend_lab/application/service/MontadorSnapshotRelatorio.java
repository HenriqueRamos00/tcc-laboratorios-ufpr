package br.ufpr.tcc.backend_lab.application.service;

import br.ufpr.tcc.backend_lab.application.dto.RelatorioSnapshot;
import br.ufpr.tcc.backend_lab.application.dto.response.RelatorioDtos;
import br.ufpr.tcc.backend_lab.domain.model.entity.RelatorioTecnico;
import br.ufpr.tcc.backend_lab.domain.model.entity.ExecucaoEnsaio;
import br.ufpr.tcc.backend_lab.domain.model.enums.StatusValidacaoRelatorio;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MontadorSnapshotRelatorio {
    private final ObjectMapper objectMapper;

    public MontadorSnapshotRelatorio(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RelatorioSnapshot atual(RelatorioTecnico relatorio) {
        return atual(relatorio.getExecucao());
    }

    public RelatorioSnapshot atual(ExecucaoEnsaio execucao) {
        return RelatorioDtos.snapshot(execucao);
    }

    public RelatorioSnapshot conteudo(RelatorioTecnico relatorio) {
        if (relatorio.getValidationStatus() == StatusValidacaoRelatorio.VALIDATED) {
            if (relatorio.getValidatedSnapshot() == null || relatorio.getValidatedSnapshot().isBlank()) {
                throw new IllegalStateException("Snapshot do relatório validado está ausente");
            }
            try {
                return objectMapper.readValue(relatorio.getValidatedSnapshot(), RelatorioSnapshot.class);
            } catch (JacksonException exception) {
                throw new IllegalStateException("Snapshot do relatório validado está inválido", exception);
            }
        }
        return atual(relatorio);
    }

    public String serializar(RelatorioSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar o snapshot do relatório", exception);
        }
    }

    public String fingerprint(RelatorioSnapshot snapshot) {
        try {
            byte[] conteudo = serializar(snapshot).getBytes(StandardCharsets.UTF_8);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(conteudo);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }

    public String termosDeBusca(RelatorioSnapshot snapshot) {
        var test = snapshot.test();
        return String.join(" ",
                snapshot.company().name(), snapshot.quote().code(), snapshot.sample().code(),
                snapshot.sample().description(), test.code(), test.type(),
                test.analysisType() == null ? "" : test.analysisType())
                .toLowerCase(Locale.ROOT);
    }
}
