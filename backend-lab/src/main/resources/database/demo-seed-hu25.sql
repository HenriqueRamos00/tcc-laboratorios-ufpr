INSERT INTO instrumentos_laboratoriais (numero_patrimonio, nome, certificado_calibracao, calibracao_valida_ate)
SELECT seed.patrimonio, seed.nome, seed.certificado, DATE '2027-10-25'
FROM (VALUES ('DEMO-INS-001','Cromatógrafo gasoso de demonstração','CERT-DEMO-2026-001'),
             ('DEMO-INS-002','Balança analítica de demonstração','CERT-DEMO-2026-002')) seed(patrimonio,nome,certificado)
WHERE NOT EXISTS (SELECT 1 FROM instrumentos_laboratoriais i WHERE i.numero_patrimonio=seed.patrimonio);

INSERT INTO equipamentos (numero_serie, tag, tipo, subestacao, tensao_primaria, empresa_id, nivel_risco, ativo)
SELECT '111.594/1', 'DEMO-TR-HU25-01', 'TRANSFORMADOR', 'Subestação de demonstração', 13.800,
       o.empresa_id, 'SAUDAVEL', TRUE
FROM orcamentos o
WHERE o.code = 'DEMO-SEED-2026-001'
  AND NOT EXISTS (SELECT 1 FROM equipamentos e WHERE e.tag = 'DEMO-TR-HU25-01');

INSERT INTO amostras (codigo, descricao, orcamento_id, equipamento_analisado_id)
SELECT seed.codigo, seed.descricao, o.id, NULL
FROM (VALUES ('A-HU25-DEMO-001/2026','Óleo isolante - amostra de demonstração','DEMO-SEED-2026-001'),
             ('A-HU25-DEMO-002/2026','Óleo isolante - amostra complementar','DEMO-SEED-2026-001'),
             ('A-HU25-DEMO-003/2026','Material polimérico - amostra demonstrativa','DEMO-SEED-2026-001')) seed(codigo,descricao,quote_code)
JOIN orcamentos o ON o.code=seed.quote_code
WHERE NOT EXISTS (SELECT 1 FROM amostras a WHERE a.codigo=seed.codigo);

UPDATE amostras SET equipamento_analisado_id=(SELECT e.id FROM equipamentos e WHERE e.tag='DEMO-TR-HU25-01')
WHERE codigo='A-HU25-DEMO-001/2026' AND equipamento_analisado_id IS NULL
  AND EXISTS (SELECT 1 FROM equipamentos e WHERE e.tag='DEMO-TR-HU25-01');

INSERT INTO execucoes_ensaios (codigo, amostra_id, orcamento_id, responsavel_id, tipo, tipo_analise, metodo, norma, data_agendada, classificacao_analise, texto_laudo, status, version, atualizado_em)
SELECT seed.codigo_temp, a.id, a.orcamento_id, u.id, seed.tipo, seed.tipo_analise, seed.metodo, seed.norma, DATE '2026-10-24', seed.classificacao, seed.texto, 'incomplete', 0, TIMESTAMP WITH TIME ZONE '2026-10-25 09:00:00-03:00'
FROM (VALUES ('HU25-TMP-1','A-HU25-DEMO-001/2026','Análise físico-química de óleo','physical-chemical','Análise físico-química','ABNT NBR 10576',NULL,'Rascunho demonstrativo de análise físico-química.'),
             ('HU25-TMP-2','A-HU25-DEMO-001/2026','Cromatografia de gases dissolvidos','chromatography','Cromatografia gasosa','ABNT NBR 7070','alerta','Rascunho demonstrativo. Resultado textual 0,01% preservado.'),
             ('HU25-TMP-3','A-HU25-DEMO-003/2026','Caracterização de polímero - tração',NULL,'Ensaio de tração','ASTM D638',NULL,'Resultado textual demonstrativo: 0,01%.')) seed(codigo_temp,sample_code,tipo,tipo_analise,metodo,norma,classificacao,texto)
JOIN amostras a ON a.codigo=seed.sample_code
JOIN orcamentos o ON o.id=a.orcamento_id
JOIN usuarios u ON lower(trim(u.email))='demo.tecnico.helena@example.test' AND u.perfil='TECNICO'
WHERE NOT EXISTS (SELECT 1 FROM execucoes_ensaios e WHERE e.codigo=seed.codigo_temp);

UPDATE execucoes_ensaios SET codigo='E-' || id || '/2026' WHERE codigo LIKE 'HU25-TMP-%';

INSERT INTO execucoes_instrumentos (execucao_id,instrumento_id)
SELECT e.id,i.id FROM execucoes_ensaios e
JOIN amostras a ON a.id=e.amostra_id
JOIN instrumentos_laboratoriais i ON i.numero_patrimonio=CASE WHEN e.tipo_analise='chromatography' THEN 'DEMO-INS-001' ELSE 'DEMO-INS-002' END
WHERE e.codigo LIKE 'E-%/2026' AND a.codigo LIKE 'A-HU25-DEMO-%'
AND NOT EXISTS (SELECT 1 FROM execucoes_instrumentos x WHERE x.execucao_id=e.id AND x.instrumento_id=i.id);

INSERT INTO medicoes_ensaios (execucao_id,codigo,nome,posicao,unidade,metodo,limite_referencia,tipo_resultado,quantidade,resultado_textual)
SELECT e.id, seed.codigo, seed.nome, seed.posicao, seed.unidade, NULL, NULL, seed.kind, seed.quantidade, seed.texto
FROM (VALUES
 ('A-HU25-DEMO-001/2026','neutralization-index','Índice de Neutralização',0,'mg KOH/g','quantity',CAST(0 AS NUMERIC),NULL),
 ('A-HU25-DEMO-001/2026','water-content','Teor de Água',1,'ppm','not-performed',NULL,NULL),
 ('A-HU25-DEMO-001/2026','density','Densidade a 20/4°C',2,'g/mL','quantity',CAST(0.87 AS NUMERIC),NULL),
 ('A-HU25-DEMO-001/2026','power-factor-100c','Fator de Potência a 100°C',3,'%','not-performed',NULL,NULL),
 ('A-HU25-DEMO-001/2026','dielectric-strength','Rigidez Dielétrica',4,'kV','not-performed',NULL,NULL),
 ('A-HU25-DEMO-001/2026','interfacial-tension','Tensão Interfacial',5,'dina/cm','not-performed',NULL,NULL)
) seed(sample_code,codigo,nome,posicao,unidade,kind,quantidade,texto)
JOIN execucoes_ensaios e ON e.amostra_id=(SELECT a.id FROM amostras a WHERE a.codigo=seed.sample_code) AND e.tipo_analise='physical-chemical'
WHERE NOT EXISTS (SELECT 1 FROM medicoes_ensaios m WHERE m.execucao_id=e.id AND m.codigo=seed.codigo);

INSERT INTO medicoes_ensaios (execucao_id,codigo,nome,posicao,unidade,metodo,limite_referencia,tipo_resultado,quantidade,resultado_textual)
SELECT e.id, seed.codigo, seed.nome, seed.posicao, seed.unidade, NULL, NULL, seed.kind, seed.quantidade, seed.texto
FROM (VALUES
 ('h2','Hidrogênio',0,'ppm','quantity',CAST(12.3 AS NUMERIC),NULL),
 ('o2','Oxigênio',1,'ppm','not-detected',NULL,NULL),
 ('n2','Nitrogênio',2,'ppm','not-performed',NULL,NULL),
 ('ch4','Metano',3,'ppm','text',NULL,'0,01%'),
 ('co','Monóxido de Carbono',4,'ppm','not-performed',NULL,NULL),
 ('co2','Dióxido de Carbono',5,'ppm','not-performed',NULL,NULL),
 ('c2h4','Etileno',6,'ppm','not-performed',NULL,NULL),
 ('c2h6','Etano',7,'ppm','not-performed',NULL,NULL),
 ('c2h2','Acetileno',8,'ppm','not-performed',NULL,NULL),
 ('combustible-gases','Total de Gases Combustíveis',9,'ppm','not-performed',NULL,NULL),
 ('total-gases','Total de Gases',10,'ppm','not-performed',NULL,NULL)
) seed(codigo,nome,posicao,unidade,kind,quantidade,texto)
JOIN execucoes_ensaios e ON e.tipo_analise='chromatography' AND e.amostra_id=(SELECT a.id FROM amostras a WHERE a.codigo='A-HU25-DEMO-001/2026')
WHERE NOT EXISTS (SELECT 1 FROM medicoes_ensaios m WHERE m.execucao_id=e.id AND m.codigo=seed.codigo);

INSERT INTO medicoes_ensaios (execucao_id,codigo,nome,posicao,unidade,metodo,limite_referencia,tipo_resultado,quantidade,resultado_textual)
SELECT e.id,'tensile-strength','Resistência à tração',0,'MPa',NULL,NULL,'text',NULL,'0,01%'
FROM execucoes_ensaios e JOIN amostras a ON a.id=e.amostra_id
WHERE a.codigo='A-HU25-DEMO-003/2026' AND e.tipo_analise IS NULL
AND NOT EXISTS (SELECT 1 FROM medicoes_ensaios m WHERE m.execucao_id=e.id AND m.codigo='tensile-strength');
