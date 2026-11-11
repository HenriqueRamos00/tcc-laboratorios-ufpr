USE [autolab];
GO

IF OBJECT_ID(N'dbo.vwRelatoriosEnsaios', N'V') IS NOT NULL DROP VIEW dbo.vwRelatoriosEnsaios;
IF OBJECT_ID(N'dbo.vwDadosEnsaios', N'V') IS NOT NULL DROP VIEW dbo.vwDadosEnsaios;
IF OBJECT_ID(N'dbo.Equipamentos', N'V') IS NOT NULL DROP VIEW dbo.Equipamentos;
IF OBJECT_ID(N'dbo.SituaProcess', N'V') IS NOT NULL DROP VIEW dbo.SituaProcess;
IF OBJECT_ID(N'dbo.dCliente', N'V') IS NOT NULL DROP VIEW dbo.dCliente;
IF OBJECT_ID(N'dbo.Equipamentos', N'U') IS NOT NULL DROP TABLE dbo.Equipamentos;
IF OBJECT_ID(N'dbo.SituaProcess', N'U') IS NOT NULL DROP TABLE dbo.SituaProcess;
IF OBJECT_ID(N'dbo.DadosEnsaios', N'U') IS NOT NULL DROP TABLE dbo.DadosEnsaios;
IF OBJECT_ID(N'dbo.RelatoriosEnsaios', N'U') IS NOT NULL DROP TABLE dbo.RelatoriosEnsaios;
IF OBJECT_ID(N'dbo.DadosEnsaiosLocal', N'U') IS NOT NULL DROP TABLE dbo.DadosEnsaiosLocal;
IF OBJECT_ID(N'dbo.EquipamentosLocal', N'U') IS NOT NULL DROP TABLE dbo.EquipamentosLocal;
IF OBJECT_ID(N'dbo.SituaProcessLocal', N'U') IS NOT NULL DROP TABLE dbo.SituaProcessLocal;
IF OBJECT_ID(N'dbo.SolicitacaoRelatorioLocal', N'U') IS NOT NULL DROP TABLE dbo.SolicitacaoRelatorioLocal;
IF OBJECT_ID(N'dbo.ClienteLocal', N'U') IS NOT NULL DROP TABLE dbo.ClienteLocal;
IF OBJECT_ID(N'dbo.RelatorioEmissao', N'U') IS NOT NULL DROP TABLE dbo.RelatorioEmissao;
IF OBJECT_ID(N'dbo.FileData', N'U') IS NOT NULL DROP TABLE dbo.FileData;
GO

CREATE TABLE dbo.DadosEnsaiosLocal
(
    Identificacao    nvarchar(100) NULL,
    ProtocoloLactec  nvarchar(100) NULL,
    NumSerie         nvarchar(250) NULL,
    Name             nvarchar(250) NULL,
    Resultado        nvarchar(max) NULL,
    EmpresaTrafo     nvarchar(max) NULL,
    DataEmissao      datetime      NULL,
    DataFinalAnalise datetime      NULL
);
GO

CREATE TABLE dbo.EquipamentosLocal
(
    NumSerie        nvarchar(250) NULL,
    Tag             nvarchar(100) NULL,
    AnoFabricacao   nvarchar(100) NULL,
    Potencia        nvarchar(100) NULL,
    Tensao          nvarchar(100) NULL,
    Fabricante      nvarchar(250) NULL,
    VolumeOleo      nvarchar(100) NULL,
    UnidadePotencia nvarchar(20)  NULL,
    TipoDeOleo      nvarchar(100) NULL,
    TipoEquipamento nvarchar(100) NULL
);
GO

CREATE TABLE dbo.SituaProcessLocal
(
    OrcamentoCRM         nvarchar(100) NULL,
    Recebido             bit           NULL,
    Protocolado          bit           NULL,
    SituacaoEnsaios      int           NULL,
    SolicitacaoRelatorio int           NOT NULL
);
GO

CREATE TABLE dbo.FileData
(
    Oid                 uniqueidentifier NOT NULL CONSTRAINT PK_FileData PRIMARY KEY,
    size                int              NULL,
    FileName            nvarchar(260)    NULL,
    Content             varbinary(max)   NULL,
    OptimisticLockField int              NULL,
    GCRecord            int              NULL
);
GO

CREATE TABLE dbo.RelatorioEmissao
(
    Oid                       uniqueidentifier NOT NULL CONSTRAINT PK_RelatorioEmissao PRIMARY KEY,
    Identificacao             nvarchar(100)    NULL,
    PdfAssinado               uniqueidentifier NULL,
    PdfLiberado               varbinary(max)   NULL,
    GCRecord                  int              NULL,
    Revisao                   int              NULL,
    RelatorioEnsaioSubstituto uniqueidentifier NULL,
    DataEmissao               datetime         NULL,
    DataPublicacao            datetime         NULL,
    TituloRelatorio           nvarchar(250)    NULL,
    ObjetivoRelatorio         nvarchar(500)    NULL,
    Conclusao                 nvarchar(max)    NULL,
    Observacoes               nvarchar(max)    NULL,
    Solicitacao               uniqueidentifier NULL,
    OptimisticLockField       int              NULL
);
GO

CREATE INDEX iPdfAssinado_RelatorioEmissao ON dbo.RelatorioEmissao (PdfAssinado);
CREATE INDEX iGCRecordIdentificacao_RelatorioEmissao ON dbo.RelatorioEmissao (GCRecord, Identificacao);
GO

CREATE TABLE dbo.SolicitacaoRelatorioLocal
(
    Oid                        uniqueidentifier NOT NULL CONSTRAINT PK_SolicitacaoRelatorioLocal PRIMARY KEY,
    NumeroSolicitacao          nvarchar(15)     NULL,
    OrcamentoCRM               nvarchar(100)    NULL,
    IDAmostra                  nvarchar(max)    NULL,
    Nome                       varchar(42)      NULL,
    ClienteSolicitante         nvarchar(250)    NULL,
    DataFinalProgramadaCliente datetime         NULL,
    SituacaoRelatorio          varchar(11)      NULL,
    DiasIntervalo              int              NULL
);
GO

CREATE TABLE dbo.ClienteLocal
(
    Oid         uniqueidentifier NOT NULL CONSTRAINT PK_ClienteLocal PRIMARY KEY,
    Sigla       nvarchar(100)    NULL,
    Nome        nvarchar(250)    NULL,
    RazaoSocial nvarchar(250)    NULL,
    Cnpj_Cpf    nvarchar(30)     NULL
);
GO

CREATE VIEW dbo.vwDadosEnsaios AS
SELECT Identificacao, ProtocoloLactec, NumSerie, Name, Resultado, EmpresaTrafo, DataEmissao, DataFinalAnalise
FROM dbo.DadosEnsaiosLocal;
GO

CREATE VIEW dbo.Equipamentos AS
SELECT NumSerie, Tag, AnoFabricacao, Potencia, Tensao, Fabricante, VolumeOleo, UnidadePotencia, TipoDeOleo, TipoEquipamento
FROM dbo.EquipamentosLocal;
GO

CREATE VIEW dbo.SituaProcess AS
SELECT OrcamentoCRM, Recebido, Protocolado, SituacaoEnsaios, SolicitacaoRelatorio
FROM dbo.SituaProcessLocal;
GO

CREATE VIEW dbo.vwRelatoriosEnsaios AS
SELECT
    solicitacao.NumeroSolicitacao,
    relatorio.Oid,
    solicitacao.OrcamentoCRM,
    solicitacao.IDAmostra,
    solicitacao.Nome,
    relatorio.PdfAssinado,
    solicitacao.ClienteSolicitante,
    relatorio.Identificacao,
    solicitacao.DataFinalProgramadaCliente,
    solicitacao.SituacaoRelatorio,
    relatorio.DataEmissao,
    relatorio.DataPublicacao,
    solicitacao.DiasIntervalo
FROM dbo.RelatorioEmissao AS relatorio
INNER JOIN dbo.SolicitacaoRelatorioLocal AS solicitacao ON solicitacao.Oid = relatorio.Oid
WHERE relatorio.GCRecord IS NULL;
GO

CREATE VIEW dbo.dCliente AS
SELECT Oid, Sigla, Nome, RazaoSocial, Cnpj_Cpf
FROM dbo.ClienteLocal;
GO
