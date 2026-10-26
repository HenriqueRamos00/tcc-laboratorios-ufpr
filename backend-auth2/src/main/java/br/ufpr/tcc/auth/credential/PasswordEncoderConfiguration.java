package br.ufpr.tcc.auth.credential;

import br.ufpr.tcc.auth.credential.model.PasswordHashing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Publica o unico codificador de senha da aplicacao. */
@Configuration
class PasswordEncoderConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordHashing.encoder();
    }
}
