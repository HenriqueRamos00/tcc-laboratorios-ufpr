-- Cria rascunhos HU25 e relatórios para ensaios de demonstração marcados como publicados.
-- O runner valida e publica estes últimos usando os casos de uso da aplicação.
INSERT INTO relatorios_tecnicos (
    execucao_id, validation_status, created_at, published, published_at, version
)
SELECT e.id, 'PENDING_VALIDATION', CURRENT_TIMESTAMP, FALSE, NULL, 0
FROM execucoes_ensaios e
JOIN amostras a ON a.id = e.amostra_id
WHERE (
       a.codigo IN ('A-HU25-DEMO-001/2026', 'A-HU25-DEMO-003/2026')
       OR (e.status = 'report-published'
           AND e.report_reference IN ('LAUDO-DEMO-2026-005-A', 'LAUDO-DEMO-2026-009-A'))
  )
  AND NOT EXISTS (
      SELECT 1 FROM relatorios_tecnicos r WHERE r.execucao_id = e.id
  );
