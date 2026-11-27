# backend-auth2

Serviço de autenticação e autorização compartilhado pelas aplicações do Lactec.
O login de clientes exige token de aplicação; as operações administrativas usam
Bearer tokens opacos. A API C# integra este serviço no modo `Portal__DataSource=lactec`
e emite o JWT do Portal do Cliente.

Spring Boot 4.1.2, Java 21, Oracle e Maven.

## Demonstração do portal

Requisitos: Docker Engine, Docker Compose e `backend-portal-web/.env` com a conexão do
AutoLAB real, acessível pela VPN do Lactec. Se o arquivo ainda não existir, copie
[`backend-portal-web/.env.example`](../backend-portal-web/.env.example) e preencha
as credenciais `AutoLab__*`. Execute na raiz do repositório:

```bash
docker compose -f compose.auth.yaml up -d --build --remove-orphans
```

O Compose inicia Oracle para autenticação, migrações Flyway, Auth2, SQL Server com o
banco `portal`, API C#, Portal do Cliente e Portal Administrativo Auth2.
Equipamentos, ensaios e relatórios vêm do AutoLAB real.
Os orçamentos de demonstração dispensam Salesforce. Este arquivo é independente do
`compose.yaml`; encerre outros ambientes que usem as mesmas portas antes de iniciar.

| Aplicação | Endereço |
| --- | --- |
| Portal do Cliente | http://localhost:4200 |
| Portal Administrativo Auth2 | http://localhost:4202 |
| API C# | http://localhost:5125 |
| Backend Auth2 | http://localhost:8081 |
| Prontidão do Auth2 | http://localhost:8081/health/readiness |

O Oracle pode levar alguns minutos no primeiro início. O Swagger fica desativado por padrão.

### Usuários e acesso

O seed cria a aplicação `portal-web`, seu token de integração e as seguintes contas:

| Conta | Senha inicial | Acesso |
| --- | --- | --- |
| `lactec@lactec.com` | `Lactec!7uQ2mN9xR4pK` | Portal Administrativo Auth2 |
| `client1@lactec.com` | `Cliente1!6vT9qL2nW8sD` | Portal do Cliente: EMPRESA DE ENERGIA / `30960572000183` |
| `client2@lactec.com` | `Cliente2!3rK7wP5mH9zF` | Portal do Cliente: DISTRIBUIDORA DE ENERGIA / `11222333000181` |

O administrador gerencia o Auth2 e entra em http://localhost:4202. A autenticação usa
`POST /api/admin/auth/login`. As operações da API estão no [guia administrativo](docs/registrar-aplicacao.md).
Os clientes já nascem ativos e com e-mail confirmado e entram pelo Portal do Cliente.

Cada cliente vê nove orçamentos de demonstração, identificados pelo prefixo `quote-`:

| Conta | Números dos orçamentos |
| --- | --- |
| `client1@lactec.com` | `1, 3, 6, 8, 10, 12, 14, 16, 18` |
| `client2@lactec.com` | `4, 5, 7, 9, 11, 13, 15, 17, 19` |

O login devolve os CNPJs em `authorizations.companyCnpjs`. O C# filtra orçamentos,
propostas PDF e dados do AutoLAB pelos CNPJs da conta; registros de outra empresa
respondem 404. As propostas comerciais são geradas pelo C# com os dados do orçamento
e o layout Lactec.

Os CNPJs de demonstração são fictícios e não alcançam registros do AutoLAB real.
Para consultar equipamentos, ensaios, relatórios e PDFs reais, vincule a conta pela
API administrativa do Auth2 a uma empresa cujo CNPJ exista no AutoLAB. O
[guia administrativo](docs/registrar-aplicacao.md#cadastrar-empresa-e-cliente)
descreve como cadastrar empresas e alterar os vínculos da conta. Faça novo login no
portal para atualizar os CNPJs no JWT.

A demonstração não configura SMTP. Os clientes pré-cadastrados já podem entrar;
confirmação de novos cadastros e recuperação de senha exigem configurar o
[envio de e-mail](#envio-de-e-mail).

### Configuração e persistência

As senhas e chaves padrão são públicas e exclusivas do ambiente local. O `.env` da
raiz permite substituir os valores do `compose.auth.yaml`:

| Variável | Finalidade |
| --- | --- |
| `AUTH_DEMO_ADMIN_PASSWORD` | Senha inicial de `lactec@lactec.com` |
| `AUTH_DEMO_CLIENT1_PASSWORD` | Senha inicial de `client1@lactec.com` |
| `AUTH_DEMO_CLIENT2_PASSWORD` | Senha inicial de `client2@lactec.com` |
| `AUTH_ORACLE_PASSWORD` | Senha administrativa do Oracle na criação do banco |
| `AUTH_SCHEMA_OWNER_PASSWORD` | Senha do dono do esquema, usado nas migrações |
| `AUTH_DATASOURCE_PASSWORD` | Senha da aplicação no Oracle |
| `SQLSERVER_SA_PASSWORD` | Senha SA do SQL Server local |
| `BACKEND_AUTH_SERVICE_TOKEN` | Token de integração compartilhado entre Auth2 e C# |
| `SERVICE_TOKEN_EXPIRATION_SECONDS` | Validade do token de integração; padrão de 30 dias |
| `PORTAL_JWT_SIGNING_KEY` | Chave de assinatura dos JWTs do portal |
| `PORTAL_USE_DEMO_DATA` | `true` usa orçamentos de demonstração; `false` consulta Salesforce |

Com `PORTAL_USE_DEMO_DATA=false`, configure também as credenciais `Salesforce__*` em
`backend-portal-web/.env`; sem elas a API não inicia. Os dados laboratoriais continuam
vindo do AutoLAB. Veja a [configuração do C#](../backend-portal-web/README.md#configuração-e-execução-em-lactec).

As senhas de pessoas são armazenadas com Argon2id; o token de integração é guardado
somente como hash. Credenciais de banco e integração permanecem nos backends.
O seed roda em uma transação pela credencial restrita da aplicação e apenas cria
registros ausentes: reiniciar preserva senhas, nomes, situação e vínculos existentes.
As variáveis de senha das contas só valem na criação inicial. Tokens vencidos são
renovados, preservando revogações manuais. O seed fica desativado por padrão no módulo
e no Compose isolado; somente este Compose define `AUTH_DEMO_SEED_ENABLED=true`.
Os dados de demonstração não fazem parte das migrações Flyway.

Os dois bancos ficam na rede interna do Compose. Os volumes são
`tcc-auth-demo_auth-oracle-data` e `tcc-auth-demo_auth-sqlserver-data`, separados dos
demais ambientes. Em volumes existentes, mantenha a senha SA já usada;
alterar `AUTH_ORACLE_PASSWORD` não muda a senha administrativa de um Oracle já criado.
As senhas do dono do esquema e da aplicação são sincronizadas na inicialização.

```bash
docker compose -f compose.auth.yaml ps
docker compose -f compose.auth.yaml logs -f backend-auth2 portal-api
docker compose -f compose.auth.yaml down     # preserva os dois bancos
docker compose -f compose.auth.yaml down -v  # apaga os dois bancos locais e os volumes do frontend
```

## Execução isolada

Na pasta `backend-auth2`:

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
| POST | `/api/users` | administrador: a aplicacao da conta e as empresas vao no corpo |
| GET | `/api/users/{id}` | aplicacao, no proprio escopo; administrador, em todos |
| PUT DELETE | `/api/users/{id}` | administrador |
| GET POST | `/api/companies` | administrador |
| GET PUT DELETE | `/api/companies/{id}` | administrador |
| POST | `/api/auth/login` | aplicacao |
| POST | `/api/auth/confirmations` | aplicacao: o link pertence a uma conta, e a conta a uma aplicacao |
| POST | `/api/auth/recoveries` | aplicacao |
| POST | `/api/auth/password-resets` | aplicacao, pelo mesmo motivo da confirmacao |

As rotas marcadas como "aplicacao" recusam credencial administrativa na propria cadeia, com
403: a cadeia exige o tipo de credencial da rota, e nao apenas uma credencial valida. O
contrario tambem vale: as marcadas so como "administrador" recusam credencial de aplicacao.

**Cadastro de cliente e de empresa e do portal administrativo.** A aplicacao consome: faz o
login dos clientes dela, confirma endereco, recupera senha e le as proprias contas. Quem
decide que conta existe, em qual aplicacao, e quais empresas ela representa e o
administrador. Empresa nao pertence a aplicacao nenhuma: o CNPJ e unico no servico inteiro,
e contas de aplicacoes diferentes podem representar a mesma empresa. O vinculo e de muitos
para muitos, informado em `companyIds` no cadastro e na alteracao da conta, e e ele que o
login devolve em `companyCnpjs`. Empresa que alguma conta ainda representa nao e excluida:
responde 409 ate os vinculos serem desfeitos.

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

## Autoatendimento de clientes

A API C# integra o autoatendimento usando a credencial de aplicação e o identificador da conta obtido da sessão autenticada. A conta precisa pertencer à aplicação. O cliente não recebe nem escolhe esse identificador nas chamadas do navegador.

O perfil expõe somente o nome como editável neste serviço. E-mail, empresas, papel, situação e confirmação são apresentados para consulta. A senha exige a senha atual e segue a política do serviço. A alteração incrementa a versão das credenciais e encerra as sessões existentes. JWTs emitidos antes desse controle, sem a versão, são tratados como versão zero e continuam válidos até uma alteração de credenciais.

A alteração de e-mail exige a senha atual e dois links, um enviado ao endereço cadastrado e outro ao novo endereço. Ambos devem ser confirmados em até uma hora. Solicitações pendentes podem ser canceladas, uma senha alterada invalida a solicitação, e novas tentativas são limitadas. O token é de uso único. Sem SMTP ou `AUTH_EMAIL_CHANGE_LINK` válido, a API não anuncia essa ação e não aceita pedidos de alteração.

No Compose da raiz, o SMTP do Auth2 é opcional. Configure `AUTH_MAIL_HOST`, usuário, senha, remetente e os links de confirmação e recuperação no `.env`. Configure também `AUTH_EMAIL_CHANGE_LINK` para habilitar a troca de e-mail. Sem host, o serviço inicia sem transporte de e-mail.

As rotas usadas pelo adapter C# são `GET` e `PATCH /api/self-service/accounts/{accountId}/profile`, `POST /api/self-service/accounts/{accountId}/password`, `POST` e `DELETE /api/self-service/accounts/{accountId}/email-change`, `POST /api/self-service/email-change/confirm` e `GET /api/self-service/accounts/{accountId}/session-state`. Todas exigem a credencial de aplicação. As respostas de perfil e de sessão não são armazenáveis em cache.

O C# expõe ao Portal do Cliente `GET` e `PATCH /api/profile`, `POST /api/profile/password`, `POST` e `DELETE /api/profile/email-change` e `POST /api/profile/email-change/confirm`. A confirmação do link é uma ação explícita da tela; abrir a URL não altera a conta.

## Envio de e-mail

Preencha `AUTH_MAIL_HOST`, `AUTH_MAIL_PORT`, `AUTH_MAIL_USERNAME`, `AUTH_MAIL_PASSWORD`
e `AUTH_MAIL_SENDER` no `.env` da raiz, para `compose.auth.yaml`, ou no `.env` desta
pasta, para o Compose isolado. Esses valores viram `SPRING_MAIL_*` no container;
o remetente vira `AUTH_NOTIFICATION_SENDER`. Na execução direta do Java, exporte
os nomes usados dentro do container. `SPRING_MAIL_ENABLED` só se aplica ao Backend Lab.
Veja a [configuração de e-mail](../README.md#e-mail-e-recuperação-de-senha).

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

## Localizar regras de contas (`account`)

Cada operacao exposta numa rota e um caso de uso proprio em `usecase/`, com um metodo `execute`:

| Caso de uso | Rota | Credencial |
| --- | --- | --- |
| `RegisterClientAccountUseCase` | `POST /api/users` | administrativa |
| `ListClientAccountsUseCase` | `GET /api/users` | administrativa ou aplicacao |
| `GetClientAccountUseCase` | `GET /api/users/{id}` | administrativa ou aplicacao |
| `UpdateClientAccountUseCase` | `PUT /api/users/{id}` | administrativa |
| `DeleteClientAccountUseCase` | `DELETE /api/users/{id}` | administrativa |
| `ConfirmEmailUseCase` | `POST /api/auth/confirmations` | aplicacao |
| `RequestPasswordRecoveryUseCase` | `POST /api/auth/recoveries` | aplicacao |
| `ResetPasswordUseCase` | `POST /api/auth/password-resets` | aplicacao |
| `GetSelfServiceProfileUseCase` | `GET /api/self-service/accounts/{id}/profile` | aplicacao |
| `UpdateSelfServiceProfileUseCase` | `PATCH /api/self-service/accounts/{id}/profile` | aplicacao |
| `ChangePasswordUseCase` | `POST /api/self-service/accounts/{id}/password` | aplicacao |
| `RequestEmailChangeUseCase` | `POST /api/self-service/accounts/{id}/email-change` | aplicacao |
| `CancelEmailChangeUseCase` | `DELETE /api/self-service/accounts/{id}/email-change` | aplicacao |
| `ConfirmEmailChangeUseCase` | `POST /api/self-service/email-change/confirm` | aplicacao |
| `GetSessionStateUseCase` | `GET /api/self-service/accounts/{id}/session-state` | aplicacao |

- `web/ClientAccountController` (`/api/users`), `web/AccountTokenController` (links por
  e-mail) e `web/ClientSelfServiceController` (`/api/self-service/**`): validacao de entrada
  e traducao de cada desfecho em resposta HTTP.
- `usecase/AccountSummary`, `usecase/SelfServiceProfile` e `usecase/PendingEmailChange`: o
  que as rotas devolvem sobre a conta.
- `usecase/CurrentPasswordVerification`: senha atual exigida pela troca de senha e pela de
  e-mail, com o limite de cinco erros em quinze minutos compartilhado entre as duas.
- `usecase/SelfServiceProfiles`: monta o perfil, oferecendo a troca de e-mail somente
  quando existe envio de e-mail.
- `ClientAccountStore`: leitura e gravacao de contas, vinculos e tokens pelos repositorios.
- `ClientAccount`: estado inicial da conta e mudancas de senha, e-mail e confirmacao.
- `ClientEmailChange`: troca de e-mail pendente e janela de pedidos da conta.
- `AccountActionToken` e seu repositorio: finalidade, validade e consumo unico dos tokens.
- `AccountConstraintViolation`: identifica a restricao violada pelo nome informado pelo Hibernate.
- `ClientAccountDirectory`: fornece as credenciais ao modulo `authentication`, que decide o login.

O cadastro traduz apenas `UK_CLIENT_ACCOUNT_EMAIL` em e-mail repetido. A recuperacao ignora
apenas o conflito concorrente `UX_ACCOUNT_CONFIRMATION_PENDING`; outras falhas sao propagadas.
Redefinir senha invalida tokens de recuperacao e de alteracao de e-mail pendentes, mantendo
a confirmacao inicial do endereco.
Os nomes das tabelas e restricoes permanecem nas migracoes `src/main/resources/db/migration`.
