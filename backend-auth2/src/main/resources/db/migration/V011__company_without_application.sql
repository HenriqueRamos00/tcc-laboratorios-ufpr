-- Desmonta o vinculo unico entre conta e empresa e tira a empresa de dentro da aplicacao.
--
-- A empresa passou a ser cadastrada pelo administrador, que atravessa aplicacoes, e nao
-- mais pela credencial de uma aplicacao. Com isso o dono dela deixa de existir: a mesma
-- pessoa juridica e uma empresa so, e contas de aplicacoes diferentes podem representa-la.
--
-- A ordem importa. A chave estrangeira composta da conta depende da restricao unica
-- (APPLICATION_ID, ID) da empresa, entao ela cai primeiro. A unicidade nova do CNPJ vem
-- por ultimo, depois de a V010 ter juntado os CNPJ repetidos entre aplicacoes.
ALTER TABLE CLIENT_ACCOUNT DROP CONSTRAINT FK_CLIENT_ACCOUNT_COMPANY;

DROP INDEX IX_CLIENT_ACCOUNT_COMPANY;

ALTER TABLE CLIENT_ACCOUNT DROP COLUMN COMPANY_ID;

ALTER TABLE COMPANY DROP CONSTRAINT UK_COMPANY_CNPJ;

ALTER TABLE COMPANY DROP CONSTRAINT UK_COMPANY_APPLICATION_ID;

ALTER TABLE COMPANY DROP CONSTRAINT FK_COMPANY_APPLICATION;

ALTER TABLE COMPANY DROP COLUMN APPLICATION_ID;

ALTER TABLE COMPANY ADD CONSTRAINT UK_COMPANY_CNPJ UNIQUE (CNPJ);
