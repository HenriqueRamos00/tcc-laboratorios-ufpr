package br.ufpr.tcc.backend_lab.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Usa o X-Correlation-ID recebido do C# ou gera um UUID para rastrear a requisição
 * nos logs e na resposta, limpando o contexto de logs ao finalizar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);
	private static final String HEADER_NAME = "X-Correlation-ID";
	private static final String MDC_KEY = "correlationId";
	private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String providedId = request.getHeader(HEADER_NAME);
		String correlationId = providedId != null && VALID_ID.matcher(providedId).matches()
				? providedId
				: UUID.randomUUID().toString();

		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			LOGGER.info("Requisição HTTP concluída: método={} caminho={} status={}",
					request.getMethod(), request.getRequestURI(), response.getStatus());
			MDC.remove(MDC_KEY);
		}
	}
}
