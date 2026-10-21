# Backend Auth

Serviço independente de autenticação e gestão de contas com Java 21, Spring Boot 4.1.1, JPA e SQL Server. Segue a organização do `backend-lab`: `Controller REST -> Use Case -> Repository -> banco`, nas camadas `presentation`, `application`, `domain` e `infrastructure`.

O administrador do Backend Auth gerencia somente este serviço. Essa permissão é independente dos perfis usados pelo Portal Web. Criar um administrador aqui não cria nem concede um perfil no portal. O frontend de administração será desenvolvido separadamente; por enquanto, há apenas uma página para o cadastro inicial.

O login do portal retorna somente identidade. A administração usa Bearer tokens opacos emitidos pelo login exclusivo de administradores, sem JWT. A integração com a API C# permanece separada.

## Swagger UI

Com a API iniciada pelo Compose, abra [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html). A especificação OpenAPI está em `/v3/api-docs` (JSON) e `/v3/api-docs.yaml` (YAML). Na execução Java fora do Docker, a porta padrão é `8080`.

Use **Try it out** e **Execute** para enviar requisições. Primeiro execute `POST /api/admin/auth/login` com o e-mail e a senha do administrador do Backend Auth. Copie `accessToken` da resposta e cole em **Authorize → serviceAdmin**, sem escrever o prefixo `Bearer`. O Swagger acrescenta o cabeçalho nas operações protegidas. HTTP Basic não é aceito.

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

Os dois recursos oferecem CRUD:

| Método | Administradores do serviço | Usuários do portal | Resultado |
| --- | --- | --- | --- |
| POST | `/api/admins` | `/api/users` | Cria conta ativa; 201 |
| GET | `/api/admins` | `/api/users` | Lista contas ativas e inativas; 200 |
| GET | `/api/admins/{id}` | `/api/users/{id}` | Consulta por ID; 200 |
| PUT | `/api/admins/{id}` | `/api/users/{id}` | Atualiza cadastro; 200 |
| DELETE | `/api/admins/{id}` | `/api/users/{id}` | Remove a conta nesse escopo; 204 |

POST de administrador recebe `{name,email,password}`; POST de usuário recebe também `role`, atualmente `CLIENTE`. As listagens são arrays ordenados por nome e ID. GET e PUT retornam também `active`; não retornam senha, hash ou tokens.

PUT de administrador exige `{name,email,active}` e PUT de usuário exige `{name,email,role,active}`. `password` é opcional: omita para preservar a senha; quando enviada, segue as regras de uma senha inicial. Exemplo de atualização:

```json
{"name":"Cliente atualizado","email":"cliente@example.com","role":"CLIENTE","active":true}
```

Desativar ou remover o último administrador ativo retorna 409, inclusive em requisições concorrentes. Isso evita bloquear a administração ou reabrir a configuração inicial. Exclusão de uma conta sem outro vínculo remove o registro; se a conta também tiver uma permissão no outro escopo, essa permissão e sua identidade são preservadas. Assim, excluir um perfil de portal não remove sua eventual administração do serviço, e vice-versa.

Para cadastrar um cliente com o token retornado pelo login administrativo:

```bash
curl http://127.0.0.1:8081/api/users -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' --data '{"name":"Cliente","email":"cliente@example.com","password":"uma senha forte 123!","role":"CLIENTE"}'
```

O perfil de portal disponível é `CLIENTE`. Novos perfis podem ser adicionados futuramente em `PerfilUsuario`; eles não concedem administração do Backend Auth. As APIs de cadastro de clientes não permitem atribuir a permissão de administrador do serviço.

## Login do portal e contrato HTTP

```http
POST /api/auth/login
Content-Type: application/json

{"email":"cliente@example.com","password":"uma senha forte 123!"}
```

Resposta HTTP 200:

```json
{"id":"2","name":"Cliente","email":"cliente@example.com","role":"CLIENTE"}
```

Uma conta criada somente para administrar o serviço não autentica como cliente do portal. Se uma conta tiver futuramente um perfil de portal explicitamente atribuído, esse perfil continuará independente da permissão administrativa. IDs são strings, senhas e hashes não são retornados, e respostas de login usam `Cache-Control: no-store`.

Nomes são aparados e aceitam até 150 caracteres; e-mails são aparados, convertidos para minúsculas e aceitam até 254 caracteres. O e-mail é único entre todas as contas. Novas senhas têm pelo menos 12 caracteres e no máximo 72 bytes UTF-8, sem remover espaços ou normalizar seu conteúdo. Os hashes usam BCrypt.

Erros seguem `{"message":"..."}`: 400 para dados inválidos, 401 para credenciais ou tokens ausentes, inválidos, expirados ou revogados, 403 para operações sem permissão administrativa, 404 para conta inexistente nesse escopo e 409 para e-mail duplicado ou tentativa de remover o último administrador ativo. Conta inexistente, inativa e senha incorreta têm a mesma resposta 401. Na configuração inicial, link inválido retorna 404 e configuração já concluída retorna 410.

`GET /health` é público e retorna `{"status":"UP"}`. O futuro frontend também poderá usar `POST /api/setup/admin` com o cabeçalho `X-Setup-Token` do link e o JSON `{name,email,password}`; nenhuma permissão ou perfil é aceito nesse cadastro.

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

Use um login próprio para a aplicação no banco externo e publique a API por HTTPS. A exceção `trustServerCertificate=true` está apenas no Compose local, para o certificado autoassinado. O processo Java não carrega automaticamente o `.env` do Compose. Não há fallback para `backend-lab` ou sincronização de contas entre módulos.

## Build e testes

Com Java 21 selecionado pelo asdf:

```bash
./mvnw test
./mvnw package
java -jar target/backend-auth-0.0.1-SNAPSHOT.jar
```

Os testes usam H2 em modo SQL Server. Cobrem o link e formulário inicial, configuração concorrente, reinícios, validação, separação entre administradores e clientes, Bearer tokens e revogação, CRUD, proteção concorrente do último administrador, unicidade, BCrypt e limites de senha. Para verificar SQL Server real, inicie um Compose isolado, complete o cadastro pelo link, crie um cliente pela API e confirme o login após recriar os containers preservando o volume.

```bash
docker compose down
```

Esse comando preserva os dados. Use `down -v` somente quando puder descartar o banco local. O frontend completo e a recuperação de senha ficam para desenvolvimento futuro.
