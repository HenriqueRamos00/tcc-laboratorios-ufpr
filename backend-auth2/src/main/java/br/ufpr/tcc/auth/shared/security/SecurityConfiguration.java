package br.ufpr.tcc.auth.shared.security;

import br.ufpr.tcc.auth.shared.web.ProblemDetailWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * A cadeia de seguranca.
 *
 * Ela e fechada por padrao: tudo o que nao estiver liberado nominalmente exige credencial
 * de aplicacao. Rota nova nasce fechada, inclusive a que alguem acrescentar sem ler esta
 * classe, que e o contrario do que acontece quando a lista e de proibicoes.
 *
 * As tres desativacoes explicitas nao sao enfeite. Sem elas o proprio framework publica um
 * POST /login que responde 302 fora da matriz de codigos, um GET /logout, e um usuario
 * padrao com senha gerada no log da subida.
 */
@Configuration
class SecurityConfiguration {

    /**
     * A ordem das regras e o que decide, porque a primeira que casa responde e as seguintes
     * nao sao consultadas. Cada uma delas existe por um motivo medido.
     *
     * O despacho de erro e liberado porque o contentor trata erro com um segundo despacho
     * interno para a rota de erro, e esse despacho tambem passa por aqui. Sem libera lo,
     * toda falha de validacao, todo corpo ilegivel e todo caminho inexistente voltavam como
     * 401 com desafio, que no consumidor significa fonte indisponivel. Medido com servidor
     * de verdade: o simulador de requisicao nao faz esse segundo despacho, entao a suite
     * ficava verde com a aplicacao quebrada.
     *
     * A sonda de prontidao e a de vivacidade ficam abaixo de /health, e e a de prontidao que
     * o healthcheck do contentor consulta.
     *
     * As duas rotas abertas sao as que existem para entrar, e elas sao liberadas POR VERBO.
     * Isso nao e simetria: liberar o caminho inteiro entrega os outros verbos ao despacho do
     * servidor, e foi medido na aplicacao no ar que OPTIONS respondia 200 com
     * "Allow: POST,OPTIONS" e GET respondia 405 com "Allow: POST", enquanto caminho
     * inventado respondia 401. Allow confirma que o caminho existe, e na rota de instalacao
     * o par de respostas ainda contava se a instalacao ja havia sido feita. Com o verbo
     * preso, todo verbo diferente de POST cai no 401 uniforme de caminho inexistente.
     *
     * Tres familias de rota exigem credencial de APLICACAO, e nao credencial autenticada
     * qualquer: o login de cliente, o pedido de recuperacao, o cadastro de cliente e as rotas
     * de empresa. O motivo nao e hierarquia, e tipo: o tratador dessas rotas le o chamador como
     * aplicacao para derivar o escopo, e com credencial administrativa a identidade nao e do
     * tipo esperado. Medido na aplicacao no ar antes da correcao: o resolvedor do framework
     * entregava nulo e as cinco rotas respondiam 500, com pilha no log, status fora da matriz, e
     * no consumidor a leitura de fonte indisponivel. Recusar na cadeia troca isso por 403.
     *
     * As rotas que aceitam as duas credenciais sao as que derivam o escopo do chamador de forma
     * explicita, pela leitura e pela alteracao de conta: aplicacao enxerga as contas dela,
     * administrador atravessa aplicacoes.
     *
     * A decisao sobre 405 fica escrita aqui, porque ela precisa estar escrita e nao
     * descoberta em producao. Nos caminhos fechados a cadeia recusa antes do despacho, entao
     * verbo que nao existe responde 401 sem credencial e 403 com credencial de aplicacao,
     * nos dois casos sem Allow. So quem apresentou credencial administrativa chega ao
     * servidor de rotas e recebe 405 com Allow, e isso e aceito: a norma manda enviar Allow
     * em todo 405, e quem recebe esse oraculo ja pode listar a superficie administrativa
     * inteira na rota ao lado.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ServiceTokenAuthenticationFilter tokenFilter,
            ProblemDetailWriter problemDetailWriter) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                        .permitAll()
                        .requestMatchers("/health", "/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login",
                                "/api/setup/admin").permitAll()
                        .requestMatchers("/api/services", "/api/services/**", "/api/admins",
                                "/api/admins/**")
                        .hasAuthority(ServiceTokenAuthenticationFilter.ADMINISTRATOR_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/users")
                        .hasAuthority(ServiceTokenAuthenticationFilter.APPLICATION_AUTHORITY)
                        .requestMatchers("/api/auth/login", "/api/auth/recoveries",
                                "/api/companies", "/api/companies/**")
                        .hasAuthority(ServiceTokenAuthenticationFilter.APPLICATION_AUTHORITY)
                        .requestMatchers("/api/auth/**", "/api/users", "/api/users/**")
                        .authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityConfiguration
                                .challengeFor(problemDetailWriter))
                        .accessDeniedHandler((request, response, denied) ->
                                problemDetailWriter.write(response, HttpStatus.FORBIDDEN,
                                        "Operacao nao permitida para esta credencial")))
                .build();
    }

    /**
     * O 401 da credencial da aplicacao, e ele leva desafio.
     *
     * Medido no consumidor: 401 com desafio significa fonte indisponivel, que e exatamente o
     * que uma integracao mal configurada e. O 401 de senha errada sai do controlador, sem
     * desafio, e por isso aparece como credencial incorreta na tela de quem digitou.
     */
    private static AuthenticationEntryPoint challengeFor(
            ProblemDetailWriter problemDetailWriter) {
        return (request, response, failure) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            problemDetailWriter.write(response, HttpStatus.UNAUTHORIZED,
                    "Credencial de aplicacao ausente ou invalida");
        };
    }
}
