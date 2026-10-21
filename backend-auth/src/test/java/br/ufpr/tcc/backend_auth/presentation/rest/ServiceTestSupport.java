package br.ufpr.tcc.backend_auth.presentation.rest;

import br.ufpr.tcc.backend_auth.application.dto.request.CriarTokenServicoRequest;
import br.ufpr.tcc.backend_auth.application.service.TokenServicoService;
import br.ufpr.tcc.backend_auth.domain.model.entity.Servico;
import br.ufpr.tcc.backend_auth.domain.repository.*;
import org.springframework.beans.factory.annotation.Autowired;

abstract class ServiceTestSupport {
    @Autowired ServicoRepository servicos;
    @Autowired EmpresaRepository empresas;
    @Autowired TokenServicoRepository tokensServico;
    @Autowired TokenServicoService emissorTokens;
    @Autowired private UsuarioRepository contas;
    @Autowired private SessaoAdministrativaRepository tokensAdmin;
    Servico servico;
    String serviceToken;

    void prepararServico() {
        tokensAdmin.deleteAll();
        tokensServico.deleteAll();
        contas.deleteAll();
        empresas.deleteAll();
        servicos.deleteAll();
        servico = new Servico();
        servico.setNome("Portal Web");
        servico.setCodigo("portal-web");
        servico = servicos.saveAndFlush(servico);
        serviceToken = emissorTokens.emitir(servico.getId(), new CriarTokenServicoRequest("Testes", null)).accessToken();
    }
}
