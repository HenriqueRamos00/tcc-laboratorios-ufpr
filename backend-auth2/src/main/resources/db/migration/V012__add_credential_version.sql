-- Versao das credenciais incorporada aos JWTs de cliente para revogacao no servidor.
ALTER TABLE CLIENT_ACCOUNT ADD (
  CREDENTIAL_VERSION NUMBER(19) DEFAULT 0 NOT NULL
);
