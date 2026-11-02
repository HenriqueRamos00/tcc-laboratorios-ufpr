package br.ufpr.tcc.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada do servico de autenticacao compartilhado pelas aplicacoes do
 * Lactec.
 *
 * A configuracao automatica do usuario em memoria fica de fora de proposito. Ela cria um
 * usuario padrao e escreve a senha gerada dele no log da subida sempre que nao existe
 * UserDetailsService no contexto, e aqui nunca vai existir: quem autentica sao os dois
 * diretorios de token opaco. Medido antes da exclusao, a linha "Using generated security
 * password" aparecia em toda subida, inclusive na suite, o que contraria a regra de nunca
 * registrar credencial em log.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
