# Integração do Portal do Cliente com o Backend Lab

Status: proposta para implementação futura. Este documento não representa endpoints já implementados.

Referências consultadas em 11/11/2026: branch `angelo/autolab` no commit `4ef0d59` e `origin/main` no commit `764b934`. Os recursos presentes somente em `main` estão identificados abaixo; devem ser preservados na integração das branches.

Atualização durante o merge: a base de relatórios de `main` foi integrada ao C# e ao Spring. O `compose.yaml` usa login, orçamentos e relatórios publicados Spring, com as fontes AutoLAB desconectadas. Equipamentos e indicadores retornam 501 (`lab-integration-pending`) até a implementação proposta neste documento. O `compose.auth.yaml` mantém as fontes SQL Server. As indicações de disponibilidade nas referências originais abaixo registram o ponto de partida da integração.

## Objetivo

Permitir que o Portal do Cliente use dados reais do Spring/PostgreSQL ao executar `compose.yaml`: equipamentos, análises liberadas, histórico, informações para indicadores e documentos de relatórios.

O fluxo será:

```text
Frontend -> API C# -> endpoints de cliente do Backend Lab -> PostgreSQL
```

A API C# continuará adaptando as respostas ao contrato do frontend e calculando os indicadores que já pertencem ao seu domínio. O Spring será responsável pela persistência, autorização do cliente e liberação dos resultados. O modo `compose.auth.yaml` continuará utilizando suas integrações com Backend Auth e AutoLAB.

## Base existente e trabalho novo

| Recurso | Situação nas referências consultadas | Ação futura |
| --- | --- | --- |
| Login de cliente e orçamentos em `/api/client/quotes` | Existem nas duas branches; o C# já os consome. | Reutilizar autenticação e autorização. |
| Equipamentos em `/api/equipments` | Existem, com acesso técnico. | Criar consultas próprias de cliente, filtradas pelas empresas autorizadas. |
| Execuções e medições em `/api/tests` | Existem, com acesso técnico; incluem dados em edição. | Criar consultas de resultados liberados para clientes. |
| Etapa do orçamento calculada pelo Spring | Existe nas duas branches, com evolução em `main`. | Preservar a etapa fornecida pelo Spring. |
| Preparação, validação, publicação e PDF de relatórios | Implementados em `main`. | Incorporar e reutilizar esse ciclo de vida. |
| Relatórios publicados em `/api/client/reports` e relatórios por orçamento | Implementados em `main`, juntamente com o adapter C#. | Preservar o contrato e ampliar os vínculos necessários ao histórico de equipamentos. |
| Cadastro e análises de equipamentos em `/api/client/**` | Não implementados nas referências. | Adicionar endpoints, casos de uso, DTOs e consultas. |

Restaurar os providers de amostra do C# permite demonstrações, mas não atende ao objetivo deste documento. A integração deverá consultar dados persistidos pelo Spring.

## Autenticação e autorização das novas consultas

1. Receber o Bearer Spring do cliente, encaminhado pela API C# em cada requisição.
2. Reutilizar `JwtAuthenticationFilter`, a família `/api/client/**` do `SecurityConfig` e o componente `AcessoClienteAtual`.
3. Consultar a situação atual do usuário, seu cadastro de cliente e seus vínculos com empresas ativas.
4. Aplicar o escopo de empresas antes da paginação, das contagens e da leitura de resultados ou documentos.
5. Autorizar detalhe, histórico, análise individual e PDF independentemente. Um identificador recebido na URL não comprova acesso.
6. Filtros de orçamento, equipamento ou empresa devem apenas restringir o conjunto já autorizado. Não poderão ampliá-lo.

Os endpoints técnicos existentes mantêm suas permissões. Os novos controllers serão somente de leitura para o cliente; alterações continuam no fluxo interno.

Cliente sem empresas ativas recebe listas vazias. Token ausente, inválido, expirado ou usuário inativo segue o comportamento de autenticação existente. Para recursos de outra empresa, preservar a convenção dos endpoints de cliente atuais: acesso proibido; recurso inexistente ou relatório ainda indisponível: não encontrado. Cobrir a interação entre perfil e filtro JWT em testes, pois o filtro atual também recusa tokens internos nas rotas de cliente.

## Resultados liberados e consistência com o PDF

As consultas laboratoriais do cliente devem partir dos relatórios publicados e dos seus snapshots validados, conforme a implementação de `main`:

- Reutilizar `DisponibilidadeRelatorios` para verificar validação, publicação e disponibilidade do PDF persistido.
- Reutilizar `MontadorSnapshotRelatorio.conteudo` para ler o conteúdo validado.
- Usar `validatedCompany`, `validatedQuote`, `validatedSample` e a referência de equipamento contida no snapshot para identificar os vínculos da publicação.
- Entregar medições, classificação e conclusão do mesmo conteúdo usado no documento publicado.
- Manter as verificações de vínculo atual do cliente; a existência de um snapshot não concede acesso por si só.

Uma edição posterior da execução não deve alterar os valores de uma análise já publicada. Rascunhos, resultados em manutenção e relatórios apenas validados não devem aparecer nessas consultas.

O DTO interno `RelatorioDtos.Detalhe` contém informações de responsáveis e instrumentos. Criar uma projeção específica para o cliente, com os campos necessários ao portal, em vez de expor esse DTO integralmente.

## Endpoints a adicionar

As rotas e nomes abaixo são propostas. Devem ser confirmados com os adapters C# antes da implementação.

| Método e rota | Finalidade | Contrato mínimo |
| --- | --- | --- |
| `GET /api/client/equipments` | Listar equipamentos das empresas autorizadas. | Página de resumos cadastrais, quantidade de análises publicadas e referência da análise publicada mais recente. |
| `GET /api/client/equipments/{equipmentId}` | Consultar um equipamento autorizado. | Cadastro, empresa e metadados do histórico publicado. |
| `GET /api/client/equipments/{equipmentId}/analyses` | Consultar o histórico liberado de um equipamento. | Página de análises com medições e referência do relatório publicado. |
| `GET /api/client/analyses/{analysisId}` | Consultar uma análise liberada específica. | Detalhe do mesmo snapshot exibido no histórico, após autorização própria. |
| `GET /api/client/reports/{reportId}` | Consultar metadados e conclusão de um relatório publicado. | Identidades de empresa, orçamento, amostra, equipamento e análise; datas disponíveis e referência de download. |

O histórico deverá funcionar quando o equipamento tiver análises de tipos diferentes ou várias análises do mesmo tipo. A resposta deve distinguir ausência de histórico, ausência de medições de um tipo e falha de consulta.

Equipamentos cadastrados sem análise publicada podem aparecer no cadastro com contagem zero e referência mais recente nula. Isso não os transforma em equipamentos com indicadores disponíveis; o C# deverá adaptar essa situação à tela atual.

### Relatórios já disponíveis em `main`

Preservar as rotas existentes:

| Método e rota | Função existente |
| --- | --- |
| `GET /api/client/reports` | Lista de relatórios publicados e disponíveis ao cliente. |
| `GET /api/client/quotes/{quoteId}/reports` | Conclusão e relatórios publicados de um orçamento autorizado. |
| `GET /api/client/reports/{reportId}/file` | PDF publicado persistido, após autorização. |

Ampliar os metadados por campos aditivos ou pelos novos endpoints de detalhe. Não substituir silenciosamente a lista existente por um objeto paginado, pois `SpringReportDocumentRepository` em `main` espera um array. Se for necessário paginar essa rota, combinar a migração com o adapter e seus testes.

Os PDFs do Spring são documentos publicados pelo seu ciclo de validação. A publicação não comprova assinatura digital; o contrato compartilhado com AutoLAB precisa distinguir disponibilidade do PDF e existência de assinatura.

## DTOs e identidade

### Cadastro do equipamento

Criar DTOs próprios para resumo e detalhe do cliente, com:

- `id` como string, usando a identidade persistida do Spring.
- Empresa por `id` e nome.
- Número de série, tag, tipo e subestação.
- Tensão primária, com unidade definida no contrato.
- `publishedAnalysisCount` e referência `latestPublishedAnalysis`, que pode ser nula.

A entidade atual `Equipamento` não possui fabricante, ano de fabricação, potência, tipo de óleo ou volume de óleo. Esses campos podem permanecer indisponíveis no portal na primeira entrega. Adicionar persistência, validação e edição no Portal Interno somente se o produto exigir seu preenchimento; não introduzir valores demonstrativos como cadastro real.

Definir o tratamento de equipamento inativo: recomendação inicial de omiti-lo do cadastro ativo, preservando o acesso autorizado a publicações históricas. A desativação não deverá apagar relatórios publicados.

### Análise publicada

O DTO deverá carregar:

| Grupo | Campos necessários |
| --- | --- |
| Identidade | `id` da análise, `reportId` e `testId`, todos como strings; código do ensaio e código do relatório para exibição. |
| Vínculos | Empresa, orçamento, amostra e equipamento analisado, cada qual com sua identidade disponível. Equipamento pode ser nulo. |
| Protocolo | Protocolo persistido do orçamento, quando disponível, separado da identidade da análise. |
| Tipo | Tipo de ensaio e `analysisType`, preservando os códigos existentes. |
| Datas | Agendamento, validação e publicação disponíveis; conclusão efetiva somente se houver origem persistida para ela. |
| Resultado | Classificação técnica informada e conclusão/texto do laudo publicado. |
| Medições | Lista ordenada com código, nome, unidade, método, limite de referência e resultado tipado. |
| Documento | Disponibilidade do PDF e identidade necessária ao download existente. |

O protocolo do orçamento pode se repetir entre amostras e execuções. Definir `analysisId` como uma identidade estável da publicação, por exemplo o ID do relatório publicado, e documentar essa escolha. Não usar o protocolo como chave única.

O ID do equipamento será a referência para agrupar seu histórico. Número de série é dado de exibição e pode não ser globalmente único. Relatórios sem equipamento analisado continuam acessíveis pela consulta de relatórios/orçamento, sem serem agrupados artificialmente em um equipamento.

Definir a política para mais de uma publicação da mesma execução: uma nova versão documental não deve virar automaticamente uma nova coleta nos gráficos. Separar identidade da execução, da publicação e do ponto de medição antes de implementar contagens e séries.

### Medições

Reutilizar a representação já presente em `EnsaioDtos` e `RelatorioSnapshot`:

| `result.kind` | Conteúdo | Regra |
| --- | --- | --- |
| `quantity` | `value`: decimal em string. | Preservar precisão; não converter para `double` na serialização. |
| `not-detected` | Sem valor numérico. | Manter distinto de quantidade zero. |
| `not-performed` | Sem resultado medido. | Manter distinto de não detectado. |
| `text` | `text`: conteúdo informado. | Não interpretar automaticamente como número. |

Preservar a ordem das medições e os campos `code`, `name`, `unit`, `method` e `referenceLimit`. Limites e métodos ausentes continuam nulos. Medições desconhecidas pelo cálculo de indicadores não devem impedir o acesso ao relatório nem ser descartadas pelo Spring.

Os códigos de alguns templates Spring diferem dos códigos usados pelo C#. O adapter precisará de mapeamento explícito, por exemplo:

| Spring | Código atual no domínio C# |
| --- | --- |
| `neutralization-index` | `neutralizacao` |
| `water-content` | `agua` |
| `density` | `densidade` |
| `power-factor-100c` | `fatorDePotencia` |
| `dielectric-strength` | `rigidez` |
| `interfacial-tension` | `tensaoInterfacial` |
| `combustible-gases` | `combustiveis` |
| `total-gases` | `total` |

Os códigos dos gases, como `h2` e `ch4`, já coincidem. Validar também unidade e significado da medição: igualdade de nome ou código não basta para permitir um cálculo. Não mudar os códigos existentes no Spring apenas para imitar os nomes das colunas do AutoLAB.

### Lacunas de datas e dados históricos

`dataAgendada` não é a data de conclusão do ensaio. `validatedAt`, `publishedAt` e `atualizadoEm` também têm significados próprios. O modelo atual não oferece uma data específica de conclusão efetiva para alimentar automaticamente `AnalysisEndDate` do C#.

Antes de exibir séries temporais, escolher e documentar uma das soluções: acrescentar a data real de conclusão ao fluxo técnico e ao snapshot; ou adaptar o contrato C# para apresentar a data de publicação com seu significado correto. Não preencher uma data ausente com agendamento ou atualização.

Se o snapshot passar a incluir novos campos, manter leitura dos snapshots já persistidos. A unidade da tensão primária e o vínculo histórico do equipamento também devem ser confirmados antes de produzir DTOs definitivos.

## Paginação, consultas e erros

- Para as novas listas, usar `items`, `page`, `pageSize` e `totalItems`, conforme o padrão Spring existente.
- Proposta inicial: `page=1`, `pageSize=20`, máximo de 100 itens; valores inválidos retornam 400.
- Ordenar o cadastro por ID e os históricos por data de publicação decrescente, com desempate pelo ID da publicação.
- Aplicar filtros e autorização no banco antes de paginar. O total considera apenas registros autorizados e visíveis.
- A lista de equipamentos deve paginar equipamentos, sem dividir o mesmo equipamento entre páginas de medições.
- Buscar contagens e referências mais recentes em lote. Evitar uma consulta ou requisição HTTP por equipamento.
- Carregar snapshots e medições somente quando necessários; listagens cadastrais não devem carregar todos os PDFs.
- As leituras não alteram execução, etapa, publicação ou documento persistido.
- Preservar o formato de erro existente do Spring. O C# continuará responsável por sua tradução ao contrato público do portal.
- Falha ou indisponibilidade do Spring deve ser tratada como erro; não substituir por amostras ou consultas ao AutoLAB.

## Componentes a criar ou ampliar

Os nomes são sugestões e devem seguir as convenções da implementação resultante do merge.

| Camada | Trabalho previsto |
| --- | --- |
| `presentation/rest` | Controllers de equipamentos e análises do cliente; detalhe público de relatório. |
| `application/usecase` | Casos de uso para listar/detalhar equipamentos, listar histórico e consultar análise/relatório publicado. |
| `application/dto/response` | DTOs específicos de cadastro e resultados liberados; paginação das novas consultas. |
| `application/service` | Reutilizar acesso do cliente, disponibilidade e leitura do snapshot; centralizar a projeção pública e as regras de identidade/histórico. |
| `domain/repository` e `infrastructure/persistence` | Consultas por empresas autorizadas, equipamento e publicação, com contagens, paginação e leitura em lote. |
| Modelo e persistência | Somente as lacunas confirmadas, como data de conclusão e campos adicionais de cadastro; planejar migração e compatibilidade de snapshots. |
| `src/test` | Testes de acesso, publicação, contratos, identidade e persistência. |

O snapshot atual é texto JSON. Avaliar como consultar o equipamento histórico sem ler todos os snapshots em memória. Se necessário, persistir uma referência/indexação do equipamento na validação do relatório, com preenchimento dos registros antigos e sem alterar o conteúdo histórico publicado.

## Dependências no C# e no Compose

Estas alterações pertencem a outros módulos, mas são necessárias para consumir os novos endpoints:

| Configuração de `portal-api` | Valor pretendido em `compose.yaml` |
| --- | --- |
| `Portal__DataSource` | `backend-lab`, já suportado. |
| `BackendLab__BaseUrl` | `http://backend-lab:8080`, pelo mecanismo de configuração existente. |
| `LabResults__Source` | Novo provider `backend-lab`. |
| `EquipmentRegistry__Source` | Novo provider `backend-lab`. |
| `LaboratoryProgress__Source` | `not-connected`; a etapa já vem das consultas de orçamento Spring. |
| `ReportDocuments__Source` | Futuro provider `backend-lab` para enriquecer equipamentos/análises. Preservar o bridge de relatórios publicados já integrado; hoje `not-connected` desliga apenas o provider AutoLAB. |

Os novos valores de source ainda não existem no código atual. A implementação precisará registrar adapters HTTP que encaminhem o Bearer da requisição atual e traduzam DTOs, paginação e falhas.

No provider Spring, a autorização será aplicada pelo Java com esse Bearer. Ajustar a resolução de escopo do C# para esse caminho: ela não poderá depender da claim AutoLAB `inquilino` nem de `LabResults__DevelopmentTenant` para liberar ou filtrar dados Spring.

As portas atuais do C# contêm pressupostos do AutoLAB: protocolo como identidade da análise, chave de equipamento baseada em série e ID de relatório como GUID. Generalizar esses pontos para IDs Spring sem perder a compatibilidade do modo AutoLAB. A listagem cadastral com resumo mais recente também deverá ser conciliada com `ILabTestRepository`, que hoje devolve análises paginadas por equipamento.

O backend Spring não precisa receber nem implementar as opções `LabResults__Source` e similares; elas selecionam providers na API C#. O frontend continuará consumindo os contratos públicos do C#.

## Sequência recomendada

1. Integrar a base de relatórios de `main`, preservando login, orçamentos e publicação existentes.
2. Fechar os contratos de identidade, datas, histórico, unidades e campos ausentes com os consumidores C#.
3. Implementar consultas de equipamentos do cliente e autorização por empresa, com testes.
4. Implementar histórico e análise individual a partir das publicações e snapshots, com testes de consistência documental.
5. Completar metadados de relatórios e vínculos com equipamentos, preservando o download existente.
6. Implementar os adapters e ajustes de contrato necessários no C#, selecionar os providers em `compose.yaml` e validar o fluxo completo.
7. Verificar separadamente que `compose.auth.yaml` continua usando AutoLAB e seus documentos.

## Critérios de aceite

- [ ] `compose.yaml` atende ao Portal do Cliente com dados Spring/PostgreSQL, sem depender de SQL Server ou retornar amostras como resultados reais.
- [ ] Um cliente acessa equipamentos e resultados das suas empresas ativas; outro cliente não amplia esse escopo por filtros ou IDs.
- [ ] Cliente com várias empresas acessa a união autorizada sem duplicatas; sem empresas ativas, recebe listas vazias.
- [ ] Alterar ou remover vínculos, ou desativar o usuário/empresa, afeta novas consultas com um token ainda não expirado.
- [ ] Detalhe, histórico, análise e PDF aplicam a mesma política de acesso.
- [ ] Dados em rascunho, não publicados ou sem documento disponível seguem as regras de liberação existentes.
- [ ] Editar uma execução após a publicação não altera as medições expostas para aquela publicação.
- [ ] Protocolos repetidos, séries repetidas, análises de tipos diferentes e ausência de equipamento não misturam históricos.
- [ ] A política de versões documentais não duplica pontos de uma mesma medição nos gráficos.
- [ ] Os quatro tipos de resultado preservam seu significado e a precisão decimal.
- [ ] Datas e campos cadastrais ausentes permanecem identificados como ausentes.
- [ ] Paginação e contagens refletem o conjunto autorizado, com ordenação estável.
- [ ] As consultas de orçamento preservam a etapa calculada pelo Spring.
- [ ] PDFs publicados são os documentos persistidos do relatório autorizado.
- [ ] Os fluxos existentes de ADMIN e TECNICO e os contratos de relatórios de `main` continuam funcionando.
- [ ] Os testes Spring passam; consultas e migrações relevantes também são verificadas em PostgreSQL.
- [ ] O fluxo C# e frontend é validado nas duas composições, executadas independentemente.

## Referências para implementação

- [Arquitetura e execução do Backend Lab](../README.md).
- [Autorização HTTP atual](../src/main/java/br/ufpr/tcc/backend_lab/infrastructure/config/SecurityConfig.java).
- [DTOs e medições das execuções](../src/main/java/br/ufpr/tcc/backend_lab/application/dto/response/EnsaioDtos.java).
- [Templates e códigos de medição](../src/main/java/br/ufpr/tcc/backend_lab/application/usecase/TemplateMedicaoUseCase.java).
- [Contrato atual de análises no C#](../../backend-portal-web/PortalHexagonalApi/Portal.Application/LabResults/ILabTestRepository.cs).
- [Modelo atual de identidade de análise no C#](../../backend-portal-web/PortalHexagonalApi/Portal.Domain/LabResults/SampleAnalysis.cs).
- Em `origin/main` no commit `764b934`: `RelatorioClienteController`, `ListarRelatoriosClienteUseCase`, `ObterPdfRelatorioClienteUseCase`, `DisponibilidadeRelatorios`, `MontadorSnapshotRelatorio`, `RelatorioSnapshot` e `SpringReportDocumentRepository`. Esses arquivos devem ser consultados nessa referência enquanto ainda não existirem na branch de trabalho.
