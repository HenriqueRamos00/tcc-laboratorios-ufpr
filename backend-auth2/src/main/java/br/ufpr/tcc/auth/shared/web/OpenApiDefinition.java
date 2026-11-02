package br.ufpr.tcc.auth.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.context.annotation.Configuration;

/**
 * O cabecalho do contrato em OpenAPI, e as duas credenciais que ele exige.
 *
 * Sem isto o artefato nasce chamado "OpenAPI definition", versao "v0", e sem
 * `securitySchemes` nenhum. Duas consequencias medidas no proprio arquivo gerado: ele nao diz
 * de qual dos dois servicos de autenticacao do repositorio ele e, e cliente gerado a partir
 * dele nao manda credencial nenhuma, tomando 401 com desafio em tudo, que no consumidor
 * aparece como fonte indisponivel e nao como credencial faltando.
 *
 * As duas credenciais aparecem separadas porque a distincao entre elas e o eixo do contrato, e
 * ela nao e expressavel em prosa para uma ferramenta: cada controlador declara qual exige.
 *
 * A descricao carrega o que seria ruido repetir em cada operacao: os dois significados do 401,
 * o `Retry-After` do 429, o identificador como texto, e o fato de o minimo de senha ser regra
 * de cadastro e nao de login.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Servico de autenticacao do Lactec, segunda implementacao",
        version = "0.0.1-SNAPSHOT",
        description = """
                Autenticacao e autorizacao compartilhada pelas aplicacoes do Lactec.

                Quatro pontos do contrato que valem para quase toda rota, ditos uma vez aqui:

                1. 401 **sem** `WWW-Authenticate` quer dizer credencial da pessoa recusada;
                401 **com** `WWW-Authenticate` quer dizer que a credencial da aplicacao nao foi
                aceita. Sao erros de dono diferente e a tela trata cada um de um jeito.
                2. 429 sempre leva `Retry-After` em segundos inteiros.
                3. Todo identificador viaja como texto, inclusive quando o banco guarda numero:
                numero de 64 bits perde precisao em JavaScript.
                4. O minimo de oito caracteres da senha e regra de **cadastro**. No login nao
                existe minimo, de proposito: cobrar ali responderia 400 onde o contrato espera
                401 e entregaria o tamanho da senha a quem sonda.
                """))
@SecuritySchemes({
        @SecurityScheme(name = OpenApiDefinition.APPLICATION_CREDENTIAL,
                type = SecuritySchemeType.HTTP, scheme = "bearer", in = SecuritySchemeIn.HEADER,
                description = "Credencial da aplicacao, emitida pelo administrador. Formato"
                        + " svc_ seguido de 43 caracteres."),
        @SecurityScheme(name = OpenApiDefinition.ADMINISTRATIVE_CREDENTIAL,
                type = SecuritySchemeType.HTTP, scheme = "bearer", in = SecuritySchemeIn.HEADER,
                description = "Credencial administrativa, obtida no login administrativo."
                        + " Formato adm_ seguido de 43 caracteres.")
})
class OpenApiDefinition {

    static final String APPLICATION_CREDENTIAL = "credencial-de-aplicacao";

    static final String ADMINISTRATIVE_CREDENTIAL = "credencial-administrativa";
}
