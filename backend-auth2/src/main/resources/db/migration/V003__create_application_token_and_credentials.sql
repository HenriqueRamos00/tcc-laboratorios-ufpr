-- Token de aplicacao e as colunas de credencial que a fatia de autenticacao exige.
--
-- A base nasce vazia, entao acrescentar coluna obrigatoria aqui nao converte dado nenhum.
-- Elas nao entraram na primeira migracao porque nenhum caso de teste daquela fatia as
-- pedia, e codigo que a suite nao exige nao se escreve adiantado.

-- O token nao guarda o valor apresentado, so o digest. Nao existe identificador embutido
-- no token: o formato que o consumidor valida nao tem lugar para separador, entao a linha
-- e encontrada pelo proprio digest, que por isso e unico.
CREATE TABLE APPLICATION_TOKEN (
  ID NUMBER(19) NOT NULL,
  APPLICATION_ID NUMBER(19) NOT NULL,
  NAME VARCHAR2(150 CHAR) NOT NULL,
  TOKEN_DIGEST RAW(32) NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  EXPIRES_AT TIMESTAMP(6) WITH TIME ZONE,
  -- Revogacao preenche a coluna e a linha fica: a auditoria exige saber quem revogou e
  -- quando, e linha apagada nao conta essa historia.
  REVOKED_AT TIMESTAMP(6) WITH TIME ZONE,
  CONSTRAINT PK_APPLICATION_TOKEN PRIMARY KEY (ID),
  CONSTRAINT UK_APPLICATION_TOKEN_DIGEST UNIQUE (TOKEN_DIGEST),
  CONSTRAINT FK_APPLICATION_TOKEN_APPLICATION FOREIGN KEY (APPLICATION_ID)
    REFERENCES APPLICATION (ID)
);

CREATE INDEX IX_APPLICATION_TOKEN_APPLICATION ON APPLICATION_TOKEN (APPLICATION_ID);

-- A forma guardada da senha carrega algoritmo, custo e sal dentro dela, entao nao existe
-- coluna de sal nem de parametro. O tamanho e folgado de proposito: trocar de algoritmo
-- depois nao pode exigir migracao de coluna.
ALTER TABLE CLIENT_ACCOUNT ADD (
  PASSWORD_HASH VARCHAR2(255 CHAR) NOT NULL,
  -- Confirmacao pertence a conta, e conta e por aplicacao: a mesma pessoa cadastrada em
  -- duas aplicacoes confirma duas vezes.
  EMAIL_CONFIRMED NUMBER(1) DEFAULT 0 NOT NULL,
  ACTIVE NUMBER(1) DEFAULT 1 NOT NULL,
  CONSTRAINT CK_CLIENT_ACCOUNT_CONFIRMED CHECK (EMAIL_CONFIRMED IN (0, 1)),
  CONSTRAINT CK_CLIENT_ACCOUNT_ACTIVE CHECK (ACTIVE IN (0, 1))
);

ALTER TABLE ADMINISTRATOR ADD (PASSWORD_HASH VARCHAR2(255 CHAR) NOT NULL);

GRANT SELECT, INSERT, UPDATE, DELETE ON APPLICATION_TOKEN TO AUTH_READWRITE;
