package br.ufpr.tcc.backend_lab.infrastructure.config;

import br.ufpr.tcc.backend_lab.domain.model.entity.PerfilUsuario;
import br.ufpr.tcc.backend_lab.domain.model.entity.Usuario;
import br.ufpr.tcc.backend_lab.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class UsuarioSeedConfig implements ApplicationRunner {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${auth.seed.admin.name:}")
	private String adminName;

	@Value("${auth.seed.admin.email:}")
	private String adminEmail;

	@Value("${auth.seed.admin.password:}")
	private String adminPassword;

	@Value("${auth.seed.technician.name:}")
	private String technicianName;

	@Value("${auth.seed.technician.email:}")
	private String technicianEmail;

	@Value("${auth.seed.technician.password:}")
	private String technicianPassword;

	@Override
	public void run(ApplicationArguments args) {
		createIfConfigured(adminName, adminEmail, adminPassword, PerfilUsuario.ADMIN);
		createIfConfigured(technicianName, technicianEmail, technicianPassword, PerfilUsuario.TECNICO);
	}

	private void createIfConfigured(String name, String email, String password, PerfilUsuario perfil) {
		if (isBlank(name) || isBlank(email) || isBlank(password)
				|| usuarioRepository.existsByEmailIgnoreCase(email.trim())) {
			return;
		}

		Usuario usuario = new Usuario();
		usuario.setNome(name.trim());
		usuario.setEmail(email.trim().toLowerCase());
		usuario.setSenhaHash(passwordEncoder.encode(password));
		usuario.setPerfil(perfil);
		usuario.setAtivo(true);
		usuarioRepository.save(usuario);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
