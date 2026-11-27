package br.ufpr.tcc.auth.account.usecase;

import br.ufpr.tcc.auth.account.ClientAccountStore;
import br.ufpr.tcc.auth.notification.EmailTransportAvailability;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Monta o perfil do autoatendimento, usado pela leitura e pela alteracao do perfil.
 *
 * A troca de e-mail so aparece nas acoes quando existe envio de e-mail de verdade: oferecer
 * uma acao cujo link nunca chega deixaria a pessoa esperando uma mensagem que nao sai.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SelfServiceProfiles {

    private final ClientAccountStore store;
    private final EmailTransportAvailability emailTransport;

    SelfServiceProfile of(ClientAccountStore.SelfServiceAccount account) {
        List<String> companyIds = account.companyIds();
        List<String> cnpjs = account.companyCnpjs();
        List<SelfServiceProfile.SelfServiceCompany> companies = new ArrayList<>();
        for (int index = 0; index < companyIds.size(); index++) {
            companies.add(new SelfServiceProfile.SelfServiceCompany(companyIds.get(index),
                    cnpjs.get(index)));
        }
        return new SelfServiceProfile(account.name(), account.email(), "CLIENTE",
                account.active(), account.emailConfirmed(), List.of("name"), actions(),
                List.copyOf(companies), pendingEmailChange(account),
                new SelfServiceProfile.SelfServicePasswordPolicy(
                        ChangePasswordUseCase.MINIMUM_LENGTH, ChangePasswordUseCase.MAXIMUM_LENGTH,
                        null, true, true, true, true));
    }

    private List<String> actions() {
        return emailTransport.isEmailChangeAvailable()
                ? List.of("change-email", "change-password")
                : List.of("change-password");
    }

    /** Pedido vencido ou anterior a uma troca de credencial nao aparece como pendente. */
    private PendingEmailChange pendingEmailChange(ClientAccountStore.SelfServiceAccount account) {
        OffsetDateTime now = OffsetDateTime.now();
        return store.pendingEmailChange(account.id(), account.applicationId())
                .filter(change -> change.expiresAt().isAfter(now)
                        && change.credentialVersion() == account.credentialVersion())
                .map(change -> new PendingEmailChange(change.newEmail(), change.expiresAt(),
                        change.currentEmailConfirmed(), change.newEmailConfirmed()))
                .orElse(null);
    }
}
