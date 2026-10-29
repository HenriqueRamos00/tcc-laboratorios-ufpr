# backend-auth2

Servico de autenticacao e autorizacao compartilhado pelas aplicacoes do Lactec. Ele existe
como **segunda implementacao** do mesmo requisito, para ser comparada com `backend-auth/`,
que foi escrita por outro integrante. As duas atendem o mesmo contrato publico.

Spring Boot 4.1.1, Java 21, Oracle 23ai, Maven.

## Subir

```bash
cp .env.example .env     # e preencha as senhas e o segredo de instalacao
docker compose up --build
```

O banco leva alguns minutos na primeira subida porque a imagem do Oracle inicializa o banco
dela. Os dois usuarios do esquema nao sao o motivo da espera: eles nascem de um script
idempotente que roda a cada subida.

A sonda certa para saber que esta pronto e `GET http://localhost:8081/health/readiness`,
que e a mesma que o healthcheck do contentor consulta. **A raiz `GET /health` agrega tambem
o envio de e-mail**, e com o disjuntor do envio aberto ela responde
`{"status":"OUT_OF_SERVICE"}`, sempre com HTTP 200. Com o servidor de e-mail recusando
conexao, o disjuntor abre e a raiz muda de corpo enquanto a prontidao segue `{"status":"UP"}`:
quem automatiza alarme consulta a prontidao, e quem quer saber do envio consulta a raiz.

## Testar

```bash
docker run --rm --network host \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD:/w" -v auth2-maven:/root/.m2 -w /w \
  eclipse-temurin:21.0.12.1_1-jdk-alpine ./mvnw -B test
```

O soquete do Docker e a rede do host sao necessarios porque parte da suite sobe um Oracle
de verdade em contentor. Nao existe banco em memoria aqui de proposito: as regras caras
deste servico moram no esquema, e banco em memoria nao executa migracao, nao cobra
restricao e nao distingue semantica de byte de semantica de caractere.

Para rodar so o que nao precisa de banco, acrescente `-DexcludedGroups=integration`.

## Documentacao

| Arquivo | Para quem |
| --- | --- |
| `docs/registrar-aplicacao.md` | quem administra: cadastrar aplicacao e emitir token |
| `docs/consumir-a-api.md` | quem integra: apresentar credencial e ler cada desfecho |
| `docs/openapi.json` | quem integra: o contrato em OpenAPI, gerado pela propria aplicacao |

Estes tres descrevem **como o servico funciona e como consumi-lo**, que e o que um
integrador precisa. O registro de construcao do modulo, com o levantamento, a apuracao
das decisoes e as revisoes, nao mora aqui: ele e material de banca, nao documentacao de
produto, e num repositorio publico ele vira mapa para quem quer atacar o servico.

As regras de estilo, de teste e de arquitetura estao no `CLAUDE.md` deste diretorio.

O `docs/openapi.json` e o contrato em formato de maquina, para quem integra e para quem gera
cliente a partir dele. **A aplicacao publicada nao serve essa documentacao:** a interface do
Swagger esta desligada e `/v3/api-docs` responde 404 para quem apresenta credencial e 401 para
quem nao apresenta, igual a qualquer rota que nao existe. O motivo e duplo: o documento lista a
superficie administrativa inteira, e o botao de experimentar da interface gasta tentativa da
conta que ele usar.

### Regerar `docs/openapi.json`

O artefato nasce da propria aplicacao, numa subida descartavel com a documentacao ligada por
variavel de ambiente. Nenhum arquivo do projeto muda para isso, e a interface do Swagger
continua desligada: so `springdoc.api-docs.enabled` e invertido, e so naquela subida.

```bash
cd backend-auth2
docker compose stop api
docker compose run --rm -d --service-ports \
  -e SPRINGDOC_API_DOCS_ENABLED=true \
  -e SPRINGDOC_WRITER_WITH_DEFAULT_PRETTY_PRINTER=true api

until curl -fsS http://localhost:8081/health >/dev/null 2>&1; do sleep 5; done

# entre com um administrador existente e guarde o token
TOKEN=...

curl -fsS -H "Authorization: Bearer $TOKEN" http://localhost:8081/v3/api-docs \
  | python3 -m json.tool > docs/openapi.json

docker compose down --remove-orphans && docker compose up -d
```

Tres pontos do procedimento que nao sao obvios:

- **`/v3/api-docs` tambem e rota fechada.** A cadeia nega tudo o que nao esta liberado
  nominalmente, e a documentacao gerada nao esta. Sem token a chamada volta 401 com desafio,
  entao o procedimento passa pelo login administrativo antes de baixar o arquivo.
- **O nome da variavel leva sublinhado no lugar do hifen.** A propriedade e
  `springdoc.api-docs.enabled`, e `SPRINGDOC_API_DOCS_ENABLED` resolve porque a consulta ao
  ambiente troca ponto e hifen por sublinhado. A forma sem o hifen nao resolve, e o sintoma e a
  documentacao continuar desligada sem erro nenhum.
- **A impressao formatada sai do lado de fora.** O arquivo e passado por `json.tool` porque
  JSON numa linha so nao tem diferenca revisavel, e artefato versionado sem diff legivel deixa
  de ser revisavel, que e metade do motivo de versionar.

## Rotas

| Verbo | Rota | Quem pode chamar |
| --- | --- | --- |
| GET | `/health` | qualquer um; agrega banco e envio de e-mail |
| GET | `/health/readiness` | qualquer um; e a sonda que o contentor consulta |
| GET | `/health/liveness` | qualquer um |
| GET | `/health/mail` | qualquer um; estado do envio, com os contadores |
| POST | `/api/setup/admin` | segredo de instalacao, e so enquanto nao existir administrador |
| POST | `/api/admin/auth/login` | qualquer um, com e-mail e senha de administrador |
| GET POST | `/api/services` | administrador |
| GET PUT DELETE | `/api/services/{serviceId}` | administrador |
| GET POST | `/api/services/{serviceId}/tokens` | administrador |
| DELETE | `/api/services/{serviceId}/tokens/{tokenId}` | administrador |
| GET POST | `/api/admins` | administrador |
| GET PUT DELETE | `/api/admins/{id}` | administrador |
| GET | `/api/users` | aplicacao, no proprio escopo; administrador, em todas |
| POST | `/api/users` | aplicacao, e so ela: o escopo sai da credencial |
| GET PUT DELETE | `/api/users/{id}` | aplicacao, no proprio escopo; administrador, em todos |
| GET POST | `/api/companies` | aplicacao, no proprio escopo |
| GET PUT DELETE | `/api/companies/{id}` | aplicacao, no proprio escopo |
| POST | `/api/auth/login` | aplicacao |
| POST | `/api/auth/confirmations` | aplicacao: o link pertence a uma conta, e a conta a uma aplicacao |
| POST | `/api/auth/recoveries` | aplicacao |
| POST | `/api/auth/password-resets` | aplicacao, pelo mesmo motivo da confirmacao |

As rotas marcadas como "aplicacao" recusam credencial administrativa na propria cadeia, com
403: a cadeia exige o tipo de credencial da rota, e nao apenas uma credencial valida.

Excluir aplicacao e desativacao logica: a linha fica, o acesso cai no mesmo instante, e a
trilha de auditoria continua explicavel.

**A situacao e obrigatoria nas tres rotas de alteracao.** Corpo sem `active` responde 400
nas tres, porque campo de situacao ausente nao pode valer verdadeiro por omissao: um pedido
enviado para corrigir o nome nao reativa o que alguem desativou.

**Campo que a rota nao honra responde 400, e nao e ignorado.** A desserializacao recusa
propriedade desconhecida, porque aceitar e descartar ensina ao chamador que o campo existe.

Codigo de aplicacao repetido responde 409, e endereco de cliente repetido tambem, inclusive
quando os dois cadastros chegam ao mesmo tempo: quem recusa o segundo e a restricao unica do
banco, e a recusa e traduzida para conflito.

**Corpo acima de 32 KB responde 413, recusado antes de ser lido**, nos dois caminhos, com
tamanho declarado e em pedacos. O maior pedido legitimo deste servico tem menos de mil bytes.

**Falta de capacidade responde 503 com `Retry-After`**, tanto para fila de atendimento cheia
quanto para banco fora de alcance, e a sonda de prontidao inclui o banco.

**O teto de threads de atendimento e o tamanho do pool de conexao sao declarados, e nao
herdados do padrao do servidor.** Os valores vivem no `application.yaml` e no `compose.yaml`,
dimensionados contra o teto de memoria do contentor, e a justificativa de cada numero fica no
comentario ao lado dele.

Administrador e o contrario: a exclusao apaga a linha, porque o cadastro de administrador
e pequeno e revisado por gente. O que nao se permite e ficar sem nenhum administrador
ativo, e por isso desativar ou excluir o ultimo ativo responde 409. A rota de instalacao
nao socorre nesse caso: ela conta linha na tabela, e a linha desativada continua contando.
Trocar a senha de um administrador encerra as sessoes abertas dele no mesmo instante.

## Envio de e-mail

O adaptador de servidor de e-mail existe: `notification/SmtpMessageTransport`, por SMTP, com
os tres tempos limite da biblioteca declarados e STARTTLS obrigatorio.

O envio nao fica no caminho da resposta e nao derruba o cadastro. O caso de uso publica a
mensagem dentro da propria transacao, um ouvinte de depois do commit a entrega a um pool
exclusivo de duas threads com fila de cinquenta, e dali ela vai para o servidor. Transacao
desfeita nao gera mensagem; servidor fora do ar gera uma linha de log e nada mais; fila cheia
descarta com registro, e quem nao recebeu pede de novo.

Um disjuntor fecha a torneira depois de tres falhas em sequencia e libera uma tentativa de
prova um minuto depois. O estado dele fica em `GET /health/mail`, com os contadores por
desfecho e sem nenhum dado de pessoa. A raiz de `GET /health` continua respondendo 200 mesmo
com o disjuntor aberto, de proposito: e-mail parado e funcao degradada, e 503 ali faria a
borda tirar de rotacao o unico servico de autenticacao que existe.

Ambiente sem `SPRING_MAIL_HOST` nao tem adaptador: a aplicacao sobe, o cadastro funciona, e
cada mensagem descartada aparece no log e em `/health/mail` como `transport: ABSENT`. Nao
existe propriedade que escolha entre enviar e so registrar, porque esse interruptor alguem
liga em producao sem perceber que parou de mandar e-mail.

Para exercitar o fluxo sem servidor de e-mail existe `auth.notification.record-messages`,
desligada por padrao. Ligada com `SPRING_MAIL_HOST` definido, ela **recusa a subida**: guardar
o token em memoria so se justifica onde nao existe envio de verdade.
