# Restricao por regiao: a analise, a decisao e o que falta medir

Este documento responde ao requisito 8 do Lactec, "acesso restrito ao territorio nacional".
Ele existe porque a resposta honesta a esse requisito nao e codigo, e sim uma decisao sobre
**onde** o controle mora, e essa decisao depende de fatos da rede do cliente que ainda nao
foram medidos.

## A decisao

**O controle de regiao nao entra nesta aplicacao agora.** Ele pertence a borda, e o que falta
para liga lo nao e codigo: e a resposta das perguntas da ultima secao.

Essa decisao segue a regra que o proprio `CLAUDE.md` deste modulo escreveu antes de qualquer
linha ser escrita: "Sem lista de proxies confiaveis, nao existe restricao por regiao. Ler o
primeiro valor de `X-Forwarded-For` e aceitar o pais que o atacante digitou. Enquanto essa
lista nao existir, o filtro de regiao fica desligado em vez de dar falsa sensacao."

## O fato que decide, e ele e do nosso proprio repositorio

**O Portal nao encaminha o endereco do usuario final.** O cliente .NET deste servico
(`backend-portal-web/.../BackendAuth/BackendAuthAdapter.cs`) envia o cabecalho de autorizacao
e o corpo, e nada mais: nenhum `X-Forwarded-For`, nenhum `Forwarded`. Isso esta registrado em
`docs/round-2-decisoes.md` desde o round de decisoes.

A consequencia e direta e nao tem contorno em codigo: para todo o trafego que chega aqui
vindo do Portal, **o endereco do usuario final nao existe nesta aplicacao, em forma nenhuma**.
O que a aplicacao enxerga e o endereco do Portal. Um filtro de regiao neste servico, por bem
escrito que fosse, conseguiria recusar uma chamada de integracao feita direto de fora do pais,
e **nao** conseguiria recusar o navegador de um usuario fora do pais, que e o que o requisito
pede. Essa parte do requisito 8 so e atendivel onde o navegador conecta.

## Por que a borda, e com qual condicao

A propria Azure documenta o controle de pais como regra de firewall de aplicacao, com
`GeoMatch`, e deixa escolher a variavel avaliada: `RemoteAddr`, que e o endereco que
costuma vir do `X-Forwarded-For`, ou `SocketAddr`, que e o endereco que o firewall ve de
fato. **`SocketAddr` e o que nao se forja**, e e ele que a regra precisa usar.

A condicao e a que o `CLAUDE.md` ja cobrava: **bloqueio na borda so vale com a origem
trancada**. A documentacao da Azure diz a mesma coisa sobre o produto dela, e ainda avisa que
filtrar por faixa de endereco nao basta, porque outros clientes da Azure usam as mesmas
faixas: a origem precisa conferir tambem a identidade do perfil que a borda envia. Se a porta
desta aplicacao continuar alcancavel direto, qualquer regra de pais na borda vale zero, e
quem descobrir o endereco pula a borda inteira.

## O que foi descartado, com o motivo

**Ler `X-Forwarded-For` sem lista de proxies confiaveis.** O RFC 7239 diz que o cabecalho
nao pode ser tido como correto, porque todo no do caminho pode altera lo, inclusive o cliente;
e o primeiro valor da lista e justamente o escrito por quem chamou. A Azure documenta o mesmo
sobre o proprio produto: o endereco de cliente pode ser sobrescrito a vontade pelo usuario, e
por isso ela publica um cabecalho separado com o endereco do soquete.

**Ligar `server.forward-headers-strategy` e confiar no resultado.** Em `NATIVE`, a confianca
vem da lista de proxies internos do Tomcat, cujo padrao inclui as faixas privadas inteiras.
Dentro da rede do `compose`, isso e todo mundo: qualquer contentor vizinho que alcance a porta
8081 seria tratado como proxy interno e poderia escrever o endereco que quisesse. O Tomcat e o
Spring condicionam os dois o mecanismo a uma premissa que ninguem mediu aqui, que e a borda
apagar os cabecalhos encaminhados recebidos de fora.

**Consultar servico de geolocalizacao de terceiro.** Endereco de origem e dado pessoal, e a
consulta mandaria o endereco dos usuarios do Lactec para fora. Se a restricao morar na
aplicacao, a base e local e atualizada por processo nosso, com dono e prazo.

**Assar a base de geolocalizacao na imagem.** O `Dockerfile` fixa as bases por digest e o
contentor sobe somente leitura. Uma tabela que muda todo mes dentro da imagem obriga
reconstruir a aplicacao para atualizar dado, e o digest deixa de significar "mesmo codigo". Se
a base entrar, entra montada somente leitura.

## O que isso compra, dito sem maquiagem

Restricao por regiao reduz ruido, nao risco. Rede virtual privada, proxy residencial e maquina
alugada em nuvem dentro do Brasil passam direto; base de geolocalizacao erra, e o proprio
produto da Azure tem uma classe para endereco ainda nao mapeado; e operadora movel com
endereco compartilhado gera recusa de usuario legitimo. Ela nunca e o controle que protege o
login: o que protege e senha com hash forte, limite de tentativa e limite de requisicao, e os
tres estao implementados e provados neste modulo. Se o bloqueio por regiao virar razao para
afrouxar qualquer um dos tres, ele esta fazendo mal.

Consequencia operacional que o Lactec precisa aceitar por escrito: funcionario em viagem fora
do pais deixa de acessar. Ou existe lista de excecao com dono e prazo, ou o chamado vai
chegar.

## As decisoes ja tomadas, para quando o mecanismo for ligado

Elas ficam escritas agora para nao serem improvisadas depois.

| Assunto | Decisao |
| --- | --- |
| Endereco de origem indeterminado | **recusar**, e o contrario sendo ato explicito de configuracao |
| Endereco de laco local, privado, de enlace local e unico local | classe propria, liberada, porque e endereco da nossa infraestrutura e nunca de cliente vindo da Internet; e por isso que a sonda do contentor continua passando |
| NAT de operadora, `100.64.0.0/10` | **nao** entra na classe de infraestrutura: e endereco de cliente de verdade que nao e publico, e cai na regra declarada |
| Configuracao incompleta | derruba a subida, em vez de ligar meio mecanismo |
| Lista de proxies confiaveis vazia | o cabecalho encaminhado e ignorado por completo, nos dois sentidos: nao promove nem rebaixa pais |
| Posicao do controle | decisao de admissao, antes de existir identidade, e nao decisao de autorizacao |
| Desfecho da recusa | 403, que e linha nova na tabela de desfechos de `consumir-a-api.md`, e so e escrita no round que ligar o mecanismo |

O teste que importa mais, quando houver codigo, e o do cabecalho forjado: requisicao com
`X-Forwarded-For` vindo de endereco que nao esta na lista de proxies confiaveis **nao muda o
pais avaliado**, nos dois sentidos. A mutacao obrigatoria e esvaziar a lista de proxies
confiaveis e ver teste vermelho.

## As perguntas que o Lactec precisa responder, e o que muda com cada uma

1. **Qual recurso fica na frente deste servico?** Define se existe lista de proxies confiaveis
   e qual e ela. Com Front Door, a lista e um conjunto de faixas que muda com frequencia,
   entao ela precisa de processo de atualizacao e nao de valor fixo no ambiente. Sem nada na
   frente, o endereco do soquete passa a ser o do cliente e a restricao passa a valer de fato
   na aplicacao.
2. **Quais cabecalhos chegam na origem hoje?** Medicao, nao opiniao: uma requisicao de teste e
   o log do que chegou. Se chegar o cabecalho de endereco de soquete que a borda publica, a
   resolucao de origem passa a ler esse cabecalho, que o cliente nao escreve, em vez de andar
   na cadeia do `X-Forwarded-For`.
3. **A borda apaga `X-Forwarded-For` e `Forwarded` recebidos de fora?** Se nao apagar, a cadeia
   pode chegar pre fabricada pelo cliente, e a unica leitura segura passa a ser contar saltos
   confiaveis, nunca aceitar a esquerda da lista.
4. **A origem esta trancada?** Se a porta continuar alcancavel direto, regra de pais na borda
   vale zero. Esta pergunta muda o lugar onde o requisito 8 e implementado, nao o codigo.
5. **Existe firewall de aplicacao, e em que camada?** Se existir, a regra de pais vira regra
   dele, avaliando o endereco de soquete, e o mecanismo desta aplicacao fica desligado para
   sempre, como segunda linha documentada.
6. **De onde `/health` e sondado?** A sonda do contentor e laco local e ja passa. Monitoramento
   externo fora do pais, com a restricao ligada, tomaria recusa, e a implementacao ganharia
   uma lista de isentos que hoje nao existe de proposito.
7. **Funcionario em viagem: existe lista de excecao, com dono e prazo?** Se existir, ela e
   tabela com data de validade, e nao propriedade de ambiente.
8. **Quem e o dono do processo mensal da base de geolocalizacao, e qual base?** Define licenca,
   atribuicao obrigatoria, tamanho do arquivo e o gerador. Sem dono, a base envelhece e a
   restricao passa a recusar brasileiro.
9. **Aceitam por escrito que a recusa por regiao e um desfecho novo no contrato?** O consumidor
   atual distingue 401 com e sem desafio; 403 por origem e uma linha nova, e o Portal precisa
   saber le la.

## Em que estado o repositorio fica

Nenhum codigo de restricao por regiao existe neste modulo, e isso e a decisao, nao uma
pendencia esquecida. O `CLAUDE.md` descreve a regra e as tres restricoes duras; este documento
registra a analise, o que foi descartado e por que, as decisoes ja tomadas e as nove perguntas
que destravam a implementacao. Quem for ligar o mecanismo comeca por este documento, nao do
zero.
