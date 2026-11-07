-- O formato dos quatro objetos do AutoLAB que o portal le.
--
-- ATENCAO: este DDL e PROVISORIO. Ele foi deduzido dos extratos `select *` que o laboratorio
-- enviou, e nao do esquema real: os nomes e a ordem das colunas sao os que os extratos trazem,
-- mas o TIPO e o TAMANHO de cada uma sao suposicao. Enquanto o laboratorio nao mandar
--
--   SELECT * FROM INFORMATION_SCHEMA.COLUMNS
--    WHERE TABLE_NAME IN ('Equipamentos', 'SituaProcess', 'vwDadosEnsaios', 'vwRelatoriosEnsaios');
--
-- nenhum adaptador de banco deve ser escrito contra este arquivo. Um `varchar` daqui que seja
-- `nvarchar` la muda o comportamento do preenchimento a direita e do acento, e a suite ficaria
-- verde contra um banco que nos desenhamos.
--
-- Por que varchar e nao nvarchar: o branco a direita ignorado na comparacao de texto, que e o
-- comportamento que o portal precisa enfrentar, e os extratos nao
-- trazem caractere fora do latino-1. Se a producao usar nvarchar, este arquivo muda junto.
--
-- Por que toda coluna e texto e nenhuma e numero: medido nos extratos. `Potencia` traz
-- `2000/2500` e `3/3/2,5`, que sao enrolamentos multiplos; `Tensao` traz `13,800/220`;
-- `AnoFabricacao` traz `05/25` e `01/03/2025`; `VolumeOleo` traz `1L` e `6 KG`. Tipar qualquer
-- uma recusa linha verdadeira ja na leitura.
USE [autolab];
GO

IF OBJECT_ID(N'dbo.vwRelatoriosEnsaios', N'V') IS NOT NULL DROP VIEW dbo.vwRelatoriosEnsaios;
IF OBJECT_ID(N'dbo.vwDadosEnsaios', N'V') IS NOT NULL DROP VIEW dbo.vwDadosEnsaios;
IF OBJECT_ID(N'dbo.SituaProcess', N'U') IS NOT NULL DROP TABLE dbo.SituaProcess;
IF OBJECT_ID(N'dbo.Equipamentos', N'U') IS NOT NULL DROP TABLE dbo.Equipamentos;
IF OBJECT_ID(N'dbo.DadosEnsaios', N'U') IS NOT NULL DROP TABLE dbo.DadosEnsaios;
IF OBJECT_ID(N'dbo.RelatoriosEnsaios', N'U') IS NOT NULL DROP TABLE dbo.RelatoriosEnsaios;
GO

-- O cadastro de equipamentos. Dez colunas e nada mais: sem id, sem data e sem empresa.
--
-- A ausencia de chave primaria e deliberada e reproduz o que foi medido: nas 20575 linhas do
-- extrato ha 58 numeros de serie em mais de uma linha, e 49 continuam divergindo depois de
-- descartar linha identica. Declarar `NumSerie` como chave aqui faria a carga falhar e
-- esconderia justamente o caso que a regra de concordancia existe para tratar.
CREATE TABLE dbo.Equipamentos
(
    NumSerie        varchar(120)  NULL,
    Tag             varchar(120)  NULL,
    AnoFabricacao   varchar(20)   NULL,
    Potencia        varchar(40)   NULL,
    Tensao          varchar(40)   NULL,
    Fabricante      varchar(120)  NULL,
    VolumeOleo      varchar(40)   NULL,
    UnidadePotencia varchar(20)   NULL,
    TipoDeOleo      varchar(60)   NULL,
    TipoEquipamento varchar(60)   NULL
);
GO

-- O estado do processo por orcamento. Cinco colunas e nada mais.
--
-- `OrcamentoCRM` tambem nao e chave: 76 valores se repetem no extrato, 46 deles com estados
-- diferentes, porque a linha e por amostra e a coluna que identificaria a amostra nao existe
-- nesta consulta. As colunas de estado sao `int` porque os extratos so trazem digito ou vazio,
-- e `SituacaoEnsaios` aceita NULL porque 690 linhas vem sem valor.
CREATE TABLE dbo.SituaProcess
(
    OrcamentoCRM         varchar(120) NULL,
    Recebido             int          NULL,
    Protocolado          int          NULL,
    SituacaoEnsaios      int          NULL,
    SolicitacaoRelatorio int          NULL
);
GO

-- As medicoes, uma linha por item medido.
--
-- `Resultado` tem tres formas e as tres significam coisas diferentes: numero quer dizer que o
-- ensaio foi feito e deu aquilo, `ND` quer dizer que foi feito e nada foi detectado, e NULL
-- quer dizer que nao foi realizado. Por isso a coluna e texto anulavel: colapsar `ND` em vazio
-- faz a tela afirmar ausencia de gas onde houve ausencia de visita.
CREATE TABLE dbo.DadosEnsaios
(
    Identificacao    varchar(60)   NULL,
    ProtocoloLactec  varchar(60)   NULL,
    NumSerie         varchar(120)  NULL,
    Name             varchar(120)  NULL,
    Resultado        varchar(40)   NULL,
    EmpresaTrafo     varchar(200)  NULL,
    DataEmissao      datetime      NULL,
    DataFinalAnalise datetime      NULL
);
GO

-- Os laudos. `Oid` e GUID e e a chave real; uma `NumeroSolicitacao` tem varios relatorios.
-- `DataFinalProgramadaCliente` e `DiasIntervalo` vem vazios em metade das linhas do extrato.
CREATE TABLE dbo.RelatoriosEnsaios
(
    NumeroSolicitacao          varchar(60)      NULL,
    Oid                        uniqueidentifier NOT NULL,
    OrcamentoCRM               varchar(120)     NULL,
    IDAmostra                  varchar(60)      NULL,
    Nome                       varchar(120)     NULL,
    ClienteSolicitante         varchar(200)     NULL,
    Identificacao              varchar(60)      NULL,
    DataFinalProgramadaCliente datetime         NULL,
    SituacaoRelatorio          varchar(40)      NULL,
    DataEmissao                datetime         NULL,
    DataPublicacao             datetime         NULL,
    DiasIntervalo              varchar(20)      NULL
);
GO

-- As duas views que o portal consulta pelo nome com prefixo vw, como o laboratorio as expoe.
CREATE VIEW dbo.vwDadosEnsaios AS SELECT * FROM dbo.DadosEnsaios;
GO

CREATE VIEW dbo.vwRelatoriosEnsaios AS SELECT * FROM dbo.RelatoriosEnsaios;
GO
