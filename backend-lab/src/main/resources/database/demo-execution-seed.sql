-- Execuções de demonstração para testar os status técnicos dos orçamentos.
-- Uma execução publicada sem data deve aparecer como Validado na consulta.
INSERT INTO execucoes_ensaios (orcamento_id, status, report_reference, published_at)
SELECT orcamento.id, seed.status, seed.report_reference, seed.published_at
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
WHERE NOT EXISTS (
    SELECT 1
    FROM execucoes_ensaios existente
    WHERE existente.orcamento_id = orcamento.id
);
