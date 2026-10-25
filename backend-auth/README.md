# Backend Auth

Serviço independente de autenticação e gestão de contas com Java 21, Spring Boot 4.1.1, JPA e SQL Server. Segue a organização do `backend-lab`: `Controller REST -> Use Case -> Repository -> banco`, nas camadas `presentation`, `application`, `domain` e `infrastructure`.

O administrador do Backend Auth gerencia somente este serviço. Essa permissão é independente dos perfis usados pelo Portal Web. Criar um administrador aqui não cria nem concede um perfil no portal. O frontend de administração será desenvolvido separadamente; por enquanto, há apenas uma página para o cadastro inicial.

Cada cliente pertence a um serviço cadastrado, por exemplo `portal-web`. Integrações usam Bearer tokens opacos daquele serviço para consultar e autenticar somente seus clientes; o login do cliente retorna somente identidade. A administração do Backend Auth usa seus próprios Bearer tokens, emitidos pelo login exclusivo de administradores, sem JWT. A integração com a API C# permanece separada.

## Seed de demonstração

Para testar o sistema completo, execute na raiz do repositório:

```bash
docker compose -f compose.auth.yaml up -d --build
```

Esse Compose independente inicia SQL Server, este serviço, API C# e frontend.
Não requer `.env`, cadastro inicial nem emissão manual de token. Abra o portal em
http://localhost:4200 e a administração via Swagger em http://localhost:8081/swagger-ui.html.

| E-mail e senha | Permissão | Empresa autorizada / CNPJ |
| --- | --- | --- |
| `lactec@lactec.com` | Administrador somente deste serviço | — |
| `client1@lactec.com` | `CLIENTE` de `portal-web` | EMPRESA DE ENERGIA / `04252011000110` |
| `client2@lactec.com` | `CLIENTE` de `portal-web` | DISTRIBUIDORA DE ENERGIA / `11222333000181` |

O seed usa BCrypt e cria empresas, serviço, contas e token em uma transação. As
senhas simples são uma exceção exclusiva do seed; as APIs de cadastro mantêm a
política normal. A existência do administrador fecha o formulário inicial.
Reinícios criam somente registros ausentes, preservando nomes, senhas, estado e
vínculos já editados. Colisões de e-mail com outro perfil/serviço e tokens já
vinculados a outro serviço abortam o seed sem alterar registros. O token de serviço
é armazenado apenas como hash; permanece válido entre reinícios e é renovado pelo
seed quando expira, respeitando `SERVICE_TOKEN_EXPIRATION_SECONDS`.

Fora do Compose demo, `AUTH_DEMO_SEED_ENABLED` é `false` por padrão. Para habilitar
somente neste módulo, configure essa variável e `BACKEND_AUTH_SERVICE_TOKEN`
(`svc_` seguido de 43 caracteres base64url). A execução Java lê variáveis do processo;
o Compose isolado lê seu `.env`. O portal deve usar o mesmo token.

O Compose demo publica padrões locais de senha SQL, token de serviço e chave JWT,
substituíveis pelo `.env` da raiz. Seu banco fica no volume dedicado
`tcc-auth-demo_auth-sqlserver-data`, separado do Compose deste módulo. Para parar,
use `docker compose -f compose.auth.yaml down` na raiz. Para apagar exclusivamente
o ambiente demo e recriar as contas, use `down -v` e depois `up -d --build` com o
mesmo arquivo. Esses comandos não alteram o banco do Compose isolado deste módulo.

## Swagger UI

Com a API iniciada pelo Compose, abra [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html). A especificação OpenAPI está em `/v3/api-docs` (JSON) e `/v3/api-docs.yaml` (YAML). Na execução Java fora do Docker, a porta padrão é `8080`.

Use **Try it out** e **Execute** para enviar requisições. Primeiro execute `POST /api/admin/auth/login` com o e-mail e a senha do administrador do Backend Auth. Copie `accessToken` da resposta e cole em **Authorize → serviceAdmin**, sem escrever o prefixo `Bearer`. O Swagger acrescenta o cabeçalho nas operações protegidas. HTTP Basic não é aceito. Para testar uma integração, emita um token em `POST /api/services/{serviceId}/tokens`, copie `accessToken` e use **Authorize → serviceToken**. Use apenas um dos dois Bearer tokens por vez: ambos ocupam o cabeçalho `Authorization`; remova a autorização anterior ao alternar entre administração e integração. `GET /api/users` e `GET /api/users/{id}` aceitam os dois tipos com escopos diferentes; o login de clientes exige `serviceToken`.

Antes do primeiro cadastro, também é possível testar `POST /api/setup/admin`: em **Authorize → setupToken**, cole somente o valor do parâmetro `token` do link exibido nos logs. Após criar o primeiro administrador, essa operação retorna 410. A documentação não divulga o valor do link inicial.

## Iniciar e criar o primeiro administrador

Execute a partir de `backend-auth/`, com Docker e Compose v2:

```bash
cp .env.example .env
```

Preencha somente `SQLSERVER_SA_PASSWORD` com uma senha forte para o SQL Server local. Depois:

```bash
docker compose up -d --build --wait
docker compose logs backend-auth
```

Enquanto não existir um administrador do serviço, os logs mostram um link como:

```text
Crie o primeiro administrador deste serviço: http://localhost:8081/setup?token=<link-gerado>
```

Abra esse link no navegador e preencha nome, e-mail e senha. Após o cadastro, o formulário e o endpoint de configuração inicial ficam indisponíveis, inclusive depois de reiniciar a aplicação. Não é preciso fornecer credenciais de administrador por variáveis de ambiente, criar arquivos de senha ou executar scripts de cadastro.

O link contém um segredo aleatório gerado a cada inicialização. Antes de concluir o cadastro, um reinício invalida o link anterior; consulte os logs novamente. Trate esse link como privado. Se o cadastro falhar na validação, ele continua disponível para corrigir os campos. Apenas uma requisição pode concluir a configuração inicial. Um administrador inativo também mantém o cadastro inicial fechado.

A API fica em `http://127.0.0.1:8081` e o SQL Server local em `127.0.0.1:1433`. Altere `AUTH_PORT`, `SQLSERVER_PORT` e `APP_BASE_URL` no `.env` se necessário. `APP_BASE_URL` define a origem exibida no link de configuração; use a URL pública HTTPS em implantação.

O Compose local usa SQL Server Developer e o login SQL `sa`, com a senha fornecida no `.env`. Um comando SQL cria o banco `backend_auth` caso não exista. Não há scripts de criação de usuários, permissões ou administrador da aplicação. O volume mantém os dados entre reinícios.

## Administração do serviço

O login de administração, preparado para o futuro frontend, é:

```http
POST /api/admin/auth/login
Content-Type: application/json

{"email":"admin@example.com","password":"a senha escolhida no formulário"}
```

Resposta HTTP 200, sem cookie e com `Cache-Control: no-store`:

```json
{
  "accessToken": "<token-opaco>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": "1", "name": "Administrador", "email": "admin@example.com" }
}
```

Esse login é exclusivo para administradores deste serviço. Clientes do portal não recebem tokens administrativos. O token é aleatório, não é JWT e seu hash SHA-256 é armazenado no SQL Server junto com a validade e a conta. A validade padrão é uma hora, configurável por `ADMIN_TOKEN_EXPIRATION_SECONDS`; faça novo login após expirar. Tokens válidos sobrevivem a reinícios da aplicação, pois são verificados no banco a cada chamada. Logins administrativos também removem registros de tokens já expirados.

Todas as operações de gestão exigem:

```http
Authorization: Bearer <accessToken>
```

A validade do token, a situação da conta e a permissão de administrador são verificadas em cada requisição. Alterar a senha, desativar ou excluir a permissão administrativa revoga os tokens existentes. Alterar somente nome ou e-mail preserva os tokens.

Administradores podem gerenciar os dois recursos de contas:

| Método | Administradores do serviço | Clientes dos serviços | Resultado |
| --- | --- | --- | --- |
| POST | `/api/admins` | `/api/users` | Cria conta ativa; 201 |
| GET | `/api/admins` | `/api/users` | Lista contas ativas e inativas; 200 |
| GET | `/api/admins/{id}` | `/api/users/{id}` | Consulta por ID; 200 |
| PUT | `/api/admins/{id}` | `/api/users/{id}` | Atualiza cadastro; 200 |
| DELETE | `/api/admins/{id}` | `/api/users/{id}` | Remove a conta nesse escopo; 204 |

POST de administrador recebe `{name,email,password}`; POST de cliente recebe também `role`, atualmente `CLIENTE`, e `serviceId`, o ID de um serviço ativo. As listagens são arrays ordenados por nome e ID. GET e PUT retornam também `active` e, para clientes, `serviceId`, `companyIds` e `authorizations.companyCnpjs`; não retornam senha, hash ou tokens. Administradores consultam todos os clientes. Tokens de serviço podem usar somente os GETs e enxergam exclusivamente os clientes associados ao próprio serviço; IDs de outro serviço retornam 404.

PUT de administrador exige `{name,email,active}` e PUT de cliente exige `{name,email,role,active,serviceId}`. Alterar `serviceId` transfere imediatamente o acesso ao cliente para o novo serviço. O campo opcional `companyIds` associa várias empresas cadastradas; em PUT, substitui a lista inteira. Omitir, enviar `null` ou `[]` remove todas as associações. `password` é opcional: omita para preservar a senha; quando enviada, segue as regras de uma senha inicial. Exemplo de atualização:

```json
{"name":"Cliente atualizado","email":"cliente@example.com","role":"CLIENTE","active":true,"serviceId":"1"}
```

Desativar ou remover o último administrador ativo retorna 409, inclusive em requisições concorrentes. Isso evita bloquear a administração ou reabrir a configuração inicial. Exclusão de uma conta sem outro vínculo remove o registro; se a conta também tiver uma permissão no outro escopo, essa permissão e sua identidade são preservadas. Assim, excluir um perfil de portal não remove sua eventual administração do serviço, e vice-versa.

Para cadastrar um cliente com o token retornado pelo login administrativo:

```bash
curl http://127.0.0.1:8081/api/users -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' --data '{"name":"Cliente","email":"cliente@example.com","password":"uma senha forte 123!","role":"CLIENTE","serviceId":"1"}'
```

O perfil de portal disponível é `CLIENTE`. Novos perfis podem ser adicionados futuramente em `PerfilUsuario`; eles não concedem administração do Backend Auth. As APIs de cadastro de clientes não permitem atribuir a permissão de administrador do serviço.

## Serviços e tokens de integração

Somente administradores do Backend Auth podem cadastrar serviços e emitir, listar ou revogar seus tokens. A administração do Backend Auth continua independente dos serviços cadastrados e dos perfis de seus clientes.

| Método | Endpoint | Uso |
| --- | --- | --- |
| POST | `/api/services` | Cria serviço ativo com `{name,code}`; 201 |
| GET | `/api/services` | Lista serviços ativos e inativos; 200 |
| GET | `/api/services/{id}` | Consulta serviço; 200 |
| PUT | `/api/services/{id}` | Atualiza com `{name,code,active}`; 200 |
| DELETE | `/api/services/{id}` | Exclui serviço sem clientes e seus tokens; 204 |
| POST | `/api/services/{serviceId}/tokens` | Emite token com `{name,expiresIn?}`; 201 |
| GET | `/api/services/{serviceId}/tokens` | Lista somente metadados dos tokens; 200 |
| DELETE | `/api/services/{serviceId}/tokens/{tokenId}` | Revoga token; 204 |

`name` é aparado e aceita até 150 caracteres. `code` é único, aparado e convertido para minúsculas; aceita até 80 caracteres com letras ASCII, números e hífens entre segmentos, como `portal-web`. Tokens usam o ID estável do serviço, portanto renomear o código não muda seus limites de acesso. IDs são strings nas respostas e podem ser enviados como strings em `serviceId`.

Fluxo de exemplo, usando o Bearer administrativo nos três primeiros passos:

1. `POST /api/services` com `{"name":"Portal Web","code":"portal-web"}`; guarde `id`.
2. `POST /api/users` com `{"name":"Cliente","email":"cliente@example.com","password":"uma senha forte 123!","role":"CLIENTE","serviceId":"<id>"}`.
3. `POST /api/services/<id>/tokens` com `{"name":"API C#","expiresIn":2592000}`.
4. Guarde `accessToken` da resposta na configuração privada do backend C#. Use esse token para os GETs de clientes e para `POST /api/auth/login`.

A emissão retorna, com `Cache-Control: no-store`:

```json
{
  "accessToken": "svc_<segredo-aleatorio>",
  "tokenType": "Bearer",
  "expiresIn": 2592000,
  "token": {
    "id": "1",
    "name": "API C#",
    "serviceId": "1",
    "createdAt": "2026-10-21T12:00:00Z",
    "expiresAt": "2026-11-20T12:00:00Z"
  }
}
```

O segredo aparece somente na emissão; o banco armazena apenas seu hash SHA-256. A listagem retorna `{id,name,serviceId,createdAt,expiresAt}`, incluindo tokens expirados; tokens revogados são removidos. `expiresIn` deve estar entre 1 e 31536000 segundos (365 dias). Quando omitido, usa `SERVICE_TOKEN_EXPIRATION_SECONDS`, por padrão 2592000 (30 dias). Para rotacionar, emita um segundo token, configure a integração e revogue o anterior. Tokens válidos sobrevivem a reinícios.

Tokens de serviço permitem apenas consultar clientes e verificar suas credenciais. O serviço autorizado é derivado do token; enviar outro `serviceId` no corpo ou na URL de consulta não amplia seu acesso. Eles não podem criar, editar ou excluir contas, administrar serviços, gerar tokens nem consultar administradores. Mantenha o token no backend da integração e use HTTPS; não o distribua ao navegador ou aplicativo de um cliente.

Desativar um serviço revoga todos os seus tokens e impede novas associações e emissões. Não altera as senhas nem o estado de seus clientes; administradores continuam podendo editar ou remover essas contas. Reativar exige emitir novos tokens; tokens anteriores continuam inválidos. Excluir um serviço com clientes associados retorna 409: primeiro transfira esses clientes com `PUT /api/users/{id}` ou exclua-os. Excluir um serviço vazio remove também seus tokens.

**Atualização de uma instalação existente:** com `JPA_DDL_AUTO=update`, são criadas as tabelas de serviços e tokens e a coluna opcional `usuarios.servico_id`. Clientes existentes ficam sem associação e continuam visíveis para administradores, mas não podem ser consultados ou autenticados por nenhuma integração até receberem `serviceId` por PUT. Não há associação automática. Instalações com schema gerenciado externamente devem aplicar a estrutura equivalente antes de iniciar com `validate`. Criar ou editar clientes pela API passa a exigir `serviceId`, e o login de clientes passa a exigir token de serviço.

## Login de clientes e contrato HTTP

A integração envia o token do serviço e as credenciais do cliente:

```http
POST /api/auth/login
Authorization: Bearer svc_<token-do-portal-web>
Content-Type: application/json

{"email":"cliente@example.com","password":"uma senha forte 123!"}
```

Resposta HTTP 200:

```json
{"id":"2","name":"Cliente","email":"cliente@example.com","role":"CLIENTE","serviceCode":"portal-web","authorizations":{"companyCnpjs":["04252011000110","12ABC34501DE35"]}}
```

Credenciais corretas de um cliente associado a outro serviço, cliente sem associação, conta inexistente, conta inativa e senha incorreta retornam a mesma resposta 401. Sem token de serviço válido, a chamada é rejeitada antes de verificar as credenciais. Um Bearer administrativo não substitui o token de integração nesse login.

Uma conta criada somente para administrar o serviço não autentica como cliente do portal. Se uma conta tiver futuramente um perfil de portal explicitamente atribuído, esse perfil continuará independente da permissão administrativa. IDs são strings, senhas e hashes não são retornados, e respostas de login usam `Cache-Control: no-store`.

Nomes são aparados e aceitam até 150 caracteres; e-mails são aparados, convertidos para minúsculas e aceitam até 254 caracteres. O e-mail é único entre todas as contas. Novas senhas de clientes têm pelo menos 8 caracteres, com letra maiúscula, letra minúscula e número obrigatórios. Caracteres Unicode são aceitos. Senhas de administradores continuam exigindo pelo menos 12 caracteres. O limite de todas as senhas é 72 bytes UTF-8, sem remover espaços ou normalizar o conteúdo. Os hashes usam BCrypt. A nova regra de composição é aplicada no cadastro e na troca da senha do cliente; senhas existentes continuam válidas até serem alteradas.

Erros seguem `{"message":"..."}`: 400 para dados inválidos, 401 para credenciais ou tokens ausentes, inválidos, expirados ou revogados, 403 para token sem permissão para a operação, 404 para conta, serviço ou token inexistente nesse escopo e 409 para e-mail/código duplicado, serviço inativo, exclusão de serviço com clientes ou tentativa de remover o último administrador ativo. CNPJ duplicado e empresa com clientes associados também retornam 409; bloqueio temporário de login retorna 429 com `Retry-After`. Conta inexistente, inativa e senha incorreta têm a mesma resposta 401. Na configuração inicial, link inválido retorna 404 e configuração já concluída retorna 410.

`GET /health` é público e retorna `{"status":"UP"}`. O futuro frontend também poderá usar `POST /api/setup/admin` com o cabeçalho `X-Setup-Token` do link e o JSON `{name,email,password}`; nenhuma permissão ou perfil é aceito nesse cadastro.

## Tentativas de login dos clientes

Cada cliente pode acumular quatro falhas consecutivas com resposta 401. A quinta falha inicia um bloqueio de 15 minutos e retorna 429, com `Retry-After: 900` e `Cache-Control: no-store`. Durante o bloqueio, até credenciais corretas retornam 429; `Retry-After` informa os segundos restantes. Requisições bloqueadas não prolongam o período.

Após o período, a próxima tentativa recebe um novo limite de cinco falhas. Login bem-sucedido e troca de senha por administrador zeram o contador e removem o bloqueio. O estado é persistido no SQL Server e as tentativas são serializadas por conta, incluindo chamadas concorrentes. Reiniciar a aplicação ou trocar o token de integração não permite evitar o limite.

A contagem usa a conta do cliente dentro do serviço autorizado pelo Bearer, sem usar o IP. A API C# deve encaminhar 429 e `Retry-After` ao seu consumidor e aguardar antes de reenviar. Outros clientes que chegam pelo mesmo portal/IP têm contadores independentes. Token inválido e credenciais referentes a outro serviço não afetam o contador da conta. Essa política é exclusiva do login de clientes.

`CLIENT_LOGIN_COOLDOWN_SECONDS` configura a duração; padrão `900` e intervalo aceito de 1 a 86400 segundos.

## Empresas e autorizações dos clientes

Empresas têm somente nome e CNPJ, além do ID gerado. O registro é global, com CNPJ único no banco; uma empresa pode estar associada a vários clientes e cada cliente pode ter várias empresas. Somente administradores do Backend Auth podem gerenciar esse cadastro e as associações.

| Método | Endpoint | Uso |
| --- | --- | --- |
| POST | `/api/companies` | Cria com `{name,cnpj}`; 201 |
| GET | `/api/companies` | Lista por nome e ID; 200 |
| GET | `/api/companies/{id}` | Consulta empresa; 200 |
| PUT | `/api/companies/{id}` | Atualiza com `{name,cnpj}`; 200 |
| DELETE | `/api/companies/{id}` | Exclui empresa sem associações; 204 |

`name` é aparado e aceita até 150 caracteres. O CNPJ aceita formato numérico e alfanumérico, com ou sem a máscara `XX.XXX.XXX/XXXX-XX`. É armazenado e retornado em maiúsculas, sem máscara, com 14 caracteres. Os dígitos verificadores seguem o [manual da Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf). CNPJ inválido retorna 400; duplicado retorna 409, inclusive em cadastros concorrentes.

Exemplo de cadastro administrativo:

```json
{"name":"Empresa de exemplo","cnpj":"04.252.011/0001-10"}
```

Associe as empresas no POST ou PUT de cliente:

```json
{
  "name": "Cliente",
  "email": "cliente@example.com",
  "password": "Valida123",
  "role": "CLIENTE",
  "active": true,
  "serviceId": "1",
  "companyIds": ["1", "2"]
}
```

IDs repetidos são associados uma vez; IDs de empresas inexistentes retornam 404, sem alterar o cliente. Em PUT, `companyIds` substitui todas as associações: omitir ou enviar `null`/`[]` remove-as. Omita `password` para preservar a senha.

O login de clientes e as respostas de criação/consulta/atualização de clientes incluem:

```json
{"authorizations":{"companyCnpjs":["04252011000110","12ABC34501DE35"]}}
```

A lista é ordenada e contém somente CNPJs de empresas associadas àquela conta. Sem empresas, retorna `{"authorizations":{"companyCnpjs":[]}}`. O login continua retornando `serviceCode`, sem `serviceId`. Os outros endpoints de clientes incluem `companyIds` para administrar os vínculos. Tokens de integração continuam limitados aos clientes do próprio serviço; não podem acessar o CRUD global de empresas nem alterar associações.

Alterar um CNPJ atualiza as autorizações retornadas nos próximos logins e consultas. Excluir uma empresa ainda associada retorna 409; remova os vínculos dos clientes primeiro. Excluir um cliente remove apenas seus vínculos e preserva as empresas.

Com `JPA_DDL_AUTO=update`, a inicialização adiciona `empresas`, a relação `usuarios_empresas`, `usuarios.falhas_login` (padrão zero) e `usuarios.login_bloqueado_ate`. Clientes existentes iniciam sem empresas e sem bloqueio; senhas e tokens são preservados. Com `validate`, aplique previamente a estrutura equivalente no schema gerenciado externamente.

## Execução fora do Compose

Provisione um SQL Server e configure o processo Java:

| Variável | Uso |
| --- | --- |
| `SERVER_PORT` | Porta Java; padrão `8080` |
| `DB_URL` | URL JDBC; padrão `jdbc:sqlserver://localhost:1433;databaseName=backend_auth;encrypt=true;trustServerCertificate=false` |
| `DB_USERNAME` | Login SQL; padrão `backend_auth` |
| `DB_PASSWORD` | Senha SQL obrigatória |
| `JPA_DDL_AUTO` | Padrão `update`; use `validate` para schema gerenciado externamente |
| `APP_BASE_URL` | Origem usada no link inicial; padrão `http://localhost:${SERVER_PORT}` |
| `ADMIN_TOKEN_EXPIRATION_SECONDS` | Validade dos tokens administrativos; padrão `3600`, valor positivo |
| `SERVICE_TOKEN_EXPIRATION_SECONDS` | Validade padrão dos tokens de integração; padrão `2592000`, entre 1 e 31536000 |
| `CLIENT_LOGIN_COOLDOWN_SECONDS` | Espera após cinco falhas consecutivas por cliente; padrão `900` (15 minutos), entre 1 e 86400 |
| `AUTH_DEMO_SEED_ENABLED` | Habilita as contas públicas locais; padrão `false` |
| `BACKEND_AUTH_SERVICE_TOKEN` | Token de integração a cadastrar quando o seed está habilitado; obrigatório nesse caso |

Use um login próprio para a aplicação no banco externo e publique a API por HTTPS. A exceção `trustServerCertificate=true` está apenas no Compose local, para o certificado autoassinado. O processo Java não carrega automaticamente o `.env` do Compose. Não há fallback para `backend-lab` ou sincronização de contas entre módulos.

## Build e testes

Com Java 21 selecionado pelo asdf:

```bash
./mvnw test
./mvnw package
java -jar target/backend-auth-0.0.1-SNAPSHOT.jar
```

Os testes usam H2 em modo SQL Server. Cobrem o link e formulário inicial, configuração concorrente, reinícios, validação, separação entre administradores e clientes, Bearer tokens e revogação, CRUD, proteção concorrente do último administrador, unicidade, BCrypt e limites de senha. Incluem CRUD de serviços, tokens de integração, isolamento de consultas e login, reassociação de clientes e contas antigas sem associação. Também cobrem complexidade de senha do cliente, bloqueio persistente e concorrente, CRUD de empresas, CNPJ numérico/alfanumérico, unicidade e autorizações com múltiplas empresas. Para verificar SQL Server real, inicie um Compose isolado, complete o cadastro pelo link, cadastre dois serviços com tokens e clientes distintos, verifique os limites de acesso e confirme a persistência após recriar os containers preservando o volume.

```bash
docker compose down
```

Esse comando preserva os dados. Use `down -v` somente quando puder descartar o banco local. O frontend completo e a recuperação de senha ficam para desenvolvimento futuro.
