# Round 4: a revisao por tema, e o que ela mudou

Cinco frentes de revisao somente leitura, uma por tema que nenhuma revisao anterior tinha
coberto, mais duas que ja tinham relatorio pela metade. Nenhum agente escreveu no
repositorio: eles leram, mediram e entregaram achado com evidencia. **Toda afirmacao de agente
foi medida pelo integrador antes de virar mudanca**, e as que nao se sustentaram estao
registradas como nao sustentadas.

| Frente | Modelo | Achados aceitos | Achados recusados ou reduzidos |
| --- | --- | --- | --- |
| Esquema, restricao e concorrencia | Opus | 6 de 11 | paginacao e expurgo viraram divida declarada |
| Migracao e privilegio de banco | Opus | 6 de 9 | concessao em migracao repetivel ficou de fora |
| Contentor e execucao | Fable | 5 de 7 | segredo em variavel de ambiente ficou declarado |
| Cadeia de suprimentos | Fable | 5 de 7 | springdoc em perfil ficou de fora |
| Furos na suite de teste | Opus | 11 de 12 | limpeza da base comum ficou declarada |

## Os defeitos que a revisao achou e que estavam em producao

### O contador de tentativa nunca recomecava

Vencida a espera, a primeira falha levava a contagem de cinco para seis, o limite aparecia
estourado outra vez e a conta trancava pelo periodo inteiro. A regra 3 do Lactec diz cinco
tentativas e espera configuravel, nao cinco tentativas na vida.

Medido no vermelho, antes da correcao: `a primeira falha depois da espera nao tranca,
esperando false mas era true`. Para quem nao sabe a senha, isso transformava **uma**
requisicao por periodo, em vez de cinco, na alavanca para manter conta alheia fechada; para o
titular que errasse a digitacao uma vez, era ciclo sem saida de quinze em quinze minutos.
Efeito de borda fechado junto: o contador deixou de crescer sem teto e nao caminha mais para
o limite de `NUMBER(3)`, que estouraria como erro de servidor na rota de login.

### O teto de corpo nao valia para envio em pedacos

O filtro de teto olhava somente o tamanho declarado, e o comentario dele afirmava que o caso
em pedacos era coberto pelo leitor de JSON. A afirmacao estava errada, e foi medida contra a
aplicacao no ar: corpos de 200 KB e de **8 MB** com `Transfer-Encoding: chunked` entraram
inteiros, foram interpretados e responderam 400 de validacao, enquanto o mesmo corpo com
`Content-Length` respondia 413. Bastava omitir um cabecalho para o teto deixar de existir, na
rota aberta de entrada administrativa.

A correcao conta os bytes durante a leitura. Depois dela, medido na mesma aplicacao: 413 nos
dois caminhos, nos dois tamanhos, e corpo normal continua respondendo 401.

### Banco fora de alcance respondia 500, e a sonda dizia que estava tudo bem

Medido com o contentor do banco pausado: `/health/readiness` respondia **200 UP** enquanto
toda rota de negocio respondia **500** no formato padrao do framework, com o caminho pedido
ecoado no corpo e uma pilha inteira em nivel de erro por requisicao. A raiz de `/health`, que
agrega o banco, dizia a verdade com 503.

Duas correcoes. A prontidao passou a incluir o banco, porque e ela que o healthcheck do
contentor consulta. E o caso ganhou tratador proprio: a excecao de banco inacessivel nao e a
de pool cheio, que ja respondia 503. Medido depois: prontidao 503, rota de negocio 503 com
`Retry-After: 2` em `application/problem+json`, e tudo volta a 200 quando o banco volta.

### CNPJ repetido na alteracao de empresa respondia 500

Uma requisicao sequencial bastava. Medido no vermelho: `ORA-00001: unique constraint
UK_COMPANY_CNPJ violated` escapando sem traducao. A matriz manda 409, e o consumidor le
qualquer status fora dela como fonte indisponivel, entao corrigir um CNPJ digitado errado
aparecia ao operador como servico fora do ar. O cadastro ja respondia 409, mas pela consulta
previa no controlador, fora da transacao da gravacao: a decisao desceu para o caso de uso e a
restricao do banco passou a ser o juiz, com corrida coberta por teste.

### A senha dos dois usuarios de banco vencia em 2027-04-23

Medido em `DBA_USERS`: os dois usuarios nasceram no perfil `DEFAULT`, que vence a senha em 180
dias com 7 de carencia. No dia do vencimento o pool deixaria de abrir conexao com `ORA-28001`
e o unico servico de autenticacao pararia por inteiro, sem que reiniciar o contentor
resolvesse nada, porque o script de inicializacao descartava "usuario ja existe" e seguia
adiante.

O script ganhou um perfil de servico e, mais importante, passou a **convergir**: ele reaplica
senha e perfil a cada subida. Medido depois de aplicar: os dois usuarios estao em
`AUTH_SERVICE` e a expiracao e `nunca`. O freio de tentativa de conexao ficou, com espera de
um minuto em vez de um dia, porque dez recusas em laco de reinicio nao podem custar um dia de
servico fora do ar.

### Dois pedidos simultaneos de recuperacao deixavam dois links vivos

A regra diz que o pedido novo mata o anterior, e o codigo cumpria isso invalidando antes de
inserir. Ordem de comando nao e invariante. Medido no vermelho, com quatro pedidos simultaneos
para a mesma conta: **quatro** links de recuperacao validos ao mesmo tempo. E medido no banco
de desenvolvimento, que viveu com o defeito: uma conta tinha **dez** links pendentes.

O invariante foi para o banco, num indice unico parcial, e perder a corrida deixou de ser
falha: significa que outro pedido identico ja emitiu o link.

### `@Transactional` sobre um `record` e sobre um `enum`

Em `ApplicationRegistry` a anotacao tinha caido sobre os dois tipos aninhados, e nao sobre os
metodos abaixo deles. Tipo aninhado que nao e bean nao e proxiado. Medido no vermelho, e e
esta a prova de que nao era so estetica: `getTransactionAttribute` de `register` devolvia
**nulo**, ou seja, o metodo rodava sem transacao nenhuma. Nada quebrava hoje porque nenhum dos
dois faz duas escritas; o defeito era o limite declarado nao existir, e a segunda escrita que
alguem acrescentasse deixar de ser atomica em silencio.

A guarda estrutural que entrou recusa `@Transactional` em qualquer tipo aninhado de bean do
modulo, e cobra atributo transacional resolvido nos quatro metodos de cadastro com desfecho.

### A restricao de formato do codigo mudava de veredito com a sessao

`[a-z0-9]` e expressao de intervalo, e em Oracle o significado de intervalo sai do `NLS_SORT`,
que e parametro de sessao. Medido na mesma linha de dados: com `BINARY` o codigo com cedilha e
recusado, com `XSPANISH` e com `GERMAN` ele passa. O estrago nao ficaria no banco, porque o
Portal valida o mesmo formato na inicializacao dele, entao um codigo gravado com acento
impediria o Portal de **subir**.

Duas defesas, porque sao dois caminhos: a sessao da aplicacao fixa `NLS_SORT = BINARY`, e a
restricao foi reescrita com `TRANSLATE`, que nao pergunta nada ao NLS. Medido antes de aplicar:
os 17 codigos ja cadastrados passam, e hifen dobrado, hifen no fim, maiuscula, acento e nova
linha sao recusados.

### A credencial do dono do esquema vivia no ambiente da api

E o achado A7, que estava aberto desde o round anterior. A correcao proposta exigia uma medicao
que faltava: o jar serve de migrador de disparo unico? Medido que **nao**, na primeira
tentativa: sem contexto web a cadeia de seguranca nao encontra `HttpSecurity` e o processo
encerra falhando. Com a cadeia condicionada a aplicacao web, medido que **sim**: ele valida as
oito migracoes, aplica o que falta, fecha o pool e encerra sozinho, com codigo zero.

O compose ganhou um servico `migrate` de disparo unico, sem reinicio, e a api perdeu a
credencial do dono. Medido depois, dentro do contentor: o processo da api so tem
`SPRING_FLYWAY_ENABLED` e nenhuma variavel de credencial de migracao. Medido tambem o caminho
infeliz: migrador com senha errada encerra com codigo 1, e nesse caso o compose nao cria a api.

Desligar o Flyway na api faz parte da correcao, e nao e enfeite: sem credencial propria ele
cairia no `DataSource` da aplicacao, cujo usuario nao le a tabela de historico e so tem
`CREATE SESSION`. Trocar um achado de seguranca por indisponibilidade total nao seria correcao.

### O healthcheck do banco ficava verde antes de os usuarios existirem

A sonda da imagem olha o estado do banco, e o `runOracle.sh` roda os scripts de inicializacao
**depois** de ela responder zero. Nesta maquina a janela foi de 850 ms com volume pronto, e num
volume novo ela e muito maior. Pior: o `runUserScripts.sh` carrega o script com ponto e ninguem
confere o status dele, entao script que falha deixaria o contentor sadio com o banco sem
usuario nenhum. A sonda passou a exigir um marcador que so existe quando o script terminou.

Isso era pre-requisito do servico `migrate`: com disparo unico, migracao que falha por banco
nao provisionado aborta a api em vez de ser tentada outra vez.

## O que a suite nao pegava, e agora pega

Onze furos corrigidos. Quatro mereceram mutacao, e todas morreram:

| Mutacao aplicada | Quem a matou |
| --- | --- |
| condicao de vencimento removida do consumo do link | os dois casos novos de link vencido |
| `@Size(min = 8)` acrescentado ao pedido de login | o caso de senha curta respondendo 401 |
| linha que zera o contador apagada do caso de uso | o caso de acerto zerando a contagem |
| guarda de segredo em branco removida da instalacao | a classe nova que sobe sem segredo |
| concessao de uma tabela apagada de uma migracao | a guarda de deriva de privilegio, que nomeia a tabela |

Os quatro furos que mais importavam:

- **Administrador desativado entrava de novo.** A consulta por e-mail seleciona a situacao mas
  nao filtra por ela, entao a unica avaliacao vive numa condicao do caso de uso, e nenhum caso
  fazia login de administrador desativado. Apagar aquela condicao deixava a suite verde com o
  afastado recebendo sessao nova de oito horas.
- **Um caso passava pelo motivo errado.** O corpo que deveria exercitar a ausencia da situacao
  mandava tres campos que o pedido nao declara, e a desserializacao recusava antes da
  validacao: o 400 vinha de corpo ilegivel. E o mesmo formato de defeito que ja havia sido
  corrigido uma vez na mesma classe.
- **Um caso nao podia falhar.** A afirmacao de que bloquear administrador nao bloqueia cliente
  usava um identificador de aplicacao inventado dentro do teste e nunca apresentado a producao,
  entao a contagem era zero por construcao. Pior, o auxiliar reimplementava o algoritmo da
  chave, e trocar o separador na producao deixava tudo verde porque a copia mudava junto. O caso
  foi reescrito para entrar pela rota: o cliente entra de verdade depois de o administrador
  estar bloqueado.
- **Uma assercao era satisfeita por residuo.** A listagem do administrador afirmava
  `tamanho >= 2`, e a base comum nao limpa estado: quando aquele caso roda, a tabela ja carrega
  contas de seis outras classes. Agora ela cobra os dois identificadores que o proprio caso
  criou.

Seis assercoes que aceitavam dois status viraram valor exato, porque a diferenca entre 401 e
403 nao e indiferente neste modulo: o 401 da cadeia leva `WWW-Authenticate` e o consumidor le
isso como fonte indisponivel.

## O que a cadeia de suprimentos rendeu

A arvore resolvida tem 165 artefatos, e todos foram consultados. O parent subiu de 4.1.1 para
4.1.2, e com ele Tomcat de 11.0.24 para 11.0.27 e Jackson de 3.1.5 para 3.1.7. **Nenhum dos
avisos e exploravel neste desenho**, e isso esta dito para o relatorio nao parecer mais grave
do que e: a autorizacao e do Spring Security e nao do contentor, nao ha HTTP/2 nem AJP nem
formulario do contentor, e nao existe campo decimal em corpo de entrada nenhum. A atualizacao
e barata justamente por isso.

A distribuicao do Maven passou a ter soma de verificacao, conferida aqui contra o `sha512`
publicado pela Apache antes de ser fixada. O jar ganhou carimbo de tempo fixo, porque duas
construcoes seguidas da mesma arvore davam resumos diferentes. O BouncyCastle passou para
escopo de execucao, e duas dependencias de teste sem nenhum import sairam.

## O que nao foi feito, e por que

- **Limite de requisicao por token ou conta.** Continua sendo a maior divida, e continua
  menor que antes: o teto de concorrencia tira o pior caso. O que falta e impedir o
  desperdicio.
- **Expurgo das tabelas de controle.** Medido: 1219 linhas de `LOGIN_ATTEMPT` contra 21 contas,
  1208 delas com uma unica falha registrada, e nenhum comando no modulo as remove. O
  crescimento e guiado por quem chama, e e o unico recurso sem teto declarado. Entra como
  fatia propria, com indice de expurgo e relogio injetavel.
- **Paginacao das rotas de listagem.** `GET /api/users` devolve toda conta da aplicacao num
  corpo so. A correcao muda o contrato publico, e contrato se combina com o integrador antes.
- **`ACTIVE` modelado de duas formas.** `BOOLEAN` nativo em duas tabelas, `NUMBER(1)` com
  `CHECK` em outra, sem motivo escrito em nenhum round. A unificacao e migracao de tipo em
  tabela populada, e o caminho de conversao nao foi medido: fica declarado em vez de arriscado.
- **Limpeza de estado na base comum da suite.** O isolamento de hoje e acidente de
  configuracao, nao desenho: a execucao e sequencial por padrao, e ligar paralelismo tornaria a
  suite nao deterministica. Dois vazamentos concretos foram fechados, o nivel do logger ROOT
  que nao era restaurado e dois identificadores derivados de relogio e de resumo. A limpeza da
  base inteira toca 41 classes e fica como fatia propria.
- **Verificacao de versao aplicada na subida da api.** Com a migracao fora da api,
  `depends_on` nao vale para reinicio feito pelo Engine: num reboot a api volta sem o migrador
  ter rodado. O `validate` do Hibernate cobre tabela e coluna que faltam, nao restricao nem
  indice. A defesa completa e uma fatia propria.
- **`docs/openapi.json` nao foi regerado.** O desfecho 409 da alteracao de empresa e novo no
  contrato, e o procedimento de regeracao exige entrar com credencial administrativa, que o
  integrador tem e quem fez esta rodada nao. O procedimento esta no `README.md`.
- **Concessoes em migracao repetivel.** A proposta tira todo `GRANT` dos cinco scripts
  versionados, e isso muda a soma de verificacao deles: em banco ja migrado a subida cai por
  falha de validacao. O risco nao se paga agora, e a guarda de deriva de privilegio que entrou
  cobre o defeito que a proposta queria evitar.

## Numeros medidos nesta rodada

- **272 testes**, construcao verde, contra Oracle de verdade.
- **Rajada de 400 logins administrativos, 200 em paralelo**: 400 respostas 401, zero erro no
  log, zero reinicio, prontidao 200 depois, 23,7 s no total.
- **Corpo em pedacos**: 413 em 200 KB e em 8 MB, onde antes era 400 depois de ler tudo.
- **Banco pausado**: prontidao 503, rota de negocio 503 com `Retry-After`, raiz 503.
- **Migrador de disparo unico**: encerra com codigo 0 em 11,5 s no caminho feliz, e com codigo
  1 com credencial errada.
- **Expiracao de senha dos usuarios de banco**: era 2027-04-23, passou a `nunca`.
- **Cinco mutacoes aplicadas e mortas**, uma por correcao de furo que precisava de prova.

## Uma licao de processo que vale escrever

A migracao que cria o indice unico parcial **passou na suite e falhou no banco de
desenvolvimento**. A suite roda contra banco vazio, e banco sem dado nao exercita migracao de
dado: no banco real havia dez links pendentes para a mesma conta, exatamente o acervo que o
defeito produziu.

A primeira tentativa de correcao juntou limpeza e indice no mesmo arquivo, e ai apareceu a
segunda licao: quando o indice falhou, o Flyway desfez a limpeza junto, e a subida seguinte
encontrava o mesmo acervo e falhava igual. Com a aplicacao reiniciando em laco, uma das
tentativas acabou passando, o que deixou o banco num estado que nenhum arquivo do repositorio
descrevia. Migracao de dado e migracao de esquema confirmam em momentos diferentes, e por isso
sao dois arquivos, e o indice tolera ja existir.
