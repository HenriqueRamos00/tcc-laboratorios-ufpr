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

## Comandos úteis

```bash
docker compose ps
docker compose logs -f portal-api backend-lab
docker compose --profile android down
```

`down` preserva os volumes. Para o ambiente Auth2, use `-f compose.auth.yaml` nos comandos.
