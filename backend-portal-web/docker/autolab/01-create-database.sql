-- O banco do AutoLAB para desenvolvimento, na mesma instancia que a demonstracao ja sobe.
--
-- A collation e a decisao mais importante deste arquivo, e ela e PROVISORIA ate o laboratorio
-- confirmar a de producao. SQL_Latin1_General_CP1_CI_AS e o padrao de instalacao brasileira, e
-- o CI_AS quer dizer que o banco NAO distingue caixa, mas DISTINGUE acento. A documentacao
-- define o sufixo assim: accent-sensitive (_AS) distingue caractere acentuado de nao
-- acentuado, e quem apaga essa distincao e o _AI. Medido nesta instancia: 'ENSAIO' = 'ensaio'
-- da IGUAIS, 'ENSAIO' = 'ENSAIO com acento' da DIFERENTES, e sob _AI os dois dao IGUAIS.
--
-- Isso importa porque a regra de casamento de numero de serie do portal e ordinal, byte a byte:
-- sob esta collation, um WHERE NumSerie = 'teste' tambem casa 'TESTE', enquanto duas series
-- que diferem so por acento seguem distintas. O adaptador precisa de COLLATE Latin1_General_BIN2
-- explicito na comparacao, ou refazer a comparacao em C#. Subir o banco com a collation errada
-- faria a suite provar o contrario do que acontece em producao.
IF DB_ID(N'autolab') IS NULL
    CREATE DATABASE [autolab] COLLATE SQL_Latin1_General_CP1_CI_AS;
GO

-- O IF acima cria, mas nao converge: um banco autolab que ja exista fica com a collation dele,
-- e o DDL seguinte a herda coluna por coluna, porque nenhuma coluna declara COLLATE. Numa
-- instancia de outra pagina de codigo os literais acentuados da carga entram achatados, sem
-- erro e sem aviso, porque a documentacao avisa que perda de dado em traducao de pagina de
-- codigo nao e reportada. Entao a collation declarada aqui passa a ser verificada, e nao so
-- desejada: parar com erro custa um `docker compose down -v`, seguir custa uma massa adulterada
-- que ninguem percebe.
IF CAST(DATABASEPROPERTYEX(N'autolab', 'Collation') AS nvarchar(128))
   <> N'SQL_Latin1_General_CP1_CI_AS'
    THROW 50001, N'O banco autolab existe com outra collation. Apague o banco antes de carregar a massa.', 1;
GO
