-- Decisões demonstrativas para orçamentos que já avançaram do aceite.
-- O orçamento 005 usa ordem de compra; os demais usam o termo preenchido.
INSERT INTO aceites_orcamentos (orcamento_id, cliente_id, method, accepted_at)
SELECT o.id,
       cliente.id,
       CASE WHEN o.code = 'DEMO-PIPE-2026-005' THEN 'ORDEM_DE_COMPRA'
            ELSE 'TERMO_DE_ACEITE'
       END,
       o.created_date
FROM orcamentos o
JOIN usuarios usuario
  ON usuario.email_normalizado = 'demo.cliente.mariana@example.test'
 AND usuario.perfil = 'CLIENTE'
JOIN clientes cliente ON cliente.usuario_id = usuario.id
WHERE o.code LIKE 'DEMO-PIPE-2026-%'
  AND o.stage IN (
      'Aprovado pelo cliente',
      'Aguardando entrega da amostra',
      'Recebido',
      'Protocolado',
      'Em execução',
      'Completo',
      'Validado',
      'Finalizado',
      'Elaborando relatório',
      'Relatório publicado'
  )
  AND NOT EXISTS (
      SELECT 1 FROM aceites_orcamentos existente WHERE existente.orcamento_id = o.id
  );

INSERT INTO termos_aceite_orcamentos (
    aceite_id, company, cnpj, state_registration, street, city, state, district,
    postal_code, contact, phone, fax, invoice_emails, requester_name,
    responsible_name, job_title, department, approval_date,
    allows_results_by_email, results_email
)
SELECT aceite.orcamento_id,
       empresa.legal_name,
       empresa.cnpj,
       NULL,
       empresa.address,
       empresa.city,
       empresa.state,
       'Centro',
       '80000-000',
       orcamento.external_contact_name,
       empresa.contact_phone,
       NULL,
       orcamento.external_contact_email,
       'Mariana Costa',
       'Mariana Costa',
       'Engenheira de qualidade',
       'Qualidade',
       CAST(aceite.accepted_at AS DATE),
       FALSE,
       NULL
FROM aceites_orcamentos aceite
JOIN orcamentos orcamento ON orcamento.id = aceite.orcamento_id
JOIN empresas empresa ON empresa.id = orcamento.empresa_id
WHERE aceite.method = 'TERMO_DE_ACEITE'
  AND orcamento.code LIKE 'DEMO-PIPE-2026-%'
  AND NOT EXISTS (
      SELECT 1 FROM termos_aceite_orcamentos existente WHERE existente.aceite_id = aceite.orcamento_id
  );
