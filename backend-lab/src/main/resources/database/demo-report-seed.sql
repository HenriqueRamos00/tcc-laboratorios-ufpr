-- Relatórios internos pendentes vinculados aos ensaios HU25 já persistidos.
-- Não altera status de ensaio ou de orçamento, nem cria arquivos ou publicação.
INSERT INTO relatorios_tecnicos (
    execucao_id, validation_status, created_at, published, published_at, version
)
SELECT e.id, 'PENDING_VALIDATION', CURRENT_TIMESTAMP, FALSE, NULL, 0
FROM execucoes_ensaios e
JOIN amostras a ON a.id = e.amostra_id
WHERE a.codigo IN ('A-HU25-DEMO-001/2026', 'A-HU25-DEMO-003/2026')
  AND NOT EXISTS (
      SELECT 1 FROM relatorios_tecnicos r WHERE r.execucao_id = e.id
  );
