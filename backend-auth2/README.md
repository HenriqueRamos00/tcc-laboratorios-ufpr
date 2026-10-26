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

O banco leva alguns minutos na primeira subida, porque ele cria os dois usuarios do
esquema. Quando `GET http://localhost:8081/health` responder `{"status":"UP"}`, esta
pronto.

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
| `docs/restricao-por-regiao.md` | a analise do requisito 8, a decisao e as perguntas abertas |
| `docs/round-1-levantamento.md` | o levantamento de boas praticas e os fatos medidos |
| `docs/round-2-decisoes.md` | a apuracao das decisoes em disputa, com o porque de cada uma |
| `docs/round-3-execucao.md` | a evidencia de cada fatia, incluindo o que nao foi provado |

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

Tres pontos do procedimento que nao sao obvios, e os tres foram medidos ao gerar o arquivo que
esta versionado:

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
| GET | `/health` | qualquer um |
| POST | `/api/setup/admin` | segredo de instalacao, e so enquanto nao existir administrador |
| POST | `/api/admin/auth/login` | qualquer um, com e-mail e senha de administrador |
| GET POST | `/api/services` | administrador |
| GET PUT DELETE | `/api/services/{id}` | administrador |
| GET POST | `/api/services/{id}/tokens` | administrador |
| DELETE | `/api/services/{id}/tokens/{tokenId}` | administrador |
| GET POST | `/api/admins` | administrador |
| GET PUT DELETE | `/api/admins/{id}` | administrador |
| GET POST | `/api/users` | aplicacao, no proprio escopo; administrador, em todos |
| GET PUT DELETE | `/api/users/{id}` | aplicacao, no proprio escopo; administrador, em todos |
| GET POST | `/api/companies` | aplicacao, no proprio escopo |
| GET PUT DELETE | `/api/companies/{id}` | aplicacao, no proprio escopo |
| POST | `/api/auth/login` | aplicacao |
| POST | `/api/auth/confirmations` | aplicacao |
| POST | `/api/auth/recoveries` | aplicacao |
| POST | `/api/auth/password-resets` | aplicacao |

Excluir aplicacao e desativacao logica: a linha fica, o acesso cai no mesmo instante, e a
trilha de auditoria continua explicavel.

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
