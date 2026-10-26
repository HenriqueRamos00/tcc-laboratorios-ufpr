-- Esquema inicial do servico de autenticacao. A base nasce vazia: nao existe dado
-- anterior para converter.
--
-- Nenhum identificador entre aspas. Sem aspas o Oracle normaliza para maiuscula, e o nome
-- resolvido passa a ser o mesmo que o Hibernate procura, independente da caixa em que
-- alguem escreva depois.
--
-- Todo texto e VARCHAR2(n CHAR) porque NLS_LENGTH_SEMANTICS deste banco e BYTE e o
-- conjunto de caracteres e AL32UTF8. Medido: 150 caracteres com cedilha ocupam 299 bytes,
-- e VARCHAR2(150) recusaria o nome que o contrato do Portal declara aceitar.
--
-- Toda restricao e todo indice tem nome explicito, para que a mensagem de erro do Oracle
-- diga qual regra recusou a linha em vez de um nome gerado.

CREATE TABLE APPLICATION (
  ID NUMBER(19) NOT NULL,
  CODE VARCHAR2(80 CHAR) NOT NULL,
  NAME VARCHAR2(150 CHAR) NOT NULL,
  ACTIVE BOOLEAN NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  UPDATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT PK_APPLICATION PRIMARY KEY (ID),
  CONSTRAINT UK_APPLICATION_CODE UNIQUE (CODE),
  -- O guarda do CHR(10) nao e redundante com a ancora. Medido neste Oracle:
  -- REGEXP_LIKE('portal-web' || CHR(10), '^[a-z0-9]+(-[a-z0-9]+)*$') devolve verdadeiro,
  -- porque o cifrao casa antes de uma quebra de linha final. O consumidor valida a string
  -- inteira, entao o banco tambem precisa.
  CONSTRAINT CK_APPLICATION_CODE_FORMAT CHECK (
    REGEXP_LIKE(CODE, '^[a-z0-9]+(-[a-z0-9]+)*$') AND INSTR(CODE, CHR(10)) = 0
  )
);

CREATE TABLE COMPANY (
  ID NUMBER(19) NOT NULL,
  APPLICATION_ID NUMBER(19) NOT NULL,
  NAME VARCHAR2(150 CHAR) NOT NULL,
  CNPJ VARCHAR2(14 CHAR) NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  UPDATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT PK_COMPANY PRIMARY KEY (ID),
  -- Chave alternativa que existe para ser o alvo da chave estrangeira composta de
  -- CLIENT_ACCOUNT. A ordem das colunas e a ordem do par na clausula REFERENCES.
  CONSTRAINT UK_COMPANY_APPLICATION_ID UNIQUE (APPLICATION_ID, ID),
  CONSTRAINT UK_COMPANY_CNPJ UNIQUE (APPLICATION_ID, CNPJ),
  CONSTRAINT CK_COMPANY_CNPJ_DIGITS CHECK (REGEXP_LIKE(CNPJ, '^[0-9]{14}$')),
  CONSTRAINT FK_COMPANY_APPLICATION FOREIGN KEY (APPLICATION_ID)
    REFERENCES APPLICATION (ID)
);

CREATE TABLE CLIENT_ACCOUNT (
  ID NUMBER(19) NOT NULL,
  APPLICATION_ID NUMBER(19) NOT NULL,
  COMPANY_ID NUMBER(19),
  NAME VARCHAR2(150 CHAR) NOT NULL,
  EMAIL VARCHAR2(254 CHAR) NOT NULL,
  EMAIL_NORMALIZED VARCHAR2(254 CHAR) NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  UPDATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT PK_CLIENT_ACCOUNT PRIMARY KEY (ID),
  -- Unicidade composta por aplicacao, sobre a forma minuscula guardada em coluna propria.
  -- Coluna e nao indice de funcao porque o validate do Hibernate nao enxerga indice de
  -- funcao, e a comparacao de VARCHAR2 no Oracle e sensivel a caixa.
  CONSTRAINT UK_CLIENT_ACCOUNT_EMAIL UNIQUE (APPLICATION_ID, EMAIL_NORMALIZED),
  -- Sem este CHECK a coluna normalizada pode receber caixa mista e a unicidade acima
  -- deixa de significar o que promete.
  CONSTRAINT CK_CLIENT_ACCOUNT_EMAIL_LOWER CHECK (EMAIL_NORMALIZED = LOWER(EMAIL)),
  CONSTRAINT FK_CLIENT_ACCOUNT_APPLICATION FOREIGN KEY (APPLICATION_ID)
    REFERENCES APPLICATION (ID),
  -- Chave estrangeira composta: a empresa referida precisa pertencer a mesma aplicacao da
  -- conta. Com a empresa referida por ID sozinho, nada no banco impediria vincular conta
  -- da aplicacao A a empresa da aplicacao B.
  CONSTRAINT FK_CLIENT_ACCOUNT_COMPANY FOREIGN KEY (APPLICATION_ID, COMPANY_ID)
    REFERENCES COMPANY (APPLICATION_ID, ID)
);

-- Indice da chave estrangeira composta. Os outros dois vinculos nao ganham indice proprio
-- porque ja existe indice de restricao cuja coluna inicial e a coluna do vinculo, e
-- indice repetido e escrita a mais em cada INSERT sem nenhuma leitura a mais atendida.
CREATE INDEX IX_CLIENT_ACCOUNT_COMPANY ON CLIENT_ACCOUNT (APPLICATION_ID, COMPANY_ID);

-- Administrador em tabela propria: ele nao pertence a aplicacao nenhuma e nao tem vinculo
-- com empresa, entao nao existe coluna anulavel aqui para modelar nao se aplica.
CREATE TABLE ADMINISTRATOR (
  ID NUMBER(19) NOT NULL,
  NAME VARCHAR2(150 CHAR) NOT NULL,
  EMAIL VARCHAR2(254 CHAR) NOT NULL,
  EMAIL_NORMALIZED VARCHAR2(254 CHAR) NOT NULL,
  ACTIVE BOOLEAN NOT NULL,
  CREATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  UPDATED_AT TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT PK_ADMINISTRATOR PRIMARY KEY (ID),
  CONSTRAINT UK_ADMINISTRATOR_EMAIL UNIQUE (EMAIL_NORMALIZED),
  CONSTRAINT CK_ADMINISTRATOR_EMAIL_LOWER CHECK (EMAIL_NORMALIZED = LOWER(EMAIL))
);

-- O usuario da aplicacao nao tem DDL e nao e dono de nada. Ele le e grava por papel,
-- concedido aqui porque o papel existe antes da tabela e a tabela nasce nesta migracao.
GRANT SELECT, INSERT, UPDATE, DELETE ON APPLICATION TO AUTH_READWRITE;
GRANT SELECT, INSERT, UPDATE, DELETE ON COMPANY TO AUTH_READWRITE;
GRANT SELECT, INSERT, UPDATE, DELETE ON CLIENT_ACCOUNT TO AUTH_READWRITE;
GRANT SELECT, INSERT, UPDATE, DELETE ON ADMINISTRATOR TO AUTH_READWRITE;
