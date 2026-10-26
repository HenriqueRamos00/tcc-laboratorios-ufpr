# Round 3: execucao, com a evidencia de cada fatia

As fatias deste documento seguiram o ciclo: teste escrito primeiro, execucao mostrando o
vermelho pelo motivo certo, implementacao mais simples que faz passar, suite inteira, e entao
a mutacao do proprio requisito para provar que o teste nao e decorativo.

**Duas fatias entraram depois e nao nasceram assim.** Elas foram escritas com o codigo antes
do teste, isso foi apontado pelo integrante e as duas foram **refeitas do zero** pelo ciclo. O
registro honesto de quais fatias nasceram de teste, quais foram refeitas e o que a refacao
mudou esta na secao "O registro de TDD, fatia por fatia", no fim deste documento.

**Nota de processo.** Os agentes deste round projetaram as fatias mas nao puderam
executa-las: o tipo de agente usado e somente leitura por construcao e nao cria arquivo nem
em area temporaria, o que colide com o pedido de entregar patch e saida de Maven. Eles
mediram o que da para medir sem escrever, com `javap` e `jshell` sobre os jars resolvidos,
e isso rendeu quatro descobertas que mudaram o desenho. A execucao, os ciclos e as
mutacoes foram feitos pelo integrador.

## Fatias entregues

| Fatia | O que ela resolve | Mutacoes mortas |
| --- | --- | --- |
| Identificador TSID | chave primaria ordenavel no tempo, serializada como string | 3 |
| Log e correlacao | identificador por requisicao no MDC, lista de chaves permitidas | 2 |
| Politica de senha | regras 1 e 2 do Lactec, em funcao pura | 4 |
| Token de aplicacao | emissao, digest guardado, comparacao em tempo constante | 4, uma por guarda estrutural |
| Esquema e migracao | tabelas, restricoes, dois usuarios de banco | 4 |
| Bloqueio por tentativa | regra 3, com incremento atomico | 3 |
| Resolucao do chamador | regra 6, situacao lida junto com a credencial | 3 |
| Decisao de login | recusa uniforme, custo uniforme | 4 |
| Matriz de codigos em HTTP | as respostas da matriz, comparadas inteiras | 3 de 4 |
| Instalacao e administracao | primeiro administrador, sessao, cadastro de aplicacao | 4 |
| Ciclo do token de aplicacao | emitir, listar sem segredo, revogar na hora | 4 |
| Ciclo de vida da conta | nasce sem entrar, link libera, link de uso unico | 4 de 5 |
| Recuperacao de senha | resposta unica, proposito verificado, links derrubados | 4 |
| Escopo das rotas de leitura | recurso alheio responde como inexistente | 5 |
| Empresa | CNPJ por aplicacao, sem mascara | 4 |

## Descobertas que mudaram o desenho

1. **O Flyway nao estava ligado.** No Boot 4 a auto configuracao saiu do
   `spring-boot-autoconfigure` e virou artefato proprio. Medido: nenhum jar do Boot no
   cache continha `FlywayAutoConfiguration`, entao `spring.flyway.enabled: true` nao fazia
   nada e a migracao nunca teria rodado. A dependencia correta e `spring-boot-flyway`.
2. **A validacao de indice do Hibernate reprova um indice correto.** Com
   `hibernate.tooling.schema.index_validation: NAMED`, a subida falha com
   `Index mismatch - IX_CLIENT_ACCOUNT_COMPANY` contra um indice que o catalogo do Oracle
   reporta exatamente como declarado. A propriedade saiu, com o motivo escrito, e a
   protecao ficou nos testes de dado, que provam comportamento em vez de comparar metadado.
3. **A forma `identificador.verificador` do token esta morta.** O consumidor valida o token
   contra `^svc_[A-Za-z0-9_-]{43}$` na **inicializacao** dele, e ponto nao esta no conjunto
   permitido. Token com separador impede o Portal de subir, nao so de autenticar. A linha e
   encontrada pelo proprio digest, com restricao unica.
4. **O usuario da aplicacao nao enxerga o esquema sem ajuda.** Ele nao e dono de objeto
   nenhum, entao todo comando sem prefixo responde `ORA-00942`. A correcao nao e prefixar o
   nome do esquema no codigo, e apontar o esquema na abertura da conexao.

## Mutacoes que sobreviveram, e o que cada uma ensinou

Duas sobreviveram ao primeiro teste que deveria mata-las, e as duas apontaram para a mesma
lacuna: o caso sequencial nao exercita o guarda de concorrencia.

**O guarda de uso do link de confirmacao.** Remover `AND USED_AT IS NULL` do comando de
marcacao nao derrubou nada, porque a consulta anterior ja filtra o link usado e a segunda
chamada nem chega a gravar. O guarda so vale quando duas chamadas chegam juntas, e foi
preciso escrever o caso de corrida para a mutacao morrer. O mesmo padrao do contador de
tentativa, achado de novo em outra fatia.

**Desligar o formulario do framework.** Com cadeia propria declarada, o Spring Security 7
nao publica o formulario nem o usuario padrao, entao a chamada explicita nao muda
comportamento observavel. Ela fica como defesa em profundidade, e o teste que afirma que
`/login` nao responde 302 continua valendo contra alguem voltar para a cadeia automatica.
A mutacao nao morre, e isso esta escrito aqui em vez de ser apresentado como prova.

## O defeito que so a aplicacao de verdade mostrou

Este merece secao propria, porque e o achado mais caro da entrega e ele foi encontrado
rodando o servico, nao lendo codigo nem rodando a suite.

**Toda falha de validacao respondia 401 com desafio em vez de 400.** CNPJ com mascara,
corpo ilegivel, campo faltando e ate caminho inexistente voltavam como falha de
credencial. No consumidor medido, 401 com desafio significa fonte indisponivel, entao um
erro de digitacao do usuario apareceria na tela como servico fora do ar.

A causa: o contentor trata erro com um **segundo despacho interno** para a rota de erro, e
esse despacho tambem atravessa a cadeia de seguranca, onde nao estava liberado.

**Por que a suite nao pegou.** Os testes de contrato usavam o simulador de requisicao do
Spring, que nao faz esse segundo despacho. Os 154 testes ficavam verdes com a aplicacao
quebrada em producao. A correcao do processo foi escrever uma classe que sobe um servidor
de verdade em porta aleatoria e fala com ele por HTTP, e os quatro casos dela falharam de
cara.

Junto saiu um segundo defeito da mesma familia: a sonda de prontidao respondia 401, entao
o healthcheck do contentor **nunca** passava. Isso tinha ficado escondido porque o laco de
espera da verificacao manual vencia por tempo e seguia adiante de qualquer jeito.

A licao, que vale mais que as duas correcoes: **simulador de requisicao e um dublê, e
dublê nao reproduz o contentor.** O que depende do contentor precisa de um caso que suba o
contentor.

## O adaptador de servidor de e-mail

Ele existe agora, e com ele a frase "a unica parte que nao foi verificada ponta a ponta"
saiu deste documento. O desenho esta no `README.md`; o que importa aqui e a evidencia.

O caso obrigatorio e `mailServerThatIsDownDoesNotBreakTheRegistration`: a aplicacao sobe
apontando para uma porta onde ninguem escuta, o cadastro responde 201, a conta e o link
pendente ficam gravados, o login recusa por falta de confirmacao, e a tentativa de envio
aparece na contagem da sonda. Com o envio dentro da transacao, esse caso responderia erro de
servidor e nao sobraria conta nenhuma.

**Um achado do round mudou a configuracao do atuador, e ele vale por si.** O Boot
autoconfigura uma sonda de e-mail **ativa**: ela consulta o servidor a cada chamada de
`/health`, que e rota aberta na cadeia, e reporta `DOWN` quando o servidor cai. As duas metades
sao ruins. A primeira da a qualquer um na rede um jeito de bater no servidor de e-mail do
Lactec pela sonda do servico de autenticacao. A segunda e pior: `DOWN` na raiz de `/health`
mapeia para 503, e a borda tiraria de rotacao o unico servico de autenticacao que existe por
causa de um e-mail que nao saiu, derrubando o login junto. Ela foi desligada, e no lugar dela
entrou uma sonda **passiva**, que publica o que as tentativas de verdade observaram e devolve
`OUT_OF_SERVICE` mapeado para 200 de proposito.

## A mutacao do formulario, em detalhe

Desligar `formLogin` explicitamente na cadeia de seguranca **nao** muda comportamento
observavel nesta configuracao: com cadeia propria declarada, o Spring Security 7 nao
publica o formulario nem o usuario padrao. A chamada continua no codigo como defesa em
profundidade, e o teste que afirma que `/login` nao responde 302 continua valendo como
protecao contra alguem voltar para a cadeia automatica. Mas a mutacao nao morre, e isso
esta escrito aqui em vez de ser apresentado como prova.

O mesmo tipo de honestidade vale para dois pontos ja tratados: a comparacao em tempo
constante do digest, que nenhum teste de comportamento consegue distinguir da comparacao
comum porque as duas devolvem o mesmo booleano para toda entrada, e por isso ganhou uma
guarda estrutural declarada pelo que e; e a verificacao de descarte no caminho da conta
inexistente, que **tem** teste de comportamento, por medicao de custo com margem de mais de
uma ordem de grandeza.

## Numeros medidos

- Custo de uma verificacao de senha com os parametros escolhidos, dentro de contentor com
  teto de 768 MiB: **32 ms**.
- Nome com 150 caracteres acentuados ocupa **299 bytes**, que e o numero que explica por que
  toda coluna de texto nasce com semantica de caractere.
- Subida do contentor de Oracle para a suite de integracao: cerca de **38 segundos**, uma vez
  por execucao, com o contentor reaproveitado por todas as classes.

## O registro de TDD, fatia por fatia

O integrante perguntou se tudo tinha sido feito com TDD. A resposta era nao, e a tabela abaixo
e a resposta completa, porque declarar a divida vale mais que apresenta-la como paga.

| Fatia | Nasceu de teste? | Observacao |
| --- | --- | --- |
| As quinze fatias da tabela acima | sim | ciclo completo, com mutacao |
| Rotas de `/api/admins` | sim | o vermelho foi 405 e 404 nas rotas ausentes |
| Correcoes do round de revisao | sim | cada defeito teve teste vermelho antes da correcao, e tres deles foram medidos na aplicacao no ar |
| Trilha de auditoria | **refeita** | nasceu com codigo antes do teste, foi desmontada e reconstruida em dez ciclos |
| Adaptador de e-mail | **refeita** | nasceu de projeto de agente, com o codigo pronto; foi desmontada e reconstruida em oito ciclos |
| Testes de caracterizacao | nao, e nao podiam | fixam o que a plataforma ja fazia: 405 com `Allow`, `/logout`, usuario em memoria, documentacao gerada |

**O que a refacao mudou de fato**, porque refazer sem resultado seria cerimonia:

- O esboco do disjuntor comecou devolvendo `true` para tudo, e cada caso seguinte puxou uma
  parte dele. O estado de tres valores apareceu quando o caso da tentativa de prova pediu, e
  nao antes.
- O teste do corpo da mensagem **puxou uma costura**: ler o corpo pelo outro lado exigiria
  servidor de e-mail, entao a montagem do link passou a ser visivel no pacote, com o motivo
  escrito no proprio metodo. Na versao anterior esse comportamento existia sem teste nenhum.
- A fiacao da trilha de auditoria ganhou um teste que a versao anterior nao tinha. O teste da
  trilha isolada prova que o evento sai com os campos certos quando alguem o chama; ele nao
  prova que os eventos existem para as operacoes que a regra nomeia. Isso e fiacao, e fiacao
  se prova entrando pela rota: `AuditTrailWiringIntegrationTest` entra por nove rotas e cobra
  o evento de cada uma.
- A lista de permissao de chaves do MDC deixou de ser decorativa. Ela era declarada e nao
  aplicada: nenhum ponto do codigo a consultava. O primeiro caso do ciclo exigiu a recusa, e
  com ela um `MDC.put` de chave nova passou a falhar em vez de levar dado pessoal para o log.

**Duas honestidades sobre a refacao, ditas sem maquiagem:**

1. Em `SmtpMessageTransport` e em `MailDeliveryHealthIndicator`, o vermelho veio antes mas a
   implementacao foi colada do projeto do agente, e nao derivada caso a caso. As duas estao
   cobertas por teste que falha sem elas, e no primeiro caso a cobertura do corpo da mensagem
   foi acrescentada depois de eu perceber a lacuna.
2. A guarda estrutural da trilha passa de primeira por construcao: ela afirma a **ausencia** de
   uma linha. Teste que passa de primeira nao prova nada sozinho, e por isso a prova dela e a
   mutacao, que esta na tabela abaixo.

## As mutacoes das duas fatias refeitas

| Mutacao | Teste que fica vermelho |
| --- | --- |
| `currentActor` lendo contexto vazio sempre | `administratorEventSaysWhoDidIt`, `applicationEventSaysWhichApplicationActed` |
| lista de permissao do MDC sem efeito | `keyOutsideTheListIsRefused` |
| trilha registrando o valor em claro do token | `auditTrailSourceNeverMentionsASecret` |
| catch da falha de entrega removido | `deliveryFailureIsRecordedAndNothingReachesTheCaller`, `mailServerThatIsDownDoesNotBreakTheRegistration`, `openCircuitStopsTouchingTheTransport` |
| catch da fila cheia removido | `fullQueueDropsTheMessageWithoutThrowing` |
| disjuntor sem efeito | `openCircuitStopsTouchingTheTransport` |
| correlacao nao limpa depois do envio | `correlationIdCrossesTheThreadAndDoesNotLeakToTheNextMessage` |
| limiar do disjuntor de `>=` para `>` | `thresholdOpensOnlyOnTheDeclaredFailure`, `openCircuitReleasesOneProbeWhenTheWaitIsOver` |
| guarda da prova em curso removido | `probeInFlightIsNotReleasedAgainByTheClock` |

A ultima linha nasceu de uma **mutacao que sobreviveu**. Remover o guarda da prova em curso
nao derrubava nada, porque com o relogio parado a espera recem reiniciada ainda nao tinha
passado, e o defeito so aparece quando o tempo anda. O caso novo adianta cinco minutos com a
prova em curso e cobra que nenhuma outra saia: quem libera a proxima e o desfecho da prova,
nao o relogio.
