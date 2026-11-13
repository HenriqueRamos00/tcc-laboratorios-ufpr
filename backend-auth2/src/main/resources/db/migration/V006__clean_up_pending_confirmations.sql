-- Arruma o acervo de credenciais pendentes antes de o invariante passar a valer.
--
-- A regra diz que o pedido novo mata o anterior, e o codigo cumpria isso invalidando antes de
-- inserir. Ordem de comando nao e invariante: no isolamento de leitura confirmada duas
-- transacoes nao se veem inserir, entao as duas invalidam o que estava confirmado, nenhuma
-- enxerga a linha da outra, e as duas inserem. Medido com quatro pedidos simultaneos para a
-- mesma conta: ficaram quatro links de recuperacao validos ao mesmo tempo. E medido no banco
-- de desenvolvimento, que viveu com o defeito: uma conta acumulou **dez** links de
-- recuperacao pendentes.
--
-- Sobrevive o pedido mais recente de cada conta e proposito, que e o que a regra promete. Os
-- outros passam a contar como usados, que e o mesmo estado em que o codigo os deixaria.
--
-- Esta limpeza mora numa migracao separada da que cria o indice, e isso foi aprendido
-- medindo: com as duas no mesmo arquivo, a falha do indice fez o Flyway desfazer tambem a
-- limpeza, e a subida seguinte encontrava o mesmo acervo sujo e falhava igual. Migracao de
-- dado e migracao de esquema confirmam em momentos diferentes, e por isso sao dois arquivos.
UPDATE ACCOUNT_CONFIRMATION
   SET USED_AT = SYSTIMESTAMP
 WHERE USED_AT IS NULL
   AND ID NOT IN (
     SELECT ID FROM (
       SELECT ID,
              ROW_NUMBER() OVER (PARTITION BY CLIENT_ACCOUNT_ID, PURPOSE
                                 ORDER BY CREATED_AT DESC, ID DESC) AS mais_recente
         FROM ACCOUNT_CONFIRMATION
        WHERE USED_AT IS NULL
     )
      WHERE mais_recente = 1
   );
