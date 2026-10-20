package br.ufpr.tcc.auth.auth.security;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.ufpr.tcc.auth.auth.model.AuthUser;
import br.ufpr.tcc.auth.auth.model.Company;
import br.ufpr.tcc.auth.auth.persistence.AuthUserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.util.Assert;

@Configuration
class AuthorizationConfig {
    @Bean
    RegisteredClientRepository clients(PasswordEncoder encoder,
            @Value("${auth.portal-client-secret}") String secret,
            @Value("${auth.portal-redirect-uri}") String redirectUri,
            @Value("${auth.portal-logout-redirect-uri}") String logoutRedirectUri) {
        Assert.isTrue(secret.length() >= 32, "AUTH_PORTAL_CLIENT_SECRET must contain at least 32 characters");
        var portal = RegisteredClient.withId("portal-web").clientId("portal-web")
                .clientSecret(encoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri).postLogoutRedirectUri(logoutRedirectUri)
                .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope("portal.read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        // shortcut: refresh expiry slides on rotation; add absolute session expiry if required.
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false).build())
                .build();
        return new InMemoryRegisteredClientRepository(portal);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    JWKSource<SecurityContext> signingKeys(@Value("${auth.key-store}") Resource resource,
            @Value("${auth.key-store-password}") String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = resource.getInputStream()) {
            store.load(input, password.toCharArray());
        }
        var privateKey = (RSAPrivateKey) store.getKey("auth-signing", password.toCharArray());
        var publicKey = (RSAPublicKey) store.getCertificate("auth-signing").getPublicKey();
        Assert.isTrue(publicKey.getModulus().bitLength() >= 2048, "Signing key must be at least 2048 bits");
        var key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        key = new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> keys) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> identityClaims(AuthUserRepository users) {
        return context -> {
            // Reload on refresh too: a disabled account must not receive another access token.
            AuthUser user = users.findWithCompaniesById(context.getPrincipal().getName())
                    .filter(AuthUser::isEnabled)
                    .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
            context.getClaims().subject(user.getId());
            List<Map<String, Object>> companies = companiesClaim(user.getCompanies());
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                // Spring's JDBC JSON allowlist accepts ArrayList, not List.of's internal class.
                context.getClaims().audience(new ArrayList<>(List.of("portal-api")))
                        .claim("companies", companies);
            } else if (context.getAuthorizedScopes() != null
                    && context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                context.getClaims()
                        .claim("preferred_username", user.getName())
                        .claim("companies", companies);
            }
        };
    }

    private static List<Map<String, Object>> companiesClaim(Iterable<Company> companies) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Company company : companies) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", company.getId());
            item.put("name", company.getName());
            item.put("cnpj", company.getCnpj());
            result.add(item);
        }
        return result;
    }
}
