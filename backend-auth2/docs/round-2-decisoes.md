# Round 2: apuracao e decisoes fechadas

Oito cedulas, lentes diferentes: seguranca ofensiva, manutencao, testabilidade, carga,
banca, operacao no cliente, contrato e simplicidade. O criterio de desempate declarado era
fato medido acima de maioria. Onde a maioria se apoiou em premissa falsa, a premissa caiu
e o voto foi reavaliado.

| Decisao | Votos | Fechado | Por que |
| --- | --- | --- | --- |
| D1 hash de senha | A 6, B 2 | **Argon2id**, perfil `m=7168, t=5, p=1` | Ver abaixo: a premissa mais citada caiu, e o que sustenta a decisao e outra coisa |
| D2 estrutura | A 8 | **Pacote por funcionalidade**, camadas dentro | Unica opcao em que o compilador cobra a regra, com entidade e repositorio package private |
| D3 unicidade de e-mail | A 8 | **Composta por aplicacao**, sobre a forma normalizada | Requisito 5 do Lactec; unicidade global vira oraculo de existencia entre aplicacoes |
| D4 banco nos testes | A 7 | **Testcontainers**, `gvenzl/oracle-free:slim-faststart` **escrito no codigo** | So Oracle de verdade prova migracao, `VARCHAR2(n CHAR)` e restricao |
| D5 dois usuarios no banco | A 6, B 2 | **Dois usuarios**, e a suite de integracao roda como o restrito | Separacao que nao e exercitada em teste nao existe |
| D6 atribuicao do TSID | A 8 | **Gerador do Hibernate** por `@IdGeneratorType` | Evita `save()` virar `merge()` e emitir `SELECT` antes de cada `INSERT` |
| D7 quinta tentativa | A 5, B 3 | **429 na quinta falha**, com a correcao abaixo | Maioria, mais a emenda que fecha a objecao da lente ofensiva |
| D8 tracing e limite | A 8 | **Sem tracing**; limite em **filtro proprio** com armazem limitado | Sem coletor, tracing e instrumentacao que ninguem le |

## D1, com a premissa corrigida

Dois votos em Argon2id se apoiaram em "BCrypt trunca em silencio e duas senhas viram a
mesma credencial". **Medido e falso nesta versao:** `spring-security-crypto-7.1.1`
lancou `IllegalArgumentException: password cannot be more than 72 bytes` para senha de 96
bytes. Isso casa com a correcao de CVE-2025-22228, que passou a recusar entrada acima do
limite em vez de truncar.

A decisao continua em Argon2id, mas pelo motivo que sobreviveu, e ele e de contrato: o
Portal cobra senha em **72 caracteres** (`LoginRequest.cs:11`), e BCrypt cobra **72
bytes**. Entre os dois existe a faixa de 37 a 72 caracteres acentuados, que o Portal
aceita e que o BCrypt recusaria com excecao. Qualquer resposta nossa nessa faixa esta
errada: 400 mostra "confira os dados" para credencial correta, e 500 e pior. Com Argon2id
nao existe limite de byte e o teto estreito volta a ser o do proprio Portal.

**O preco e memoria, e a lente de carga fez a conta.** No minimo do OWASP (`m=19456`) cada
verificacao concorrente custa cerca de 19 MiB, e dentro de `mem_limit: 768m` cabem de 9 a
12 logins simultaneos antes do `OutOfMemoryError`, que com `ExitOnOutOfMemoryError` e
`restart: unless-stopped` vira laco de reinicio. Por isso o perfil escolhido e o de defesa
equivalente da OWASP, `m=7168, t=5, p=1`, que troca memoria por passagens mantendo o mesmo
produto, e por isso entra junto um **porteiro de concorrencia** no caminho do hash, com
recusa rapida em vez de fila. Os parametros sao declarados em configuracao, e o numero
final so e fixado depois de medido dentro do contentor.

Cai junto o mapa de algoritmo herdado: a base nasce vazia no Flyway V1, entao nao existe
hash antigo para ler. Fica um `DelegatingPasswordEncoder` com uma entrada so, pelo prefixo
`{argon2}`, que e o que permite trocar custo ou algoritmo depois sem migracao de dados.

## D7, com a emenda

A lente ofensiva recusou o 429 na quinta com um argumento correto: o ataque que funciona
nao e adivinhar senha, e trancar a conta alheia. A emenda que fecha isso e explicita, e
vale como requisito:

**As cinco tentativas sao todas avaliadas.** Senha certa na quinta **autentica** e zera o
contador. O 429 sai apenas na quinta **falha**, e a partir dela, sempre com `Retry-After`
em segundos inteiros maiores que zero, porque o consumidor le o delta e a forma de data
derrubaria a tela.

## D8, com o fato medido e a restricao do consumidor

A lente da banca afirmou que o `bucket4j_jdk17-core` esta congelado em 8.14.0. **Medido e
falso:** o `maven-metadata.xml` traz 8.21.0, publicado em 2026-10-02. O parado e o
`bucket4j-core` classico. A biblioteca fica de fora por outro motivo: processo unico, com
janela fixa por origem, cabe numa classe testavel com relogio injetavel, e dependencia
nova precisa dizer o que resolve que a plataforma nao resolve.

**Restricao medida que muda a chave do limite:** o Portal **nao** encaminha
`X-Forwarded-For` (`BackendAuthAdapter.cs:18-22` envia so `Authorization` e corpo). Logo,
limitar por endereco de origem as chamadas que vem do Portal estrangularia o Portal
inteiro em vez do atacante. A chave do limite e o **token de aplicacao ou a conta** quando
a chamada vem de uma aplicacao registrada, e o endereco de origem apenas onde ele e
conhecido e confiavel.

## Decisoes que a votacao expos e que eu fechei como integrador

- **Administrador em tabela propria.** O argumento original caiu com a medicao (o Oracle
  recusa a duplicata com coluna nula), entao a razao passa a ser outra: administrador nao
  pertence a aplicacao nenhuma, nao tem vinculo com empresa e tem politica de senha
  propria no contrato. Coluna anulavel para modelar "nao se aplica" e o cheiro que a banca
  pergunta.
- **A suite de integracao roda em `./mvnw test`.** Separar em perfil que ninguem executa e
  a mesma armadilha do sufixo `IT`, com outro nome. O custo e controlado pelo contentor
  unico reaproveitado pela suite inteira, com `@ServiceConnection`, e **nao** por
  `withReuse`, que a propria documentacao declara experimental.
- **`POST /api/setup/admin` fecha por estado, nao por segredo.** Ele so responde enquanto
  nao existir nenhum administrador. Depois do primeiro, responde como rota inexistente.
