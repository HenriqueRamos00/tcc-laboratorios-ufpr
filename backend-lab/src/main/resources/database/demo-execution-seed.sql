-- Execuções de demonstração para testar os status técnicos dos orçamentos.
-- Uma execução publicada sem data deve aparecer como Validado na consulta.
INSERT INTO amostras (codigo, descricao, orcamento_id)
SELECT 'A-TEC-' || o.code, 'Amostra demonstrativa da consulta técnica', o.id
FROM orcamentos o
WHERE o.code IN ('DEMO-SEED-2026-001', 'DEMO-SEED-2026-002', 'DEMO-SEED-2026-003',
                 'DEMO-SEED-2026-004', 'DEMO-SEED-2026-005', 'DEMO-SEED-2026-006',
                 'DEMO-SEED-2026-007', 'DEMO-SEED-2026-008', 'DEMO-SEED-2026-009',
                 'DEMO-SEED-2026-010')
  AND NOT EXISTS (SELECT 1 FROM amostras a WHERE a.codigo = 'A-TEC-' || o.code);

INSERT INTO execucoes_ensaios (codigo, amostra_id, orcamento_id, responsavel_id,
                              tipo, metodo, norma, data_agendada, status,
                              report_reference, published_at, version, atualizado_em)
SELECT REPLACE(seed.code, 'DEMO-SEED-2026-', 'E-DEMO-') || '-' || seed.status,
       amostra.id, orcamento.id, tecnico.id, 'Análise demonstrativa de óleo',
       'Análise físico-química', 'ABNT NBR 10576', DATE '2026-10-24',
       seed.status, seed.report_reference, seed.published_at, 0,
       TIMESTAMP WITH TIME ZONE '2026-10-25 09:00:00-03:00'
FROM (VALUES
    ('DEMO-SEED-2026-001', 'incomplete', NULL, NULL),
    ('DEMO-SEED-2026-002', 'complete', NULL, NULL),
    ('DEMO-SEED-2026-003', 'validated', NULL, NULL),
    ('DEMO-SEED-2026-004', 'finalized', NULL, NULL),
    ('DEMO-SEED-2026-004', 'drafting-report', NULL, NULL),
    ('DEMO-SEED-2026-005', 'report-published', 'LAUDO-DEMO-2026-005-A', TIMESTAMP WITH TIME ZONE '2026-10-17 11:00:00-03:00'),
    ('DEMO-SEED-2026-006', 'report-published', 'LAUDO-DEMO-2026-006-A', NULL),
    ('DEMO-SEED-2026-007', 'complete', NULL, NULL),
    ('DEMO-SEED-2026-007', 'cancelled', NULL, NULL),
    ('DEMO-SEED-2026-008', 'cancelled', NULL, NULL),
    ('DEMO-SEED-2026-009', 'report-published', 'LAUDO-DEMO-2026-009-A', TIMESTAMP WITH TIME ZONE '2026-10-09 11:00:00-03:00'),
    ('DEMO-SEED-2026-009', 'cancelled', NULL, NULL),
    ('DEMO-SEED-2026-010', 'incomplete', NULL, NULL),
    ('DEMO-SEED-2026-010', 'complete', NULL, NULL)
) AS seed(code, status, report_reference, published_at)
JOIN orcamentos orcamento ON orcamento.code = seed.code
JOIN amostras amostra ON amostra.codigo = 'A-TEC-' || orcamento.code
JOIN usuarios tecnico ON tecnico.email_normalizado = 'demo.tecnico.helena@example.test' AND tecnico.perfil = 'TECNICO'
WHERE NOT EXISTS (
    SELECT 1
    FROM execucoes_ensaios existente
    WHERE existente.codigo = REPLACE(seed.code, 'DEMO-SEED-2026-', 'E-DEMO-') || '-' || seed.status
);
