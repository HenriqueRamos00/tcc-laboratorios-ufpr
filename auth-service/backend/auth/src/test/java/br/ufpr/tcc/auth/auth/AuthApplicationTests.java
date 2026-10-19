package br.ufpr.tcc.auth.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import br.ufpr.tcc.auth.auth.bootstrap.BootstrapAccount;
import br.ufpr.tcc.auth.auth.persistence.AccountRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApplicationTests {
    private static final String EMAIL = "client@example.test";
    private static final String PASSWORD = "long-enough-test-password";
    private static final String SECRET = "test-client-secret-with-at-least-32-characters";
    private static final String REDIRECT = "http://localhost:5125/signin-oidc";
    private static final String USER_ID = "778ee3ab-960b-4dbf-bad3-73db1cc1d75e";
    private static final String VERIFIER = "a-long-pkce-verifier-with-at-least-forty-three-characters";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired RegisteredClientRepository clients;
    @Autowired JsonMapper json;
    @Autowired AccountRepository accounts;

    @TestBean(methodName = "testKeys")
    JWKSource<SecurityContext> signingKeys;

    static JWKSource<SecurityContext> testKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @BeforeEach
    void createAccount() {
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM auth_user");
        jdbc.update("""
                INSERT INTO auth_user (id, username, email, password_hash, service, service_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, USER_ID, "José", EMAIL, passwords.encode(PASSWORD), "auto-lab", "external-42");
    }

    @Test
    void discoveryAndJwksExposeOnlyPublicKeys() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:9000/oauth2/token"));
        mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("test-key"))
                .andExpect(jsonPath("$.keys[0].n").exists()).andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void authorizationCodeIssuesSignedIdentityAndRotatesRefreshTokens() throws Exception {
        JsonNode tokens = loginAndIssueTokens();
        Jwt access = decoder.decode(tokens.path("access_token").asString());
        assertThat(access.getSubject()).isEqualTo(USER_ID);
        assertThat(access.getAudience()).containsExactly("portal-api");
        assertThat(access.getClaimAsString("service")).isEqualTo("auto-lab");
        assertThat(access.getClaimAsString("service_id")).isEqualTo("external-42");
        assertThat(access.getClaims()).doesNotContainKeys("companies", "password", "password_hash", "email");
        assertThat(Duration.between(access.getIssuedAt(), access.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
        Jwt identity = decoder.decode(tokens.path("id_token").asString());
        assertThat(identity.getSubject()).isEqualTo(USER_ID);
        assertThat(identity.getAudience()).containsExactly("portal-web");
        assertThat(identity.getClaimAsString("preferred_username")).isEqualTo("José");

        String refresh = tokens.path("refresh_token").asString();
        var reloaded = new JdbcOAuth2AuthorizationService(jdbc, clients)
                .findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(reloaded).isNotNull();
        assertThat(Duration.between(reloaded.getRefreshToken().getToken().getIssuedAt(),
                reloaded.getRefreshToken().getToken().getExpiresAt())).isEqualTo(Duration.ofDays(7));
        JsonNode rotated = json.readTree(refresh(refresh).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(rotated.path("refresh_token").asString()).isNotEqualTo(refresh);
        refresh(refresh).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(OAuth2ErrorCodes.INVALID_GRANT));
    }

    @Test
    void revokedRefreshTokenCannotBeUsed() throws Exception {
        String refresh = loginAndIssueTokens().path("refresh_token").asString();
        mvc.perform(post("/oauth2/revoke").with(httpBasic("portal-web", SECRET))
                .param("token", refresh).param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());
        refresh(refresh).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void disabledAccountCannotLoginOrRefresh() throws Exception {
        String refresh = loginAndIssueTokens().path("refresh_token").asString();
        jdbc.update("UPDATE auth_user SET enabled = 0 WHERE id = ?", USER_ID);
        mvc.perform(formLogin().user(EMAIL).password(PASSWORD)).andExpect(redirectedUrl("/login?error"));
        refresh(refresh).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String refresh = loginAndIssueTokens().path("refresh_token").asString();
        jdbc.update("UPDATE oauth2_authorization SET refresh_token_issued_at = ?, refresh_token_expires_at = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(8))),
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        refresh(refresh).andExpect(status().isBadRequest());
    }

    @Test
    void wrongCredentialsLockAccountAndErrorsDoNotRevealAccountState() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(formLogin().user(EMAIL).password("wrong"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(formLogin().user(EMAIL).password(PASSWORD)).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin().user("missing@example.test").password(PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        jdbc.update("UPDATE auth_user SET locked_until = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), USER_ID);
        mvc.perform(formLogin().user(EMAIL).password(PASSWORD)).andExpect(redirectedUrl("/"));
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM auth_user WHERE id = ?", Integer.class, USER_ID))
                .isZero();
    }

    @Test
    void loginRequiresCsrfAndLogoutEndsBrowserSession() throws Exception {
        mvc.perform(post("/login").param("username", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        MockHttpSession session = login();
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void missingPkceAndUnregisteredRedirectCannotIssueCode() throws Exception {
        mvc.perform(get("/oauth2/authorize").session(login())
                .queryParam("response_type", "code").queryParam("client_id", "portal-web")
                .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid"))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("error=invalid_request")));
        mvc.perform(get("/oauth2/authorize").session(login())
                .queryParam("response_type", "code").queryParam("client_id", "portal-web")
                .queryParam("redirect_uri", "https://attacker.example/callback").queryParam("scope", "openid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenEndpointRejectsWrongClientSecretAndWrongVerifier() throws Exception {
        String code = authorize();
        mvc.perform(post("/oauth2/token").with(httpBasic("portal-web", "wrong"))
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/token").with(httpBasic("portal-web", SECRET))
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER + "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordHashesHaveRandomSaltsAndEmailLoginIsNormalized() throws Exception {
        String first = passwords.encode(PASSWORD);
        String second = passwords.encode(PASSWORD);
        assertThat(first).startsWith("{pbkdf2}").isNotEqualTo(second).doesNotContain(PASSWORD);
        assertThat(passwords.matches(PASSWORD, first)).isTrue();
        mvc.perform(formLogin().user(" CLIENT@EXAMPLE.TEST ").password(PASSWORD)).andExpect(redirectedUrl("/"));
    }

    @Test
    void bootstrapCreatesNormalizedAccountWithoutOverwritingExistingIdentity() {
        var environment = new MockEnvironment().withProperty("auth.bootstrap.email", " New@Example.test ")
                .withProperty("auth.bootstrap.username", "New client")
                .withProperty("auth.bootstrap.password", PASSWORD)
                .withProperty("auth.bootstrap.service", "auto-lab")
                .withProperty("auth.bootstrap.service-id", "external-99");
        var bootstrap = new BootstrapAccount(environment, accounts, passwords);
        bootstrap.run(null);
        var account = accounts.findByEmail("new@example.test").orElseThrow();
        assertThat(account.serviceId()).isEqualTo("external-99");
        assertThat(passwords.matches(PASSWORD, account.passwordHash())).isTrue();
        environment.withProperty("auth.bootstrap.service-id", "must-not-overwrite");
        bootstrap.run(null);
        assertThat(accounts.findByEmail("new@example.test").orElseThrow()).isEqualTo(account);
    }

    @Test
    void bootstrapRejectsMissingMappingBeforeInsertingAccount() {
        var environment = new MockEnvironment().withProperty("auth.bootstrap.email", "new@example.test")
                .withProperty("auth.bootstrap.username", "New client");
        var bootstrap = new BootstrapAccount(environment, accounts, passwords);
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.findByEmail("new@example.test")).isEmpty();
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(formLogin().user(EMAIL).password(PASSWORD))
                .andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String authorize() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        String location = mvc.perform(get("/oauth2/authorize").session(login())
                .queryParam("response_type", "code").queryParam("client_id", "portal-web")
                .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid profile portal.read")
                .queryParam("state", "test-state").queryParam("nonce", "test-nonce")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("state")).isEqualTo("test-state");
        assertThat(query.getFirst("code")).isNotBlank();
        return query.getFirst("code");
    }

    private JsonNode loginAndIssueTokens() throws Exception {
        return json.readTree(mvc.perform(post("/oauth2/token").with(httpBasic("portal-web", SECRET))
                .param("grant_type", "authorization_code").param("code", authorize())
                .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/oauth2/token").with(httpBasic("portal-web", SECRET))
                .param("grant_type", "refresh_token").param("refresh_token", token));
    }
}
