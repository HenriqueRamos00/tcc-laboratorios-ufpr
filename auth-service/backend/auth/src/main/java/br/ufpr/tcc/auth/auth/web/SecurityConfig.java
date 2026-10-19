package br.ufpr.tcc.auth.auth.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationEndpoints(HttpSecurity http) {
        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.oidc(Customizer.withDefaults());
        });
        http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
        http.exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain login(HttpSecurity http) {
        http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .formLogin(form -> form.failureHandler((request, response, failure) ->
                        response.sendRedirect(request.getContextPath() + "/login?error")));
        return http.build();
    }

}
