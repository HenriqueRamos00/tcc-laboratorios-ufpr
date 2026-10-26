# Round 1: levantamento de boas praticas

Oito agentes independentes, somente leitura, um tema cada, proibidos de ler `backend-auth/`.
Este arquivo e o resumo que o integrador consolidou, mais o que ele mediu depois.

## 1. Fatos medidos pelo integrador

Nenhum destes e opiniao de agente. Cada um foi executado.

| Fato | Como foi medido | Consequencia |
| --- | --- | --- |
| Oracle **recusa** duplicata em indice unico composto com coluna nula | `INSERT` repetido em Oracle 23ai: `ORA-00001` com `(APP_ID:NULL, EMAIL:'a@x.com') already exists` | A linha so sai do indice quando **todas** as colunas indexadas sao nulas. Cai o argumento de que administrador precisaria de tabela propria por causa disso |
| `VARCHAR2(n)` cobra **byte**, nao caractere | `NLS_LENGTH_SEMANTICS = BYTE`, `NLS_CHARACTERSET = AL32UTF8`; `LENGTH('ção') = 3`, `LENGTHB('ção') = 5`; `VARCHAR2(5)` recusou `'coração'` (9 bytes) | Todo texto nasce `VARCHAR2(n CHAR)`, senao `name VARCHAR2(150)` recusa nome acentuado de 150 caracteres |
| Tag exata de imagem **nao** e imutavel | `21.0.12.1_1-jre-alpine` e `21.0.12.1_1-jre-alpine-3.24` respondem hoje o mesmo `sha256:b48e02e0ea88...` no registro | Base fixada por digest, com a tag ao lado para humano ler |
| Boot 4.1.1 tem log estruturado nativo | Pacote `org/springframework/boot/logging/structured/` e a propriedade `logging.structured.format.console` dentro do `spring-boot-4.1.1.jar` | `logstash-logback-encoder` saiu do POM: era dependencia nao gerenciada para entregar o que a plataforma entrega |
| `hypersistence-utils` **tem** licenca | POM pai `3.16.0` declara Apache-2.0, e `<licenses>` se herda para o POM efetivo | A afirmacao anterior do integrador estava errada e foi corrigida. O motivo real de ficar fora e que o modulo mais novo e o `-73` (Hibernate 7.3) e o Boot 4.1.1 traz 7.4.5; `-74` nao existe (404) |
| O cliente .NET decide pelo cabecalho | `Portal.Adapters/BackendAuth/BackendAuthAdapter.cs`: `401` sem `WWW-Authenticate` e credencial errada; qualquer outro status vira `AuthenticationSourceUnavailableException`; corpo so e lido em `200` | O `AuthenticationEntryPoint` padrao do Spring nao pode valer na rota de login |
| Versoes que de fato resolvem | `dependency:list` no contentor | Hibernate **7.4.5.Final**, Flyway **12.4.0** (nao 13.x), Testcontainers **2.0.5**, Spring Security **7.1.1** |
| BCrypt do Spring Security 7.1.1 **nao** trunca em silencio | `encoder.encode()` com senha de 96 bytes lancou `IllegalArgumentException: password cannot be more than 72 bytes` | Cai a premissa de dois votos. Com BCrypt o risco nao e prefixo colidindo, e sim excecao nao tratada virando 500 no cadastro |
| `bucket4j_jdk17-core` esta vivo | `maven-metadata.xml`: release 8.21.0, `lastUpdated` 2026-10-02 | Contraria o voto que afirmou congelamento em 8.14.0. O parado e o `bucket4j-core` classico, de 2024 |
| Testcontainers 2.x renomeou artefato e pacote | Build quebrou; BOM lista `testcontainers-oracle-free` | Classe e `org.testcontainers.oracle.OracleContainer`, e o modulo usa `gvenzl/oracle-free:slim` por padrao, nao a imagem de 14 GB |

## 2. Recomendacao de cada agente, em uma linha

1. **Token**: token opaco `<tokenId>.<verifier>`, 32 bytes de `SecureRandom`, guardando so o SHA-256 do verificador; `AuthenticationFilter` mais `AuthenticationConverter` e um `AuthenticationProvider` por tipo; sem `oauth2-resource-server`; hash rapido no token e lento so na senha.
2. **Senha**: Argon2id como algoritmo de gravacao e BCrypt apenas como leitor de hash herdado, num `DelegatingPasswordEncoder` com mapa explicito; politica (8+, maiuscula, minuscula, numero) em `PasswordPolicy` na camada de servico, contando ponto de codigo; migracao de hash no login bem sucedido.
3. **Bloqueio**: contador em tabela propria, mutado por **um UPDATE condicional atomico** cujo numero de linhas afetadas e a decisao; relogio do banco; limitacao por origem separada da de conta; Resilience4j para tempo limite e disjuntor.
4. **Multi-aplicacao**: coluna discriminadora `application_id`, escopo derivado do token, repositorio sem metodo que aceite consulta sem escopo, `@Filter` do Hibernate apenas como rede de protecao, cruzamento negado por chave estrangeira composta.
5. **Log**: formato nativo `ecs`, MDC limpo no `finally`, correlacao aceitando `traceparent`; trilha de auditoria em tabela Oracle somente insercao, gravada em transacao propria (`REQUIRES_NEW`), vocabulario de evento do OWASP.
6. **Banco**: TSID como `Long` em `NUMBER(19)`, atribuido por gerador do Hibernate e nao no construtor; migracao Flyway sem aspas e com restricao nomeada; usuario dono do esquema separado do usuario da aplicacao; `VARCHAR2(n CHAR)`.
7. **Estrutura**: pacote por funcionalidade no topo com camadas dentro, mais `shared/`; entidade e repositorio package private; corpo de erro unico por `ProblemDetail` com `message` como extensao; duas portas de erro, porque `@RestControllerAdvice` nao cobre 401 e 403.
8. **Contentor**: fixar por digest, limites de recurso, segredo por arquivo, `forward-headers-strategy` com lista de proxies; bloqueio por regiao nao resolve na origem atras de proxy da Azure, e so faz sentido na WAF, em `SocketAddr`, com a origem trancada.

## 3. Decisoes em disputa

Sao estas que a votacao resolve. As opcoes estao escritas como foram defendidas.

### D1. Algoritmo de hash de senha
- **A. Argon2id** gravando e BCrypt so lendo. Elimina o limite de 72 bytes, entao o teto do contrato volta a ser contavel em caractere. Custa `bcprov-jdk18on` (licenca propria declarada, cerca de 8 MiB) e cerca de 19 MiB de heap por verificacao concorrente, o que exige limite de memoria e de concorrencia antes de entrar.
- **B. BCrypt** com forca medida. Sem dependencia nova, mas o teto de 72 passa a ser cobrado em **bytes** no servico, e senha com acento recusa antes do que o usuario espera.

### D2. Eixo da estrutura de pastas
- **A. Pacote por funcionalidade** no topo, camadas dentro. Permite entidade e repositorio package private, e o compilador recusa o acesso indevido.
- **B. Camada no topo** (`web/`, `usecase/`, `persistence/`). Mais legivel para quem chega, mas obriga tudo a ser publico e a regra vira convencao cobrada por revisor.

### D3. Unicidade de e-mail
- **A. Composta por aplicacao** (`UNIQUE (application_id, email)`). O mesmo e-mail pode existir em duas aplicacoes, e unicidade global vazaria existencia de conta entre aplicacoes.
- **B. Global**. Uma pessoa, uma conta, login mais simples. Contraria o isolamento por aplicacao.
- Nota medida: a justificativa de separar administrador por causa de nulo em indice composto **caiu**, porque o Oracle recusa a duplicata.

### D4. Banco nos testes
- **A. `gvenzl/oracle-free:slim-faststart`**, que e o padrao do modulo do Testcontainers, numa unica suite de integracao com contentor reaproveitado.
- **B. Imagem oficial `container-registry.oracle.com/database/free`**, a mesma de producao, pagando minutos por execucao.
- **C. Banco em memoria** para a maioria dos testes. O brief 6 argumenta que isso nao prova migracao, restricao nem tipo.

### D5. Dois usuarios no banco
- **A. Sim**: `AUTH_OWNER` roda a migracao, `AUTH_APP` atende requisicao sem DDL. Exige script de inicializacao no compose, mais uma variavel no `.env` e `default_schema` declarado.
- **B. Nao**: um usuario so, mais simples, e a protecao fica na camada de dados.

### D6. Onde o TSID e atribuido
- **A. Gerador do Hibernate** por `@IdGeneratorType`. Evita que `save()` caia em `merge()` e emita um `SELECT` antes de cada `INSERT`.
- **B. Construtor da entidade**, que entao exige implementar `Persistable` com flag de transiente.

### D7. Resposta da quinta tentativa
- **A. `429` com `Retry-After`** ja na quinta, porque nesse instante a conta esta bloqueada.
- **B. `401` na quinta** e `429` so a partir da sexta.
- Restricao medida: o cliente .NET le `Retry-After` em `429` e trata `401` sem `WWW-Authenticate` como senha errada.

### D8. Tracing e limitacao de requisicao
- **A. Sem Micrometer Tracing** agora (nao ha coletor), com filtro proprio de correlacao; limitacao por origem com Bucket4j (`bucket4j_jdk17-core`, Apache-2.0, viva) ou filtro proprio com mapa de tamanho maximo.
- **B. Com starter de tracing** desde ja.
- Nota medida: `bucket4j-core` classico esta parado desde 2024; o artefato vivo e `bucket4j_jdk17-core`.
