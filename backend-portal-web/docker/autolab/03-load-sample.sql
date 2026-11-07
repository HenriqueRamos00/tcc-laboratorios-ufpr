-- A massa de desenvolvimento do AutoLAB.
--
-- Ela NAO e o extrato que o laboratorio enviou, e isso e deliberado. Os extratos tem nome de
-- cliente real dentro de tres colunas, e nao so os quatro nomes que a varredura do repositorio
-- conhece: foram medidas cerca de 110 linhas com nome de empresa, em Fabricante, em Tag e
-- dentro do proprio NumSerie. Copiar linha real para ca poria nome de cliente num arquivo
-- versionado, e listar aqui quais nomes sao faria esta massa acusar na propria varredura que
-- ela deveria manter limpa. Quem precisar da lista mede no extrato, que vive fora do git.
--
-- O que esta massa preserva e a FORMA de cada classe medida, que e o que o adaptador precisa
-- enfrentar. Cada bloco abaixo diz qual classe ele exercita e quantas linhas do extrato real
-- caem nela. Os valores sao inventados; os numeros de serie reais que aparecem sao os quatro
-- que o repositorio ja carrega em LabTestSample.jsonl, porque sao a chave da juncao.
--
-- Duas series ficam de fora de proposito: 884120 e 884121. Elas foram inventadas por este
-- projeto para dois laudos que vieram sem serie, e a juncao medida falha exatamente nelas. Uma
-- linha de cadastro para qualquer uma apagaria essa evidencia.
USE [autolab];
GO

-- Os quatro DELETE deixam o banco no mesmo estado a cada execucao, e dois deles limpam tabela
-- que esta carga nao preenche. DadosEnsaios e RelatoriosEnsaios ficam vazias de proposito
-- enquanto a massa de ensaios vive em LabTestSample.jsonl, logo as duas views que o portal
-- consulta devolvem zero linha. Consequencia para quem apontar o portal para este banco:
-- listagem vazia, e nao por defeito do cadastro.
DELETE FROM dbo.Equipamentos;
DELETE FROM dbo.SituaProcess;
DELETE FROM dbo.DadosEnsaios;
DELETE FROM dbo.RelatoriosEnsaios;
GO

-- Cadastro de equipamentos.
INSERT INTO dbo.Equipamentos
    (NumSerie, Tag, AnoFabricacao, Potencia, Tensao, Fabricante, VolumeOleo, UnidadePotencia, TipoDeOleo, TipoEquipamento)
VALUES
    -- Placa completa, caso comum. 571289 existe nas analises da amostra embutida.
    ('571289', 'TR-00001', '2011', '50', '220', 'FABRICANTE DE TRANSFORMADORES', '20570', 'MVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR'),

    -- A mesma serie em duas linhas que CONCORDAM: colapsa e continua respondendo.
    -- No extrato sao 9 series assim, das 58 repetidas.
    ('36170301', 'TR-00002', '2008', '2000/2500', '34,5', 'FABRICANTE DE REATORES', '1.271,5', 'kVA', 'Óleo Vegetal Isolante', 'REATOR'),
    ('36170301', 'TR-00002', '2008', '2000/2500', '34,5', 'FABRICANTE DE REATORES', '1.271,5', 'kVA', 'Óleo Vegetal Isolante', 'REATOR'),

    -- A mesma serie em duas linhas que DIVERGEM em tensao e ano: esses dois campos nao saem,
    -- o resto sai. No extrato sao 49 series assim, e a tensao diverge em 11 delas.
    ('111.594/1', 'TR-00003', '2012', '45,33', '525', 'MONTADORA DE EQUIPAMENTOS', '9,950', 'MVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR DE POTÊNCIA'),
    ('111.594/1', 'TR-00003', '05/2012', '45,33', '13,8', 'MONTADORA DE EQUIPAMENTOS', '9,950', 'MVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR DE POTÊNCIA'),

    -- Potencia que nao e numero, sem unidade. No extrato sao 100 linhas com potencia e sem
    -- UnidadePotencia, e 14 com barra. A potencia sai nua: completar com kVA erraria por mil
    -- nas 2167 linhas que sao MVA.
    ('5233', 'TR-00004', '05/25', '3/3/2,5', '13,800/220', 'FABRICANTE DE TRANSFORMADORES', '1L', NULL, 'Óleo Mineral Lubrificante', 'REGULADOR DE TENSÃO'),

    -- Unidade sem potencia: nao vira nada. No extrato sao 7 linhas.
    ('900001', 'TR-00005', '01/03/2025', NULL, '88', 'FABRICANTE DE REATORES', '6 KG', 'kVAr', 'Óleo Silicone Isolante', 'COMUTADOR'),

    -- Linha que so carrega a serie: placa muda, indistinguivel de serie que o cadastro nao tem.
    ('900002', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),

    -- Marcadores de ausencia. Cada um deles aparece no extrato e nenhum pode chegar a tela.
    ('900003', '-', 'S/N', '#', '*', 'NA', 'NÃO INFORMADO', '-', 'Óleo Mineral Isolante', 'TRANSFORMADOR'),

    -- Serie com espaco no fim. No extrato sao 10 linhas. A comparacao de texto preenche o lado
    -- mais curto com branco, entao `= '900004'` casa com esta. Isso nao vem do ANSI_PADDING,
    -- que decide so se o branco e gravado: medido, `LIKE '900004   '` NAO casa, porque no
    -- padrao do LIKE todo caractere conta, inclusive branco de borda. E o caso que so um banco
    -- de verdade prova.
    ('900004   ', 'TR-00006', '1998', '112', '138', 'MONTADORA DE EQUIPAMENTOS', '3.400', 'kVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR'),

    -- Serie com o byte de controle 0x1F no meio. No extrato sao 2 linhas. Ela nunca casa com
    -- nada, e esse e o desfecho certo: limpar caractere invisivel funde orcamento distinto.
    ('900005' + CHAR(31) + '-A', 'TR-00007', '2015', '75', '34,5', 'FABRICANTE DE REATORES', '890', 'kVA', 'Óleo Vegetal Isolante', 'REATOR'),

    -- Duas series que diferem SO na caixa. Sob a collation CI_AS do banco elas sao a mesma, e
    -- sob a regra ordinal do portal sao duas. E o par que prova se o adaptador escapou da
    -- collation com COLLATE Latin1_General_BIN2.
    ('ENSAIO-TESTE', 'TR-00008', '2020', '15', '13,8', 'FABRICANTE DE TRANSFORMADORES', '500', 'kVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR'),
    ('ensaio-teste', 'TR-00009', '2021', '30', '13,8', 'FABRICANTE DE TRANSFORMADORES', '700', 'kVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR'),

    -- Serie que nenhuma analise menciona. Ela nao pode aparecer em listagem de cliente nenhum:
    -- o cadastro so decora equipamento que as analises ja trouxeram.
    ('999777', 'TR-00010', '2019', '25', '69', 'MONTADORA DE EQUIPAMENTOS', '1.800', 'MVA', 'Óleo Mineral Isolante', 'TRANSFORMADOR');
GO

-- Estado do processo por orcamento.
--
-- Os codigos acompanham os da demonstracao do portal para a tela mostrar algo, e cobrem as tres
-- formas medidas de OrcamentoCRM: a simples, a com prefixo EAQ_ e sufixo de versao, e a com
-- prefixo `EAQ ` separado por espaco.
INSERT INTO dbo.SituaProcess (OrcamentoCRM, Recebido, Protocolado, SituacaoEnsaios, SolicitacaoRelatorio)
VALUES
    -- O caso de longe mais comum: 2268 das 3345 linhas.
    ('EAQ_2026_45484_V_1', 1, 1, 3, 2),

    -- So existe no Salesforce, nada no AutoLAB. 687 linhas. SituacaoEnsaios NULL.
    ('EAQ_2026_45482_V_1', 0, 0, NULL, 0),

    -- O mesmo caso com 0 no lugar de NULL. 87 linhas. A porteira do Recebido resolve os dois.
    ('EAQ_2026_45485_V_1', 0, 0, 0, 0),

    -- Recebido e em execucao. 192 linhas.
    ('EAQ_2026_45487_V_1', 1, 1, 0, 0),

    -- Validado com relatorio em elaboracao. 69 linhas.
    ('EAQ_2026_45489_V_1', 1, 1, 2, 1),

    -- Finalizado sem relatorio: a etapa para em finalizado. 6 linhas.
    ('EAQ_2026_45491_V_1', 1, 1, 3, 0),

    -- SituacaoEnsaios 5 e 4, fora da legenda que o laboratorio deu. 12 e 7 linhas. Pela regra
    -- do dono, tudo acima de 3 e finalizado, e o pedido de relatorio vence quando existe.
    ('EAQ_2026_45493_V_1', 1, 1, 5, 2),
    ('EAQ_2026_45494_V_1', 1, 1, 4, 0),

    -- Recebido e protocolado sem situacao de ensaio. 3 linhas. NULL vale 0, logo em execucao.
    ('EAQ 2026_45496', 1, 1, NULL, 0),

    -- Registro lancado errado na origem: ensaio completo sem recebimento. 1 linha nas 3345.
    -- A porteira do Recebido descarta sem caso especial no codigo.
    ('2026_45498', 0, 0, 1, 0),

    -- O mesmo orcamento em duas linhas com estados diferentes: uma amostra publicada e outra
    -- que nem chegou. 46 orcamentos assim. O resumo honesto e a linha menos avancada.
    ('EAQ_2026_45486_V_1', 1, 1, 3, 2),
    ('EAQ_2026_45486_V_1', 0, 0, NULL, 0),

    -- Sufixo livre: sao orcamentos DISTINTOS que compartilham o numero base, e casar por
    -- prefixo funde usinas diferentes.
    ('2026_45500_UHE Generica I', 1, 1, 3, 2),
    ('2026_45500_UHE Generica II', 1, 1, 0, 0),

    -- Espaco no fim, outra vez o branco ignorado na comparacao.
    ('2026_45502 ', 1, 1, 3, 2),

    -- Byte de controle no meio do codigo.
    ('2026' + CHAR(31) + '_45504', 1, 1, 3, 2);
GO

SELECT 'Equipamentos' AS tabela, COUNT(*) AS linhas FROM dbo.Equipamentos
UNION ALL
SELECT 'SituaProcess', COUNT(*) FROM dbo.SituaProcess;
GO
