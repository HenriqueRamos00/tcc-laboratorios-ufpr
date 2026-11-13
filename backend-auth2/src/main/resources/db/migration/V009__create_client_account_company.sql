-- Vinculo entre conta de cliente e empresa, agora de muitos para muitos.
--
-- Ate aqui a conta carregava uma unica empresa, na coluna COMPANY_ID, e nenhuma rota a
-- preenchia. O cadastro passou para o portal administrativo, e nele o administrador informa
-- a lista de empresas que a conta representa, entao o vinculo vira tabela propria.
--
-- A linha some junto com a conta, porque vinculo de conta que nao existe nao autoriza
-- ninguem. Do lado da empresa a chave estrangeira recusa a exclusao, e isso e o
-- comportamento desejado: apagar a empresa tiraria a autorizacao de quem a representa sem
-- ninguem ter pedido isso, entao quem exclui precisa antes desfazer os vinculos.
CREATE TABLE CLIENT_ACCOUNT_COMPANY (
  CLIENT_ACCOUNT_ID NUMBER(19) NOT NULL,
  COMPANY_ID NUMBER(19) NOT NULL,
  CONSTRAINT PK_CLIENT_ACCOUNT_COMPANY PRIMARY KEY (CLIENT_ACCOUNT_ID, COMPANY_ID),
  CONSTRAINT FK_ACCOUNT_COMPANY_ACCOUNT FOREIGN KEY (CLIENT_ACCOUNT_ID)
    REFERENCES CLIENT_ACCOUNT (ID) ON DELETE CASCADE,
  CONSTRAINT FK_ACCOUNT_COMPANY_COMPANY FOREIGN KEY (COMPANY_ID)
    REFERENCES COMPANY (ID)
);

CREATE INDEX IX_ACCOUNT_COMPANY_COMPANY ON CLIENT_ACCOUNT_COMPANY (COMPANY_ID);

GRANT SELECT, INSERT, UPDATE, DELETE ON CLIENT_ACCOUNT_COMPANY TO AUTH_READWRITE;
