package br.ufpr.tcc.backend_lab.infrastructure.config;

import br.ufpr.tcc.backend_lab.infrastructure.security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/health",
                                                "/api/auth/internal/login",
                                                "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/technicians",
                                                "/api/technicians/**",
                                                "/api/companies",
                                                "/api/companies/**",
                                                "/api/clients",
                                                "/api/clients/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/equipments", "/api/equipments/**")
                                        .hasRole("TECNICO")
                                        .requestMatchers(
                                                "/api/technician/quotes",
                                                "/api/technician/quotes/**")
                                        .hasRole("TECNICO")
                                        .requestMatchers("/api/quotes", "/api/quotes/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/admin/dashboard")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/client/**")
                                        .hasRole("CLIENTE")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(authenticationEntryPoint())
                                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(401);
            response.setHeader(
                    "WWW-Authenticate",
                    "Bearer"); // avisa a api requisitante qual tipo de auth espera
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Não autenticado\"}");
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Sem permissão\"}");
        };
    }
}
