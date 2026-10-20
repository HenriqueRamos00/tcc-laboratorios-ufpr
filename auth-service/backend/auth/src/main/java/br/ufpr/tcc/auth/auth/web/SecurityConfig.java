package br.ufpr.tcc.auth.auth.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import br.ufpr.tcc.auth.auth.application.ApiKeyService;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import br.ufpr.tcc.auth.auth.security.AdminSessionValidationFilter;
import br.ufpr.tcc.auth.auth.security.ApiKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain integrationEndpoints(HttpSecurity http, ApiKeyService apiKeys)
            throws Exception {
        http.securityMatcher("/api/integrations/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys),
                        AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain setupEndpoints(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/setup/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain adminEndpoints(HttpSecurity http, AuthUserRepository users,
            SecurityContextRepository securityContextRepository) throws Exception {
        http.securityMatcher("/api/admin/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/admin/auth/login", "/api/admin/auth/csrf").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .addFilterAfter(new AdminSessionValidationFilter(users),
                        SecurityContextHolderFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain authorizationEndpoints(HttpSecurity http) throws Exception {
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
    @Order(5)
    SecurityFilterChain login(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.failureHandler((request, response, failure) ->
                        response.sendRedirect(request.getContextPath() + "/login?error")));
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private static AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado");
    }

    private static AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, exception) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Sem permissão");
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
