-- No maximo uma credencial de uso unico pendente por conta e por proposito.
--
-- A regra diz que o pedido novo mata o anterior, e o codigo cumpria isso invalidando antes de
-- inserir. Ordem de comando nao e invariante: no isolamento de leitura confirmada duas
-- transacoes nao se veem inserir, entao as duas invalidam o que estava confirmado, nenhuma
-- enxerga a linha da outra, e as duas inserem. Medido com quatro pedidos simultaneos de
-- recuperacao para a mesma conta: ficaram **quatro** links validos ao mesmo tempo, onde a
-- regra promete um. Recuperacao de senha existe para a conta que pode estar comprometida,
-- entao manter vivo um link que o pedido da vitima deveria ter matado e o oposto do que ela
-- pediu.
--
-- Indice de funcao, e nao restricao unica, porque o invariante vale so para linha pendente:
-- linha ja usada sai do indice, porque as duas expressoes viram nulas e indice do Oracle nao
-- guarda chave inteiramente nula. Assim a conta pode acumular historico de links usados sem
-- limite, e pendente e que fica em um.
--
-- Os dois propositos entram na chave porque eles convivem de proposito: quem acabou de se
-- cadastrar tem link de confirmacao pendente e pode pedir recuperacao no mesmo dia.
--
-- Indice de funcao nao e visto por unique_key_validation NAMED do Hibernate, que so olha o
-- que esta mapeado, e esta tabela nao tem entidade. Fica dito aqui porque a subida nao vai
-- reclamar se alguem apagar este indice.
--
-- V006 arruma o acervo antes deste indice existir, e as duas coisas sao arquivos separados de
-- proposito: medido, com a limpeza e o indice no mesmo arquivo, a falha do indice fez o
-- Flyway desfazer a limpeza junto, e a subida seguinte encontrava o mesmo acervo e falhava
-- igual. A suite nao pegou nada disso porque ela roda contra banco vazio, e banco sem dado
-- nao exercita migracao de dado.
-- O indice ja existente nao e erro, e sim este mesmo indice criado por uma versao anterior
-- desta migracao, quando limpeza e esquema moravam no mesmo arquivo. Isso aconteceu de
-- verdade no banco de desenvolvimento: a aplicacao reiniciava em laco, cada subida tentava a
-- migracao outra vez, e uma delas passou depois de a limpeza ter confirmado. Sem este guarda,
-- aquele banco nunca mais migraria sem intervencao manual, e e exatamente esse tipo de beco
-- sem saida que uma migracao nao deve criar.
DECLARE
  indice_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(indice_ja_existe, -955);
BEGIN
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UX_ACCOUNT_CONFIRMATION_PENDING'
    || ' ON ACCOUNT_CONFIRMATION ('
    || ' CASE WHEN USED_AT IS NULL THEN CLIENT_ACCOUNT_ID END,'
    || ' CASE WHEN USED_AT IS NULL THEN PURPOSE END)';
EXCEPTION
  WHEN indice_ja_existe THEN NULL;
END;
/
