-- Leva o acervo para o modelo novo antes de o esquema antigo ser desmontado.
--
-- Este arquivo so tem comando de dado, e fica separado da V011 de proposito, pela mesma
-- licao da V006: comando de esquema confirma a transacao no Oracle, e com os dois no mesmo
-- arquivo uma falha no meio deixaria metade da conversao gravada.
--
-- A empresa deixa de pertencer a uma aplicacao e o CNPJ passa a ser unico no servico
-- inteiro. O mesmo CNPJ cadastrado em duas aplicacoes vira uma empresa so: fica a de menor
-- identificador, que e a mais antiga, e as demais sao removidas depois de os vinculos
-- terem sido apontados para a que fica. O nome das removidas se perde, e isso e aceito
-- porque elas descrevem a mesma pessoa juridica.

INSERT INTO CLIENT_ACCOUNT_COMPANY (CLIENT_ACCOUNT_ID, COMPANY_ID)
SELECT account.ID,
       (SELECT MIN(kept.ID) FROM COMPANY kept WHERE kept.CNPJ = company.CNPJ)
  FROM CLIENT_ACCOUNT account
  JOIN COMPANY company ON company.ID = account.COMPANY_ID;

UPDATE CLIENT_ACCOUNT SET COMPANY_ID = NULL WHERE COMPANY_ID IS NOT NULL;

DELETE FROM COMPANY duplicated
 WHERE duplicated.ID > (SELECT MIN(kept.ID) FROM COMPANY kept
                         WHERE kept.CNPJ = duplicated.CNPJ);
