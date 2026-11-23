package br.ufpr.tcc.backend_lab.application.dto.response;

import br.ufpr.tcc.backend_lab.domain.model.entity.AceiteOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.RecusaOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.TermoAceiteOrcamento;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DecisaoClienteInternaResponse(Aceite acceptance, Recusa rejection) {
    public static DecisaoClienteInternaResponse from(
            AceiteOrcamento aceite,
            TermoAceiteOrcamento termo,
            boolean ordemCompraDisponivel,
            RecusaOrcamento recusa) {
        if (aceite == null && recusa == null) return null;

        Aceite dadosAceite =
                aceite == null
                        ? null
                        : new Aceite(
                                aceite.getMethod().name(),
                                aceite.getAcceptedAt(),
                                termo == null ? null : Termo.from(termo),
                                ordemCompraDisponivel);
        Recusa dadosRecusa =
                recusa == null
                        ? null
                        : new Recusa(recusa.getRefusedAt(), recusa.getJustification());
        return new DecisaoClienteInternaResponse(dadosAceite, dadosRecusa);
    }

    public record Aceite(
            String method, OffsetDateTime acceptedAt, Termo terms, boolean hasPurchaseOrder) {}

    public record Recusa(OffsetDateTime refusedAt, String justification) {}

    public record Termo(Cobranca billing, Aprovacao approval) {
        private static Termo from(TermoAceiteOrcamento termo) {
            Cobranca cobranca =
                    new Cobranca(
                            termo.getCompany(),
                            termo.getCnpj(),
                            termo.getStateRegistration(),
                            termo.getStreet(),
                            termo.getCity(),
                            termo.getState(),
                            termo.getDistrict(),
                            termo.getPostalCode(),
                            termo.getContact(),
                            termo.getPhone(),
                            termo.getFax(),
                            termo.getInvoiceEmails());
            Aprovacao aprovacao =
                    new Aprovacao(
                            termo.getRequesterName(),
                            termo.getResponsibleName(),
                            termo.getJobTitle(),
                            termo.getDepartment(),
                            termo.getApprovalDate(),
                            termo.isAllowsResultsByEmail(),
                            termo.getResultsEmail());
            return new Termo(cobranca, aprovacao);
        }
    }

    public record Cobranca(
            String company,
            String cnpj,
            String stateRegistration,
            String street,
            String city,
            String state,
            String district,
            String postalCode,
            String contact,
            String phone,
            String fax,
            String invoiceEmails) {}

    public record Aprovacao(
            String requesterName,
            String responsibleName,
            String jobTitle,
            String department,
            LocalDate date,
            boolean allowsResultsByEmail,
            String resultsEmail) {}
}
