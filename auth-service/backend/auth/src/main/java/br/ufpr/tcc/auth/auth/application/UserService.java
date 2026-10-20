package br.ufpr.tcc.auth.auth.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Company;
import br.ufpr.tcc.auth.auth.model.Role;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import br.ufpr.tcc.auth.auth.persistence.CompanyRepository;
import br.ufpr.tcc.auth.auth.web.dto.CompanyRefResponse;
import br.ufpr.tcc.auth.auth.web.dto.UserRequest;
import br.ufpr.tcc.auth.auth.web.dto.UserResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final AuthUserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public UserService(AuthUserRepository users, CompanyRepository companies,
                       PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users;
        this.companies = companies;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(String search, String roleFilter, Boolean enabled) {
        Role role = parseRole(roleFilter, true);
        Specification<AuthUser> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = likePattern(search);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '!'),
                    cb.like(cb.lower(root.get("email")), pattern, '!')));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }

        return users.findAll(spec, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        String name = cleanName(request.name());
        String email = cleanEmail(request.email());
        Role role = parseRole(request.role(), false);
        List<String> companyIds = cleanCompanyIds(request.companyIds());
        List<Company> assigned = validateCompanies(role, companyIds);

        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha inicial do usuário.");
        }
        validatePassword(request.password());

        AuthUser user = new AuthUser(UUID.randomUUID().toString(), name, email,
                encoder.encode(request.password()), role);
        user.getCompanies().addAll(assigned);
        return toResponse(users.save(user));
    }

    @Transactional
    public UserResponse update(String id, UserRequest request, String currentAdminId) {
        AuthUser user = find(id);
        String name = cleanName(request.name());
        String email = cleanEmail(request.email());
        Role role = parseRole(request.role(), false);
        List<String> companyIds = cleanCompanyIds(request.companyIds());
        List<Company> assigned = validateCompanies(role, companyIds);

        if (users.existsByEmailAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.");
        }
        if (id.equals(currentAdminId) && role != user.getRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você não pode alterar o seu próprio perfil de acesso.");
        }
        if (user.getRole() == Role.ADMIN && role != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O sistema precisa manter pelo menos um administrador.");
        }
        if (request.password() != null && !request.password().isBlank()) {
            validatePassword(request.password());
            user.setPasswordHash(encoder.encode(request.password()));
        }

        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.getCompanies().clear();
        user.getCompanies().addAll(assigned);
        return toResponse(user);
    }

    @Transactional
    public UserResponse setEnabled(String id, boolean enabled, String currentAdminId) {
        AuthUser user = find(id);
        if (id.equals(currentAdminId) && !enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você não pode desativar a sua própria conta.");
        }
        if (user.getRole() == Role.ADMIN && !enabled
                && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O sistema precisa manter pelo menos um administrador ativo.");
        }
        user.setEnabled(enabled);
        return toResponse(user);
    }

    @Transactional
    public void delete(String id, String currentAdminId) {
        AuthUser user = find(id);
        if (id.equals(currentAdminId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você não pode excluir a sua própria conta.");
        }
        if (user.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O sistema precisa manter pelo menos um administrador.");
        }
        jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", id);
        users.delete(user);
    }

    private AuthUser find(String id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private List<Company> validateCompanies(Role role, List<String> companyIds) {
        if (role == Role.USER && companyIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Um usuário do portal precisa estar vinculado a pelo menos uma empresa.");
        }
        if (companyIds.isEmpty()) return List.of();
        List<Company> found = companies.findAllByIdIn(companyIds);
        if (found.size() != companyIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Uma ou mais empresas informadas não existem.");
        }
        List<Company> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER));
        return ordered;
    }

    private UserResponse toResponse(AuthUser user) {
        List<CompanyRefResponse> companyResponses = user.getCompanies().stream()
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(company -> new CompanyRefResponse(
                        company.getId(), company.getName(), company.getCnpj()))
                .toList();
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.isEnabled(), companyResponses, user.getCreatedAt());
    }

    private static String cleanName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe um nome válido, com até 150 caracteres.");
        }
        return value;
    }

    private static String cleanEmail(String email) {
        String value = AuthUser.normalizeEmail(email);
        if (value == null || value.isBlank() || value.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.");
        }
        return value;
    }

    private static List<String> cleanCompanyIds(List<String> companyIds) {
        if (companyIds == null) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String id : companyIds) {
            if (id != null && !id.isBlank()) unique.add(id.strip());
        }
        return List.copyOf(unique);
    }

    private static void validatePassword(String password) {
        if (password.length() < 15 || password.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve conter entre 15 e 1024 caracteres.");
        }
    }

    private static Role parseRole(String value, boolean allowNull) {
        if (value == null || value.isBlank()) {
            if (allowNull) return null;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o perfil do usuário.");
        }
        try {
            return Role.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Perfil inválido. Use ADMIN ou USER.");
        }
    }

    private static String likePattern(String value) {
        String escaped = value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
