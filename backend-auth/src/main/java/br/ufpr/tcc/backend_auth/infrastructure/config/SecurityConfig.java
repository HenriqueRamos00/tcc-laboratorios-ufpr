package br.ufpr.tcc.backend_auth.infrastructure.config;

import br.ufpr.tcc.backend_auth.infrastructure.security.BearerTokenIntrospector;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, BearerTokenIntrospector introspector) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeError(response, 401, "Credenciais inválidas");
        };
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .oauth2ResourceServer(resource -> resource
                        .opaqueToken(opaque -> opaque.introspector(introspector))
                        .authenticationEntryPoint(unauthorized)
                        .bearerTokenResolver(request -> {
                            String path = request.getRequestURI().substring(request.getContextPath().length());
                            return path.equals("/api/users") || path.startsWith("/api/users/")
                                    || path.equals("/api/admins") || path.startsWith("/api/admins/")
                                    || path.equals("/api/services") || path.startsWith("/api/services/")
                                    || path.equals("/api/companies") || path.startsWith("/api/companies/")
                                    || path.equals("/api/auth/login")
                                    ? resolver.resolve(request) : null;
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/setup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/setup/admin").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").hasRole("APPLICATION_SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasAnyRole("SERVICE_ADMIN", "APPLICATION_SERVICE")
                        .requestMatchers("/api/users", "/api/users/**", "/api/admins", "/api/admins/**",
                                "/api/services", "/api/services/**", "/api/companies", "/api/companies/**").hasRole("SERVICE_ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, failure) ->
                                writeError(response, 403, "Sem permissão")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
