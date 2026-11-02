-- O formato do codigo de aplicacao deixa de depender de parametro de sessao.
--
-- A restricao de V001 usava a expressao de intervalo [a-z0-9], e em Oracle o significado de
-- intervalo em expressao regular sai do NLS_SORT, que e parametro **de sessao**. Medido neste
-- banco, na mesma linha de dados: com NLS_SORT em BINARY o codigo portal-cweb escrito com
-- cedilha e recusado, e com XSPANISH ou GERMAN ele passa. Quem decide o NLS_SORT de uma
-- sessao de aplicacao e o idioma do processo, ou seja, uma variavel de ambiente de contentor.
--
-- O estrago nao ficaria no banco: o Portal valida o mesmo formato na inicializacao dele,
-- entao um codigo gravado com acento impede o Portal de **subir**, e nao apenas de
-- autenticar.
--
-- A sessao da aplicacao passou a fixar NLS_SORT = BINARY no connection-init-sql, e isso
-- fecha o caminho de quem entra pela aplicacao. Esta restricao fecha o de quem entra por
-- fora dela, com script ou com sqlplus, e por isso as duas coisas existem.
--
-- TRANSLATE remove do valor todo caractere permitido, e o que sobrar nao pertence ao
-- conjunto: nulo significa que nada sobrou. O Z inicial no segundo argumento existe porque a
-- funcao devolve nulo quando o terceiro argumento e vazio, o que aceitaria qualquer coisa.
-- O REGEXP continua respondendo pela estrutura do hifen, que precisa separar grupos e nao
-- aparecer dobrado nem nas pontas, e ele nao pergunta nada ao NLS porque [^-] nao e
-- intervalo.
--
-- Medido antes de aplicar: os 17 codigos ja cadastrados passam, e hifen dobrado, hifen no
-- fim, maiuscula, acento e nova linha sao recusados.
ALTER TABLE APPLICATION DROP CONSTRAINT CK_APPLICATION_CODE_FORMAT;

ALTER TABLE APPLICATION ADD CONSTRAINT CK_APPLICATION_CODE_FORMAT CHECK (
  TRANSLATE(CODE, 'Zabcdefghijklmnopqrstuvwxyz0123456789-', 'Z') IS NULL
  AND REGEXP_LIKE(CODE, '^[^-]+(-[^-]+)*$')
  AND INSTR(CODE, CHR(10)) = 0
);
