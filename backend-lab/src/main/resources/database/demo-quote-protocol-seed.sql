-- Atribui protocolos demonstrativos distintos aos orçamentos de exemplo já carregados.
UPDATE orcamentos
SET protocol = 'L-DEMO-2026-S-' || RIGHT(code, 3) || '-X'
WHERE code LIKE 'DEMO-SEED-2026-%'
  AND (protocol IS NULL OR protocol = 'L-DEMO-2026-' || RIGHT(code, 3) || '-X')
  AND NOT EXISTS (
      SELECT 1 FROM relatorios_tecnicos r WHERE r.validated_quote_id = orcamentos.id
  );

UPDATE orcamentos
SET protocol = 'L-DEMO-2026-P-' || RIGHT(code, 3) || '-X'
WHERE code LIKE 'DEMO-PIPE-2026-%'
  AND (protocol IS NULL OR protocol = 'L-DEMO-2026-' || RIGHT(code, 3) || '-X');

UPDATE orcamentos
SET protocol = 'L-DEMO-2026-L-001-X'
WHERE code = 'DEMO-LACTEC-001'
  AND (protocol IS NULL OR protocol = 'L-DEMO-2026-026-X')
  AND NOT EXISTS (
      SELECT 1 FROM relatorios_tecnicos r WHERE r.validated_quote_id = orcamentos.id
  );
