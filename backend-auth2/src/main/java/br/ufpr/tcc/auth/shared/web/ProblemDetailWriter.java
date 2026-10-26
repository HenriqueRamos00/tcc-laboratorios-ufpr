package br.ufpr.tcc.auth.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unico lugar que escreve corpo de erro direto na resposta.
 *
 * Ele existe porque a aplicacao tem duas saidas de erro: o tratador de excecao do
 * controlador, e os pontos de recusa da cadeia de seguranca, que rodam antes do despacho e
 * nunca chegam ao controlador. Com dois escritores, a regra de que respostas irmas sao
 * identicas morre na primeira divergencia, e ela morre em silencio.
 *
 * O corpo e constante por status. Nada de identificador de correlacao aqui: ele muda por
 * requisicao e quebraria a comparacao entre irmas, e a trilha ja o carrega no log, onde
 * ele serve para investigar sem contar nada a quem chamou.
 */
@Component
public class ProblemDetailWriter {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public void write(HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
