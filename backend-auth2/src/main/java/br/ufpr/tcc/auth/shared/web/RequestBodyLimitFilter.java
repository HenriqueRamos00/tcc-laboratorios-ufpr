package br.ufpr.tcc.auth.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Teto de tamanho para o corpo da requisicao.
 *
 * Sem teto, o corpo e lido inteiro para a memoria antes de qualquer decisao: a validacao que
 * recusa um campo por tamanho roda depois da leitura, entao o custo e pago antes de a recusa
 * existir. Algumas dezenas de corpos grandes em paralelo esgotam a memoria do processo, e isso
 * nao exige credencial nenhuma nas rotas abertas.
 *
 * O filtro roda antes da cadeia de seguranca de proposito. Recusar cedo e o ponto: quem chega
 * com carga grande demais nao deve nem consumir a resolucao de credencial.
 *
 * O status e 413, que a norma reserva para carga acima do que o servidor aceita, e nao o 400
 * de corpo malformado: o corpo nao esta errado, ele e grande. O consumidor le qualquer status
 * fora da matriz como fonte indisponivel, e para corpo de dois megabytes essa leitura esta
 * certa: nenhum pedido legitimo deste servico chega perto do teto.
 *
 * Requisicao em pedacos nao declara tamanho, e por isso ela e tratada de outro jeito: o
 * filtro troca a entrada por uma que conta os bytes enquanto eles chegam e para no mesmo
 * teto. Antes disso, este filtro so olhava o tamanho declarado, e a versao anterior deste
 * comentario afirmava que o teto de cadeia do leitor de JSON cobria o caso. A afirmacao
 * estava errada, e foi medida: com Transfer-Encoding chunked, corpos de 200 KB e de 8 MB
 * foram lidos e interpretados inteiros contra a rota aberta de entrada administrativa, e
 * responderam 400 de validacao, enquanto o mesmo corpo com tamanho declarado respondia 413.
 * Ou seja, bastava omitir um cabecalho para o teto deixar de existir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final long maximumBodyBytes;

    private final ProblemDetailWriter problemDetailWriter;

    RequestBodyLimitFilter(@Value("${auth.request.maximum-body-bytes}") long maximumBodyBytes,
            ProblemDetailWriter problemDetailWriter) {
        this.maximumBodyBytes = maximumBodyBytes;
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maximumBodyBytes) {
            problemDetailWriter.write(response, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Corpo da requisicao acima do tamanho aceito");
            return;
        }
        if (declaredLength < 0) {
            filterChain.doFilter(new CountedBodyRequest(request, maximumBodyBytes), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Requisicao cujo corpo e contado durante a leitura.
     *
     * Trocar a entrada e o unico ponto onde o teto pode ser cobrado sem tamanho declarado, e
     * ele continua sendo cobrado antes de o corpo inteiro existir em memoria: quem passa do
     * limite para no byte seguinte, nao no fim do envio.
     */
    private static final class CountedBodyRequest extends HttpServletRequestWrapper {

        private final long maximumBodyBytes;

        private CountedBodyRequest(HttpServletRequest request, long maximumBodyBytes) {
            super(request);
            this.maximumBodyBytes = maximumBodyBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CountedBody(super.getInputStream(), maximumBodyBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding)));
        }
    }

    /** Entrada que conta o que passou e recusa no teto. */
    private static final class CountedBody extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maximumBodyBytes;
        private long consumedBytes;

        private CountedBody(ServletInputStream delegate, long maximumBodyBytes) {
            this.delegate = delegate;
            this.maximumBodyBytes = maximumBodyBytes;
        }

        private void count(int readBytes) {
            if (readBytes < 0) {
                return;
            }
            consumedBytes += readBytes;
            if (consumedBytes > maximumBodyBytes) {
                throw new BodyAboveCeilingException(maximumBodyBytes);
            }
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            count(value < 0 ? -1 : 1);
            return value;
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            int readBytes = delegate.read(destination, offset, length);
            count(readBytes);
            return readBytes;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
