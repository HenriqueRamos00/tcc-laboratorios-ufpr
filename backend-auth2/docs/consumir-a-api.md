# Como consumir a API de autenticacao

Para quem esta integrando uma aplicacao. Cada afirmacao aqui foi executada: o
comportamento das respostas e exercitado pelo teste de contrato
`ClientLoginContractIntegrationTest`, que compara as respostas entre si, cabecalho por
cabecalho, contra um Oracle de verdade.

Para conseguir o token da sua aplicacao, veja `registrar-aplicacao.md`.

## Como apresentar a credencial

Toda chamada, exceto a sonda de disponibilidade e o login administrativo, leva o token da
aplicacao:

```
Authorization: Bearer svc_<43 caracteres>
```

O token identifica **qual aplicacao** esta chamando, e e dele que sai o alcance da
resposta. Nao existe campo de aplicacao no corpo de requisicao nenhuma: identificador que
chegasse no JSON seria pedido do chamador, e quem decide isso e o servidor.

## Login de uma pessoa da sua aplicacao

```bash
curl -X POST https://<host>/api/auth/login \
  -H "Authorization: Bearer $TOKEN_DA_APLICACAO" \
  -H 'Content-Type: application/json' \
  -d '{"email":"pessoa@empresa.exemplo","password":"<a senha digitada>"}'
```

Resposta **200**:

```json
{
  "id": "902317136557404180",
  "name": "Pessoa de Teste",
  "email": "pessoa@empresa.exemplo",
  "role": "CLIENTE",
  "serviceCode": "portal-web",
  "companyCnpjs": ["12345678000199"]
}
```

Quatro coisas sobre esse corpo:

- **`companyCnpjs` fica na raiz, e nao dentro de um envelope.** Este documento prometia um
  campo `authorizations` que nunca existiu, e quem programasse por ele leria indefinido. O
  corpo acima foi copiado de uma resposta de verdade da aplicacao subida.

- **`id` e texto, nao numero.** Ele tem 64 bits, e numero desse tamanho perde digito em
  JavaScript. Trate como texto do comeco ao fim.
- **`serviceCode` volta igual ao codigo da sua aplicacao.** Se o que voltar nao for o que
  voce configurou, pare: a credencial em uso nao e a que voce pensa.
- **A senha nao e normalizada em lugar nenhum.** Espaco no inicio e no fim conta, e
  maiuscula difere de minuscula. Nao corte espaco antes de enviar: a pessoa que cadastrou a
  senha com espaco nao vai conseguir entrar se voce cortar.

## A tabela de desfechos

| Status | Cabecalho que importa | O que aconteceu | O que a tela deve dizer |
| --- | --- | --- | --- |
| 200 | | entrou | segue para dentro |
| 401 | **sem** `WWW-Authenticate` | credencial da pessoa recusada | dados incorretos |
| 401 | **com** `WWW-Authenticate: Bearer` | a credencial da **sua aplicacao** foi recusada | servico indisponivel, e abra chamado |
| 429 | `Retry-After` em segundos | tentativas demais | espere o tempo informado |
| 400 | | corpo malformado, campo faltando, ou **campo que a rota nao declara** | confira os dados |
| 413 | | corpo acima do teto de 32 KB, com ou sem tamanho declarado | o pedido nao cabe, reveja o que voce esta mandando |
| 503 | `Retry-After` em segundos | o servico esta sem capacidade **neste instante**, ou sem banco | tente de novo em seguida |

### Campo que a rota nao declara responde 400

A desserializacao recusa propriedade desconhecida. Isso e decisao, e nao rigor gratuito: antes
dela, a alteracao de conta aceitava endereco, perfil e aplicacao, validava os tres e gravava
apenas nome e situacao, entao quem mandava endereco novo recebia 200 com o endereco antigo.
Aceitar e descartar ensina ao chamador que o campo existe e e honrado, e ele nao e.

Consequencia pratica para voce: **mande exatamente os campos que o esquema declara**. Campo a
mais, mesmo inofensivo, responde 400. O `docs/openapi.json` e a lista autoritativa.

### O 413 nao depende de voce declarar o tamanho

O teto de 32 KB vale tambem para envio em pedacos, sem `Content-Length`. Isso esta escrito
porque antes nao valia: medido, o mesmo corpo de 8 MB respondia 413 com tamanho declarado e
400 com `Transfer-Encoding: chunked`, depois de ter sido lido inteiro. Bastava omitir um
cabecalho para o teto deixar de existir, e agora os dois caminhos respondem igual.

### O 503 nao e defeito, e ele e curto

Ele sai em duas situacoes, e as duas sao momentaneas: o servico sem conexao de banco livre
naquele instante, e o banco fora de alcance. Trate como o 429: leia `Retry-After`, espere,
tente de novo. Antes desta linha existir, os dois casos saiam como erro de servidor, que dizia
ao seu lado que o servico quebrou quando o que aconteceu foi fila cheia por um momento, ou
banco reiniciando. O segundo caso foi medido com o contentor do banco pausado: a resposta era
500 no formato padrao do framework, com o caminho pedido ecoado, e agora e 503 com
`Retry-After` no mesmo formato das outras recusas.

### O 401 tem dois significados, e o cabecalho e que separa

Este e o ponto mais importante do documento.

- **401 sem `WWW-Authenticate`** quer dizer que a pessoa errou a credencial. Mostre erro de
  login.
- **401 com `WWW-Authenticate: Bearer`** quer dizer que **o seu token de aplicacao** nao
  foi aceito: ausente, malformado, desconhecido, revogado, vencido, ou a aplicacao foi
  desativada. Isso nao e erro da pessoa que esta tentando entrar. Mostrar erro de senha
  nesse caso manda o usuario tentar de novo contra um problema que ele nao tem como
  resolver.

### Todos os motivos de recusa respondem igual, e isso e proposital

E-mail que nao existe, senha errada, e-mail que existe **em outra aplicacao**, conta que
ainda nao confirmou o e-mail e conta desativada produzem **a mesma resposta**, byte a byte,
e no mesmo tempo.

Nao tente distinguir: nao da, e e de proposito. Qualquer diferenca ali seria uma pergunta
que um atacante faz de graca para descobrir quem tem conta no Lactec. Se voce precisa
mostrar uma mensagem especifica, ela tem que vir de outro lugar que nao a resposta de
login.

### O 429 e o `Retry-After`

Depois de cinco falhas seguidas, a resposta vira **429** e traz `Retry-After` com o numero
de **segundos inteiros** a esperar. Leia esse numero e mostre o tempo; nao invente um valor
proprio.

As cinco tentativas sao todas avaliadas: senha certa na quinta **entra** normalmente e zera
a contagem. O 429 aparece a partir da quinta **falha**.

O bloqueio vale igualmente para e-mail que nao existe. Portanto 429 **nao** significa que a
conta existe.

## Cadastro, confirmacao e recuperacao

Cadastrar uma pessoa, com a credencial da sua aplicacao:

```bash
curl -X POST https://<host>/api/users \
  -H "Authorization: Bearer $TOKEN_DA_APLICACAO" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Pessoa\",\"email\":\"pessoa@empresa.exemplo\",
       \"password\":\"$SENHA_DA_PESSOA\",\"role\":\"CLIENTE\",
       \"serviceId\":\"<id da sua aplicacao>\"}"
```

**A conta nasce sem poder entrar.** O login so passa depois que o endereco for confirmado
pelo link enviado:

```bash
curl -X POST https://<host>/api/auth/confirmations \
  -H "Authorization: Bearer $TOKEN_DA_APLICACAO" \
  -H 'Content-Type: application/json' -d '{"token":"cnf_<43 caracteres>"}'
```

Responde **204** quando o link vale, e **400** quando ele foi usado, venceu, nao existe ou
e de outro proposito. Os quatro respondem igual: distinguir contaria em que estado a conta
esta.

Recuperacao de senha, em dois passos:

```bash
curl -X POST https://<host>/api/auth/recoveries \
  -H "Authorization: Bearer $TOKEN_DA_APLICACAO" \
  -H 'Content-Type: application/json' -d '{"email":"pessoa@empresa.exemplo"}'

curl -X POST https://<host>/api/auth/password-resets \
  -H "Authorization: Bearer $TOKEN_DA_APLICACAO" \
  -H 'Content-Type: application/json' \
  -d '{"token":"cnf_<43 caracteres>","password":"OutraSenha456"}'
```

O pedido de recuperacao responde **202 sempre**, exista ou nao o endereco. Nao leia essa
resposta como confirmacao de que a conta existe, porque ela nao e.

Trocar a senha derruba os links pendentes da conta e so aceita senha dentro da politica:
oito caracteres no minimo, com maiuscula, minuscula e numero.

**Um link de confirmacao de cadastro nao troca senha.** O proposito faz parte do que e
verificado.

## Listagem e manutencao

Com a credencial da sua aplicacao voce enxerga **apenas** as contas e empresas dela.
Recurso de outra aplicacao responde **404**, igual a identificador inventado, e nao 403.

| Verbo | Rota | O que faz |
| --- | --- | --- |
| GET | `/api/users` | lista as contas da sua aplicacao |
| GET PUT DELETE | `/api/users/{id}` | consulta, altera e remove uma conta sua |
| GET POST | `/api/companies` | lista e cadastra empresa na sua aplicacao |
| GET PUT DELETE | `/api/companies/{id}` | consulta, altera e remove uma empresa sua |

CNPJ entra **so com digito**, sem pontuacao, e e unico dentro da sua aplicacao. O mesmo
CNPJ pode existir em outra aplicacao, e isso e proposital: unicidade global contaria que
aquela empresa ja e cliente do Lactec em outro sistema.

CNPJ repetido dentro da sua aplicacao responde **409**, tanto no cadastro quanto na alteracao.
A alteracao esta dita aqui porque ela nao respondia: medido com uma requisicao sequencial,
mover o CNPJ de uma empresa para o de outra da mesma aplicacao batia na restricao do banco e
saia como erro de servidor, que o seu lado le como fonte indisponivel. Corrigir um CNPJ
digitado errado aparecia ao operador como servico fora do ar.

## As duas rotas de link exigem a credencial da sua aplicacao

`POST /api/auth/confirmations` e `POST /api/auth/password-resets` passaram a exigir credencial
de aplicacao, como todas as outras rotas de escopo. O motivo e isolamento: o link pertence a
uma conta, e a conta pertence a uma aplicacao. Antes disso, a aplicacao B conseguia confirmar
conta da aplicacao A apresentando o link dela, e essa era a unica rota do servico que nao
isolava por aplicacao.

Para voce muda uma coisa so: **mande a sua credencial nas duas**, como ja fazia no login. Link
de conta de outra aplicacao responde 400, igual a link desconhecido, usado, vencido ou de
proposito errado. Os cinco casos respondem igual de proposito: distinguir contaria se o
endereco existe, em que estado esta e em qual aplicacao.

## O link de recuperacao vale por uma hora, e o pedido novo mata o anterior

Dois pontos que mudaram e que a sua tela precisa refletir:

- **O prazo e de uma hora**, e nao de um dia. Recuperacao abre a conta, entao cada hora a mais
  e uma hora em que um link parado em caixa postal continua valendo como chave. A confirmacao
  de cadastro continua com prazo longo, porque ela nao abre nada: ela so transforma conta
  inerte em conta que funciona.
- **Pedir de novo invalida o pedido anterior.** Se a pessoa clicar duas vezes em "esqueci
  minha senha", o primeiro link deixa de funcionar na hora. Mostre isso na tela, senao ela usa
  o e-mail mais antigo da caixa e recebe 400 sem entender.

A mensagem sai para o endereco **cadastrado**, nao para a grafia que voce enviou no pedido.

## Cadastro de administrador, que a sua aplicacao nao alcanca

As rotas de `/api/admins` existem no contrato, mas elas **nao** sao suas: elas exigem
credencial administrativa, e o seu token de aplicacao recebe **403** em todas, em qualquer
verbo, com identificador existente ou inventado. Elas estao documentadas aqui porque o
integrador precisa saber distinguir o 403 que ele vai receber de um defeito.

| Verbo | Rota | Desfechos |
| --- | --- | --- |
| GET POST | `/api/admins` | 200, 201, 400, 409 |
| GET PUT DELETE | `/api/admins/{id}` | 200, 204, 400, 404, 409 |

Tres desfechos dessas rotas nao aparecem em nenhuma outra, e todos sao 409:

- **endereco ja cadastrado**, no cadastro e na alteracao. Aqui o conflito e aceitavel porque
  quem chama ja pode ler a lista inteira de administradores na rota ao lado, entao a resposta
  nao conta nada que ele nao possa consultar. Nas rotas de cliente isso nunca acontece: la o
  mesmo caso responde 401 uniforme, porque contar que o endereco existe entregaria a lista de
  clientes do Lactec.
- **ultimo administrador ativo**, ao desativar ou excluir. O servico recusa ficar sem nenhum
  administrador ativo, porque a rota de instalacao conta linha na tabela e a linha desativada
  continua contando: seria um estado sem volta pela API.

Dois comportamentos que o integrador precisa conhecer, porque eles mudam o que acontece com
tokens em uso:

- **trocar a senha de um administrador encerra as sessoes abertas dele no mesmo instante.**
  Troca de senha existe para o caso de a conta estar comprometida, e deixar a sessao anterior
  de pe devolveria o acesso a quem a troca deveria ter tirado.
- **desativar tambem encerra**, e nao apenas deixa de atender enquanto estiver desativado.
  Sem isso, reativar devolveria o acesso a quem estava com o token no dia do afastamento.

## Disponibilidade

```bash
curl https://<host>/health
```

Responde **200**. Para a sua sonda use `GET /health/readiness`, que responde
`{"status":"UP"}` e e a mesma que o contentor consulta. A raiz `GET /health` agrega tambem o
envio de e-mail do servico, entao ela pode responder `{"status":"OUT_OF_SERVICE"}`, **sempre
com HTTP 200**, quando o envio esta degradado. Isso nao afeta o login: tratar
`OUT_OF_SERVICE` da raiz como servico fora do ar tiraria de rotacao um servico que esta
atendendo.

As rotas de saude sao as unicas que nao pedem credencial.

## O que o servico nunca devolve

Hash de senha, token de outra aplicacao, contagem de tentativas restantes, e qualquer
indicacao de que um e-mail existe. Se voce precisa de algum desses dados para a sua tela,
converse com quem administra o servico antes de contornar.

## Valores de exemplo

**Nenhum comando deste documento traz senha escrita.** Ela entra por variavel da sua
sessao, como `$SENHA_DA_PESSOA`, porque documento e o texto que mais se copia e cola, e
senha de exemplo que satisfaz a politica vira senha de ambiente publicado.

Todos os valores de exemplo deste documento e do Swagger sao **apenas exemplos**: nao sao
credenciais validas e nao funcionam em nenhum ambiente. Em especial, nao use o exemplo de
senha como senha de verdade.
