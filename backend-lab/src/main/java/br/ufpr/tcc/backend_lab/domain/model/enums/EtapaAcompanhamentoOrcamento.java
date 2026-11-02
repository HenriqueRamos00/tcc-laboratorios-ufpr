package br.ufpr.tcc.backend_lab.domain.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

public enum EtapaAcompanhamentoOrcamento {
    QUALIFICACAO("Qualificação"),
    ANALISE_DA_AREA("Em análise pela área", "Em revisão"),
    ELABORANDO_PROPOSTA("Elaborando proposta"),
    NEGOCIACAO("Negociação", "Em negociação"),
    APROVADO_PELO_CLIENTE("Aprovado pelo cliente"),
    AGUARDANDO_AMOSTRA("Aguardando entrega da amostra"),
    RECEBIDO("Recebido", "Fechado ganho"),
    PROTOCOLADO("Protocolado"),
    EM_EXECUCAO("Em execução"),
    COMPLETO("Completo"),
    VALIDADO("Validado"),
    FINALIZADO("Finalizado"),
    ELABORANDO_RELATORIO("Elaborando relatório"),
    RELATORIO_PUBLICADO("Relatório publicado"),
    CANCELADO("Cancelado", "Fechado perdido"),
    RECUSADO("Fechado recusado Lactec", "Recusado");

    private final String texto;
    private final String[] aliases;

    EtapaAcompanhamentoOrcamento(String texto, String... aliases) {
        this.texto = texto;
        this.aliases = aliases;
    }

    public String texto() {
        return texto;
    }

    public static EtapaAcompanhamentoOrcamento deTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Etapa de acompanhamento é obrigatória");
        }
        String normalizado = normalizar(texto);
        return Arrays.stream(values())
                .filter(etapa -> Stream.concat(Stream.of(etapa.texto), Arrays.stream(etapa.aliases))
                        .anyMatch(alias -> normalizar(alias).equals(normalizado)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Etapa de acompanhamento desconhecida"));
    }

    private static String normalizar(String texto) {
        return texto.trim().toLowerCase(Locale.ROOT);
    }
}
