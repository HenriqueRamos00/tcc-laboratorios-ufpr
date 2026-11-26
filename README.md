# tcc-laboratorios-ufpr

Portal de laboratórios desenvolvido como TCC na UFPR, com aplicações para clientes e equipe interna.

| Módulo | Responsabilidade |
| --- | --- |
| [Frontend](frontend/README.md) | Portal do Cliente em Angular |
| [Frontend Interno](frontend-interno/README.md) | Portal Interno em Angular |
| [Frontend Auth2](frontend-auth2/README.md) | Portal administrativo do Auth2 em Angular |
| [Backend Portal Web](backend-portal-web/README.md) | API C# consumida pelo Portal do Cliente e Android |
| [Backend Lab](backend-lab/README.md) | API Spring e PostgreSQL do Portal Interno |
| [Backend Auth2](backend-auth2/README.md) | Autenticação compartilhada em Spring Boot e Oracle |
| [Android](app-android/README.md) | Aplicativo móvel e ambiente de build |

## Executar com Docker Compose

Requisitos: Docker Engine e Docker Compose. Na raiz do repositório:

```bash
cp .env.example .env
```

Preencha `AUTH_SEED_ADMIN_PASSWORD` para criar o administrador do Portal Interno e configure
`JWT_SECRET` com pelo menos 32 bytes UTF-8. Em seguida:

```bash
docker compose up -d --build
```

| Aplicação | Endereço padrão |
| --- | --- |
| Portal do Cliente | http://localhost:4200 |
| Portal Interno | http://localhost:4201 |
| API C# | http://localhost:5125 |
| Backend Lab | http://localhost:8080 |

O `compose.yaml` usa o Backend Lab para autenticação e consultas, com PostgreSQL e hot reload
nos portais. Equipamentos e indicadores do cliente aguardam a
[integração com o Spring](backend-lab/docs/integracao-portal-cliente.md).
Configuração, dados de demonstração e execução isolada estão nos READMEs dos módulos.
Para incluir o ambiente de build Android, use `docker compose --profile android up -d --build`.

## Portal do Cliente com Auth2

O `compose.auth.yaml` inicia Auth2 com Oracle, SQL Server para os dados do portal, API C#,
Portal do Cliente e Portal Administrativo Auth2. Exige `backend-portal-web/.env` com a conexão do
AutoLAB real e a VPN do Lactec; sem `AutoLab__User` e `AutoLab__Password`, a API C# não inicia.

```bash
docker compose -f compose.auth.yaml up -d --build --remove-orphans
```

O Portal Administrativo Auth2 fica em http://localhost:4202. Ele não depende do AutoLAB; para
subir só esse portal, veja o [README do Frontend Auth2](frontend-auth2/README.md#docker-compose).
As contas, senhas, vínculos de empresas e configurações da demonstração estão no
[README do Auth2](backend-auth2/README.md#demonstração-do-portal).
Os dois arquivos Compose são independentes; encerre o outro ambiente antes de iniciar.

## E-mail e recuperação de senha

Configure o SMTP no `.env` da raiz. Cada backend tem seu próprio grupo de variáveis:

| Variáveis | Serviço | Uso |
| --- | --- | --- |
| `SPRING_MAIL_*` | Backend Lab (`compose.yaml`) | Recuperação no Portal Interno e no Portal do Cliente em modo `backend-lab` |
| `AUTH_MAIL_*` | Backend Auth2 (`compose.auth.yaml`) | Recuperação de clientes e confirmação de e-mail em modo `lactec` |

Nos dois grupos, preencha `HOST`, `PORT`, `USERNAME`, `PASSWORD` e `SENDER`. `USERNAME` é a conta SMTP e `SENDER` é o remetente autorizado pelo provedor. Os serviços podem usar a mesma conta ou contas diferentes; preencha cada grupo separadamente.

No Auth2, o Compose passa `AUTH_MAIL_USERNAME` e `AUTH_MAIL_PASSWORD` como `SPRING_MAIL_USERNAME` e `SPRING_MAIL_PASSWORD` dentro do container. O remetente `AUTH_MAIL_SENDER` vira `AUTH_NOTIFICATION_SENDER`. Os nomes separados no `.env` permitem configurar os dois serviços sem misturar as contas.

Sem `AUTH_MAIL_*`, o `compose.auth.yaml` entrega o e-mail do Auth2 ao Mailpit, sem STARTTLS, e as mensagens aparecem em http://localhost:8025. Para um provedor real, preencha os cinco valores e `AUTH_MAIL_STARTTLS=true`.

No Backend Lab, habilite `SPRING_MAIL_ENABLED=true`. Essa opção não se aplica ao Auth2. Para Gmail, use uma [senha de app com verificação em duas etapas](https://support.google.com/accounts/answer/185833?hl=pt-BR). Mantenha as credenciais no `.env` local.

Os links do Backend Lab usam `INTERNAL_PORTAL_RECOVERY_URL` e `CLIENT_PORTAL_RECOVERY_URL`; o Spring acrescenta o token. No Auth2, mantenha `{token}` em `AUTH_RECOVERY_LINK` e `AUTH_CONFIRMATION_LINK`. Os exemplos estão no `.env.example`.

Depois de alterar o `.env`, recrie o backend usado, com suas dependências já rodando:

```bash
docker compose up -d --no-deps --force-recreate backend-lab
# Para o stack Auth2, use este comando:
docker compose -f compose.auth.yaml up -d --no-deps --force-recreate backend-auth2
```

`docker compose restart` não recarrega essas variáveis. Os arquivos `.env` dos frontends não configuram o SMTP.

Se o e-mail não chegar, confira os logs do backend que envia. `MailAuthenticationException` indica falha na autenticação SMTP. A API responde antes do envio, por isso a resposta de sucesso não confirma a entrega.

## Comandos úteis

```bash
docker compose ps
docker compose logs -f portal-api backend-lab
docker compose --profile android down
```

`down` preserva os volumes. Para o ambiente Auth2, use `-f compose.auth.yaml` nos comandos.
