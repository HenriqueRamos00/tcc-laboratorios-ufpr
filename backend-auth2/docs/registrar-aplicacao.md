# Como registrar uma aplicacao nova

Para quem administra o servico de autenticacao. Tudo o que esta aqui foi executado: o
fluxo inteiro e exercitado pelo teste de contrato
`BootstrapAndRegistrationContractIntegrationTest`, contra um Oracle de verdade, e repetido
por HTTP contra a aplicacao subida pelo compose.

## O que e uma aplicacao aqui

Aplicacao e o cadastro de um sistema do Lactec que vai usar este servico para autenticar
as pessoas dele. O Portal Web e uma; a proxima e outra. **Incluir uma aplicacao e inserir
uma linha, nunca recompilar.**

Cada aplicacao tem o seu proprio conjunto de contas. A mesma pessoa cadastrada em duas
aplicacoes sao duas contas, com senhas independentes e confirmacoes de e-mail
independentes, e nenhuma das duas enxerga a outra. Isso nao e detalhe de implementacao: e
o que impede o cadastro de uma aplicacao de servir de consulta sobre quem tem conta na
outra.

O **codigo** da aplicacao e o nome curto pelo qual ela se identifica, e ele e cobrado em
tres lugares ao mesmo tempo: aqui no cadastro, na coluna do banco, e dentro do proprio
Portal, que recusa subir se o valor configurado nele nao casar. O formato e: pedacos de
letra minuscula e digito, ligados por hifen isolado. `portal-web` serve; `Portal-Web`,
`portal--web` e `portal-web-` nao.

## Os valores de exemplo deste documento

**Nenhum comando aqui traz senha.** A senha do primeiro administrador entra por
`$AUTH_ADMIN_PASSWORD`, que voce define na sua sessao e nao guarda em arquivo nenhum:

```bash
read -rs -p 'senha do primeiro administrador: ' AUTH_ADMIN_PASSWORD
export AUTH_ADMIN_PASSWORD
```

Isso nao e cerimonia. Documento de instalacao e o texto que mais se copia e cola, e senha de
exemplo que satisfaz a politica vira a senha do administrador do servico inteiro num ambiente
publicado. A politica cobra oito caracteres com maiuscula, minuscula e numero; o resto da
escolha e sua.

Os outros valores de exemplo, como `admin@lactec.exemplo` e `portal-web`, sao so exemplo:
troque pelos seus.

## Antes do primeiro administrador

Numa instalacao nova nao existe administrador, e a rota que cria o primeiro exige **duas**
coisas ao mesmo tempo: o segredo de instalacao, que vem da variavel `AUTH_SETUP_SECRET`, e
o fato de nao existir nenhum administrador ainda.

Faltando qualquer uma das duas, a rota responde **404**, como rota que nao existe. Ela nao
diz qual das duas faltou, de proposito: dizer que o segredo estava errado confirmaria que
a instalacao ainda nao foi feita.

```bash
curl -X POST http://localhost:8081/api/setup/admin \
  -H "X-Setup-Secret: $AUTH_SETUP_SECRET" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Administrador\",\"email\":\"admin@lactec.exemplo\",
       \"password\":\"$AUTH_ADMIN_PASSWORD\"}"
```

Resposta: **201**, sem corpo. Chamar de novo, mesmo com o segredo correto, responde **404**.

A senha do administrador obedece a mesma politica das demais: no minimo oito caracteres,
com maiuscula, minuscula e numero. Senha fora disso responde **400**.

## Entrar como administrador

```bash
curl -X POST http://localhost:8081/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@lactec.exemplo\",\"password\":\"$AUTH_ADMIN_PASSWORD\"}"
```

Resposta **200**:

```json
{
  "accessToken": "adm_<43 caracteres>",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "user": { "id": "...", "name": "Administrador", "email": "admin@lactec.exemplo" }
}
```

Senha errada responde **401**, e a resposta e a mesma de e-mail que nao existe. Isso e
proposital: diferenciar contaria quais enderecos administram o servico.

## Registrar a aplicacao

```bash
curl -X POST http://localhost:8081/api/services \
  -H "Authorization: Bearer $TOKEN_ADMINISTRATIVO" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Portal Web","code":"portal-web"}'
```

Resposta **201**:

```json
{ "id": "902317136557404180", "name": "Portal Web", "code": "portal-web", "active": true }
```

O identificador viaja como **texto**, e nao como numero, porque ele tem 64 bits e numero
desse tamanho perde digito em JavaScript.

Codigo fora do formato responde **400**. Sem credencial administrativa, **401**. Com token
de aplicacao em vez de administrativo, **403**: token de aplicacao nao administra.

## Emitir o token da aplicacao

```bash
curl -X POST http://localhost:8081/api/services/<ID_DA_APLICACAO>/tokens \
  -H "Authorization: Bearer $TOKEN_ADMINISTRATIVO" \
  -H 'Content-Type: application/json' \
  -d '{"name":"token do portal"}'
```

Resposta **201**:

```json
{
  "accessToken": "svc_<43 caracteres>",
  "tokenType": "Bearer",
  "token": { "id": "...", "name": "token do portal", "serviceId": "...",
             "createdAt": "...", "expiresAt": null }
}
```

**O valor do token aparece uma unica vez, nesta resposta.** O que fica guardado e um
digest, entao nem voce nem ninguem consegue recupera-lo depois. Perdeu, emite outro.

Se quiser prazo de validade, envie `expiresIn` em segundos no corpo. Sem ele, o token vale
ate ser revogado.

## O que acontece quando a aplicacao e desativada

A situacao da aplicacao e lida **junto** com a credencial, na mesma consulta. Desativar a
aplicacao corta o acesso dela no mesmo instante, sem precisar revogar token por token e sem
esperar vencimento. Reativar devolve o acesso sem reemitir nada.

Token desconhecido, token revogado, token vencido e token de aplicacao desativada
respondem **exatamente igual**. Quem integra nao consegue distinguir os quatro casos, e
isso e de propósito: a diferenca entre eles contaria quais aplicacoes existem.

## Trocar o token sem derrubar quem esta usando

Emita o novo, configure o consumidor, e so entao revogue o antigo. Os dois valem ao mesmo
tempo enquanto nenhum for revogado, entao a troca nao precisa de parada.
