package br.ufpr.tcc.auth.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogContextFilterTest {

    private static final String ACCEPTED_FORMAT = "[A-Za-z0-9_-]{8,64}";

    private final RequestLogContextFilter filter = new RequestLogContextFilter();
    private final AtomicReference<String> seenInsideChain = new AtomicReference<>();
    private final FilterChain capturingChain =
            (request, response) -> seenInsideChain.set(MDC.get(MdcKeys.CORRELATION_ID));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletRequest requestWith(String receivedValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        if (receivedValue != null) {
            request.addHeader(RequestLogContextFilter.HEADER_NAME, receivedValue);
        }
        return request;
    }

    /**
     * A auditoria exige a origem em cada evento, e ela sai daqui e nao de cada servico:
     * no contexto de log ela acompanha toda linha da requisicao sem que nenhuma camada
     * precise receber a requisicao como parametro.
     */
    @Test
    @DisplayName("a origem da requisicao fica no contexto de log")
    void originIsPublishedInTheLogContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (passing, response) -> seenInsideChain.set(MDC.get(MdcKeys.ORIGIN)));

        assertThat(seenInsideChain.get()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("requisicao sem o cabecalho ganha identificador gerado e ele volta na resposta")
    void generatesCorrelationIdWhenHeaderIsAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith(null), response, capturingChain);

        assertThat(seenInsideChain.get()).matches(ACCEPTED_FORMAT);
        assertThat(response.getHeader(RequestLogContextFilter.HEADER_NAME))
                .isEqualTo(seenInsideChain.get());
    }

    @Test
    @DisplayName("requisicao com o cabecalho valido reaproveita o valor recebido")
    void reusesReceivedCorrelationId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith("8f1e2d3c-portal-01"), response, capturingChain);

        assertThat(seenInsideChain.get()).isEqualTo("8f1e2d3c-portal-01");
        assertThat(response.getHeader(RequestLogContextFilter.HEADER_NAME)).isEqualTo("8f1e2d3c-portal-01");
    }

    // Quebra de linha dentro do valor e o que permite ao cliente escrever uma
    // linha inteira de log falsa. O valor de dez mil caracteres custa memoria em
    // toda linha registrada pela requisicao.
    @ParameterizedTest
    @ValueSource(strings = {
            "8f1e2d3c\n{\"level\":\"INFO\",\"message\":\"login aprovado\"}",
            "8f1e2d3c\rINJETADO",
            "8f1e2d3c\u0000ff",
            "8f1e2d3c\u0007ff",
            "abc1234",
            "8f1e;2d3c",
            "8f1e 2d3c",
            ""
    })
    @DisplayName("cabecalho malformado nao entra no MDC nem volta na resposta")
    void rejectsMalformedCorrelationId(String receivedValue) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith(receivedValue), response, capturingChain);

        assertThat(seenInsideChain.get()).isNotEqualTo(receivedValue).matches(ACCEPTED_FORMAT);
        assertThat(response.getHeader(RequestLogContextFilter.HEADER_NAME))
                .doesNotContain("\n").doesNotContain("\r").matches(ACCEPTED_FORMAT);
    }

    @Test
    @DisplayName("valor longo demais e recusado como qualquer outro malformado")
    void rejectsCorrelationIdLongerThanTheAcceptedBoundary() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith("x".repeat(65)), response, capturingChain);

        assertThat(seenInsideChain.get()).matches(ACCEPTED_FORMAT);
    }

    @Test
    @DisplayName("o MDC fica vazio depois que a cadeia termina")
    void clearsMdcAfterChain() throws Exception {
        filter.doFilter(requestWith(null), new MockHttpServletResponse(), capturingChain);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    // Excecao subindo pela cadeia e exatamente o caminho em que a limpeza e
    // esquecida, e thread de pool com MDC sujo atribui o identificador de um
    // usuario a requisicao do proximo.
    @Test
    @DisplayName("o MDC fica vazio mesmo quando a cadeia lanca excecao")
    void clearsMdcWhenChainThrows() {
        FilterChain failingChain = (request, response) -> {
            throw new IOException("falha no meio da cadeia");
        };

        assertThatIOException().isThrownBy(
                () -> filter.doFilter(requestWith(null), new MockHttpServletResponse(), failingChain));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("duas requisicoes na mesma thread nao compartilham identificador")
    void sequentialRequestsOnSameThreadDoNotShareCorrelationId() throws Exception {
        filter.doFilter(requestWith(null), new MockHttpServletResponse(), capturingChain);
        String first = seenInsideChain.get();

        filter.doFilter(requestWith(null), new MockHttpServletResponse(), capturingChain);

        assertThat(seenInsideChain.get()).isNotEqualTo(first);
    }
}
