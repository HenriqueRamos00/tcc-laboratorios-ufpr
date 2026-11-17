package br.ufpr.tcc.auth.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Publica o contexto de log da requisicao: o identificador de correlacao, devolvido tambem no
 * cabecalho da resposta para que uma chamada possa ser seguida do comeco ao fim, e o endereco
 * de onde ela chegou, que a auditoria exige em cada evento.
 *
 * Os dois saem daqui e nao de cada servico porque, no MDC, eles passam a acompanhar toda linha
 * de log da requisicao sem que nenhuma camada precise receber a requisicao como parametro.
 *
 * Isto e propagacao de identificador de correlacao, nao tracing distribuido: nao existe
 * span, nao existe amostragem e nao existe coletor. Por isso o cabecalho aceito e
 * X-Request-Id e nao traceparent, cujo formato carrega identificador de span e sinalizador
 * de amostragem que este servico nao tem como honrar, e que nao tem contrapartida de
 * resposta na especificacao.
 *
 * Valor recebido fora do formato aceito nao entra no MDC e nao e devolvido: ele e
 * substituido por um gerado. A requisicao nao e recusada por isso, porque correlacao e
 * assunto de log e nao de negocio, e responder erro transformaria um cabecalho decorativo
 * em motivo de indisponibilidade.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogContextFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";

    /**
     * Letra, digito, hifen e sublinhado, de oito a sessenta e quatro caracteres. O conjunto
     * exclui quebra de linha, caractere de controle e espaco, que sao o que permite forjar
     * uma linha de log, e o teto corta o valor enorme que seria repetido em cada linha. A
     * expressao nao leva ancora porque matches() avalia a cadeia inteira.
     */
    private static final Pattern ACCEPTED_VALUE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        MdcKeys.publish(MdcKeys.CORRELATION_ID, correlationId);
        MdcKeys.publish(MdcKeys.ORIGIN, request.getRemoteAddr());
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveCorrelationId(String receivedValue) {
        if (receivedValue != null && ACCEPTED_VALUE.matcher(receivedValue).matches()) {
            return receivedValue;
        }
        return UUID.randomUUID().toString();
    }
}
