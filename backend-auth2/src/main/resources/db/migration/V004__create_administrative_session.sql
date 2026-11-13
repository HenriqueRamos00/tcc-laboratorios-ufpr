-- Sessao administrativa: o token que o administrador recebe ao entrar.
--
-- Tabela propria, e nao a de token de aplicacao, porque os dois tem ciclo de vida e
-- alcance diferentes. O token de aplicacao e credencial de maquina, de vida longa e
-- revogavel item a item; este e de pessoa, nasce no login e vence sozinho.
--
-- Guarda o digest, nunca o valor apresentado, pela mesma razao do outro.
CREATE TABLE ADMINISTRATIVE_SESSION (
  ID NUMBER(19) NOT NULL,
  ADMINISTRATOR_ID NUMBER(19) NOT NULL,
  TOKEN_DIGEST RAW(32) NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  EXPIRES_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  REVOKED_AT TIMESTAMP(6) WITH TIME ZONE,
  CONSTRAINT PK_ADMINISTRATIVE_SESSION PRIMARY KEY (ID),
  CONSTRAINT UK_ADMINISTRATIVE_SESSION_DIGEST UNIQUE (TOKEN_DIGEST),
  CONSTRAINT FK_ADMINISTRATIVE_SESSION_ADMIN FOREIGN KEY (ADMINISTRATOR_ID)
    REFERENCES ADMINISTRATOR (ID)
);

CREATE INDEX IX_ADMINISTRATIVE_SESSION_ADMIN ON ADMINISTRATIVE_SESSION (ADMINISTRATOR_ID);

GRANT SELECT, INSERT, UPDATE, DELETE ON ADMINISTRATIVE_SESSION TO AUTH_READWRITE;
