package br.ufpr.tcc.backend_lab.application.mapper;

import br.ufpr.tcc.backend_lab.application.dto.request.TermoAceiteRequest;
import br.ufpr.tcc.backend_lab.domain.model.entity.AceiteOrcamento;
import br.ufpr.tcc.backend_lab.domain.model.entity.TermoAceiteOrcamento;

import org.springframework.stereotype.Component;

/** Converte o termo recebido pela API na entidade gravada no banco. */
@Component
public class MapeadorTermoAceite {

    public TermoAceiteOrcamento paraEntidade(AceiteOrcamento aceite, TermoAceiteRequest termo) {
        var cobranca = termo.billing();
        var aprovacao = termo.approval();

        var entidade = new TermoAceiteOrcamento();
        entidade.setAceite(aceite);

        entidade.setCompany(cobranca.company());
        entidade.setCnpj(cobranca.cnpj());
        entidade.setStateRegistration(vazioComoNulo(cobranca.stateRegistration()));
        entidade.setStreet(cobranca.street());
        entidade.setCity(cobranca.city());
        entidade.setState(cobranca.state());
        entidade.setDistrict(vazioComoNulo(cobranca.district()));
        entidade.setPostalCode(cobranca.postalCode());
        entidade.setContact(vazioComoNulo(cobranca.contact()));
        entidade.setPhone(vazioComoNulo(cobranca.phone()));
        entidade.setFax(vazioComoNulo(cobranca.fax()));
        entidade.setInvoiceEmails(vazioComoNulo(cobranca.invoiceEmails()));

        entidade.setRequesterName(aprovacao.requesterName());
        entidade.setResponsibleName(aprovacao.responsibleName());
        entidade.setJobTitle(aprovacao.jobTitle());
        entidade.setDepartment(vazioComoNulo(aprovacao.department()));
        entidade.setApprovalDate(aprovacao.date());
        entidade.setAllowsResultsByEmail(aprovacao.allowsResultsByEmail());
        entidade.setResultsEmail(vazioComoNulo(aprovacao.resultsEmail()));
        return entidade;
    }

    // O formulário envia "" nos campos opcionais em branco; no banco isso vira ausência (null).
    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
