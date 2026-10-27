# Portal Interno Lactec

Aplicação Angular independente para os perfis Administrador e Técnico.

## Desenvolvimento local

Requisitos: Node.js 24 ou superior e npm 10 ou superior. Com asdf, o arquivo
`.tool-versions` seleciona a versão do Node.

```bash
npm ci
npm start
```

A aplicação fica em <http://localhost:4201> e encaminha `/api` diretamente para
a API Spring em `http://localhost:8080`. Para usar outro endereço de API:

```bash
API_PROXY_TARGET=http://localhost:8080 npm start
```

## Docker

Com o Backend Lab Spring publicado na porta `8080`, configure o ambiente e
inicie a versão de desenvolvimento do Portal Interno:

```bash
cp .env.example .env
docker compose up --build -d
```

A aplicação fica em <http://localhost:4201>. Para iniciar a versão de produção:

```bash
docker compose --profile prod up --build -d web-prod
```

A versão de produção fica em <http://localhost:8081>. O Nginx entrega os arquivos
do Angular ao navegador e encaminha `/api` ao backend pelo mesmo endereço,
evitando problemas de CORS. O destino da API pode ser alterado em `.env` com
`API_PROXY_TARGET`, por exemplo:

```dotenv
API_PROXY_TARGET=http://host.docker.internal:8080
```

O Compose desta aplicação inicia apenas o Portal Interno. O Backend Lab deve
estar disponível separadamente na porta publicada `8080`; dentro do contêiner,
`host.docker.internal` aponta para o host e o proxy encaminha as chamadas
diretamente à API Spring.

Se o Spring estiver no Compose da raiz ou no Compose de `backend-lab`, a porta
HTTP é publicada em loopback por padrão. No Linux, `host.docker.internal` usa o
endereço da bridge Docker e precisa alcançar essa porta. Inicie o backend com
publicação nesse endereço:

```bash
# Na raiz do repositório:
APP_BIND_ADDRESS=0.0.0.0 docker compose up -d --build backend-lab
# Ou, dentro de backend-lab/:
APP_BIND_ADDRESS=0.0.0.0 docker compose up -d --build
```

Essa opção publica a API nas interfaces do host. Use-a no ambiente de
desenvolvimento em que os containers separados precisam acessar o backend.
No Compose integrado da raiz, o Portal Interno usa `http://backend-lab:8080`
pela rede Docker e funciona com a publicação padrão em loopback.

O administrador inicial é configurado pelo backend usando `AUTH_SEED_ADMIN_*`.
`AUTH_SEED_TECHNICIAN_*` pode preparar um técnico de desenvolvimento. Depois de entrar no
Portal Interno, a gestão de técnicos está disponível em **Técnicos** no menu
administrativo.

## Dashboard do Administrador

A rota `/admin` apresenta indicadores consultados em `GET /api/admin/dashboard`:
total de orçamentos, pendentes de aceite, clientes ativos e valor total orçado.
Os valores são agregados pelo Backend Lab a partir do PostgreSQL e atualizados
quando o dashboard é aberto ou quando o administrador tenta novamente após uma
falha. Os cards apresentam a resposta da API; uma resposta válida com totais
zerados é exibida como `0` e `R$ 0,00`.

O dashboard também oferece atalhos para **Orçamentos**, **Técnicos**,
**Empresas** e **Clientes**. A rota é exclusiva para ADMIN e usa a sessão e o
shell existentes do Portal Interno.

## Empresas e clientes

As páginas **Empresas** (`/admin/empresas`) e **Clientes** (`/admin/clientes`) permitem pesquisar, cadastrar, editar e alterar a situação dos registros. São exclusivas ADMIN e consomem diretamente a API Spring. Os dados de listagem também preenchem a edição, sem endpoint extra de detalhe.

O cadastro de cliente exige senha inicial e pelo menos uma empresa ativa; a edição pode omitir a senha para preservá-la e substituir os vínculos. Vínculos anteriores com empresas inativas são mostrados para preservar o histórico, mas não autorizam acesso. Desativar um cliente bloqueia seu login e os tokens já emitidos na próxima requisição.

Os formulários seguem os protótipos das HUs 17 a 20 e usam os componentes de painel, confirmação e o tema do Portal Interno. No cadastro de cliente, as empresas são selecionadas por caixas de seleção, em vez das linhas com botão de adicionar da HU20. O campo Cargo aparece na tabela e no formulário, como pedem os critérios dessa HU.

CNPJ suporta formato numérico de 14 dígitos ou máscara, com validação dos dígitos verificadores. E-mail/CNPJ duplicados geram mensagem de conflito. Cancelar ou fechar o painel sem salvar conserva os dados. Carregamento, erro da API e ausência de resultados têm estados separados.

## Pesquisa de orçamentos

A página **Orçamentos** (`/admin/orcamentos`) consulta diretamente a API Spring e lista os registros persistidos em páginas. A busca considera código da proposta, nome, descrição e empresa. O filtro usa os cinco códigos comerciais definidos em `orcamento-presentation.ts`; a seleção envia o código exato, e **Todos os status** omite o parâmetro. O backend valida os códigos recebidos. Os códigos recebem rótulos em português; um valor inesperado é exibido literalmente com estilo neutro.

A tabela mostra proposta, empresa, nome e descrição, data, valor e status. Ao lado do código, há um botão para copiar o número da proposta. Na coluna de ações, a lupa abre os detalhes; os demais botões permitem editar e baixar o PDF, quando disponível. A paginação usa 20 itens por padrão e oferece opções de 10, 20, 50 ou 100 itens. Após um cadastro, a lista volta à primeira página; após uma edição, mantém a página e os filtros.

O formulário lateral de criação e edição exige uma empresa ativa para novos registros e mantém o vínculo original durante a edição mesmo quando a empresa está inativa. O laboratório é informado como texto livre. Nome e e-mail do contato começam com os dados da empresa e podem ser ajustados para a proposta. Itens de serviço são editáveis em cartões compactos; subtotais e total são somente leitura e calculados a partir de quantidade e preço unitário, sem impostos ou descontos. O envio é bloqueado quando um valor não pode ser representado com precisão segura em centavos. A criação inicia com status **Pendente de Aceite**; na edição, somente os cinco status comerciais podem ser salvos. A etapa continua sob responsabilidade do sistema.

Um PDF pode ser enviado no cadastro ou na edição, com limite de 50 MiB. Na edição, deixar o campo de arquivo vazio mantém o documento atual; enviar outro PDF o substitui. O painel de detalhes mostra laboratório, escopo, contato e itens de serviço, indicando os campos sem informação nos registros antigos. Os itens aparecem em cartões para facilitar a leitura em telas estreitas.

## Consulta técnica de orçamentos

Em **Orçamentos** (`/tecnico/orcamentos`), o técnico pode pesquisar pelo código, nome da proposta ou empresa e filtrar por período de criação e status técnico. Os resultados são paginados pela API. É possível informar só a data inicial ou só a final; os dias selecionados são incluídos por inteiro, no fuso `America/Sao_Paulo`, também usado para exibir a data de criação.

O botão **Abrir** mostra os dados técnicos e os serviços propostos em um painel lateral. A API usa respostas próprias para o técnico, sem valores, contatos comerciais, etapa, status comercial ou PDF. A tela permite apenas consulta.

O status reflete o andamento das execuções: **Incompleto**, **Completo**, **Validado** ou **Publicado**. Execuções canceladas ficam fora do cálculo quando há outras ativas; se todas estiverem canceladas, aparece **Cancelado**. Sem execuções, aparece **Não informado**. Esses dois últimos rótulos usam estilo neutro. O status comercial da proposta não interfere nesse cálculo.
