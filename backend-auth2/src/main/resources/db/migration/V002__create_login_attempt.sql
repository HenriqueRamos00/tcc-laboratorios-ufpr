-- Contador de tentativa de login, que sustenta a regra das cinco tentativas.
--
-- A chave e o digest de aplicacao mais identificador apresentado, e nao a conta. Tres
-- razoes, e todas mudam comportamento observavel:
--
-- 1. Identificador que nao existe precisa ser contado igual ao que existe. Se so conta
--    real bloqueasse, o 429 passaria a significar que o e-mail existe, e a matriz de
--    codigos de resposta perderia o sentido.
-- 2. Sem vinculo com a conta, apagar conta nao zera contador e o expurgo de auditoria nao
--    desbloqueia ninguem por efeito colateral.
-- 3. O identificador apresentado nao fica legivel no banco, entao a tabela de controle nao
--    vira lista de quem tentou entrar.
--
-- Nao existe aqui nenhum vinculo com CLIENT_ACCOUNT de proposito.
CREATE TABLE LOGIN_ATTEMPT (
  ATTEMPT_KEY RAW(32) NOT NULL,
  FAILED_ATTEMPTS NUMBER(3) DEFAULT 0 NOT NULL,
  LOCKED_UNTIL TIMESTAMP(6) WITH TIME ZONE,
  LAST_FAILURE_AT TIMESTAMP(6) WITH TIME ZONE,
  CONSTRAINT PK_LOGIN_ATTEMPT PRIMARY KEY (ATTEMPT_KEY),
  CONSTRAINT CK_LOGIN_ATTEMPT_COUNT CHECK (FAILED_ATTEMPTS >= 0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON LOGIN_ATTEMPT TO AUTH_READWRITE;
