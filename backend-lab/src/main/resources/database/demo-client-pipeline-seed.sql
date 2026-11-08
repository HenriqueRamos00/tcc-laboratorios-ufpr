-- Carteira complementar de demonstração do acompanhamento do cliente.
-- Todos os registros pertencem às empresas ativas vinculadas à cliente Mariana.
INSERT INTO orcamentos (
    empresa_id, code, name, status, stage, description, laboratory_name, protocol, scope,
    external_contact_name, external_contact_email, total_price, created_date
)
SELECT empresa.id, seed.code, seed.name, seed.status, seed.stage, seed.description,
       seed.laboratory_name, 'L-DEMO-2026-P-' || RIGHT(seed.code, 3) || '-X',
       seed.scope, seed.contact_name, seed.contact_email,
       seed.total_price, seed.created_date
FROM (VALUES
    ('98765432000198', 'DEMO-PIPE-2026-001', 'Óleo isolante - avaliação inicial do lote 06', 'PendingAcceptance', 'Qualificação', 'Triagem de demanda para avaliação de óleo isolante de transformador.', 'Laboratório de Óleos e Combustíveis', 'Definição preliminar dos parâmetros físico-químicos.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 6800.00, TIMESTAMP WITH TIME ZONE '2026-10-28 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-002', 'Compósito estrutural - lote piloto 05', 'PendingAcceptance', 'Em análise pela área', 'Avaliação de viabilidade técnica para corpos de prova compósitos.', 'Laboratório de Materiais e Polímeros', 'Análise de requisitos e definição do método de ensaio.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 9200.00, TIMESTAMP WITH TIME ZONE '2026-10-27 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-003', 'Óleo isolante - transformador TR-18', 'PendingAcceptance', 'Elaborando proposta', 'Proposta técnica para diagnóstico periódico de óleo isolante.', 'Laboratório de Óleos e Combustíveis', 'Teor de água, acidez e tensão interfacial.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 7400.00, TIMESTAMP WITH TIME ZONE '2026-10-26 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-004', 'Painel compósito - inspeção de recebimento', 'PendingAcceptance', 'Em negociação', 'Ensaios de recebimento de painéis compósitos para uso industrial.', 'Laboratório de Materiais e Polímeros', 'Tração e flexão em corpos de prova do lote.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 11800.00, TIMESTAMP WITH TIME ZONE '2026-10-25 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-005', 'Óleo isolante - monitoramento semestral', 'InProgress', 'Aprovado pelo cliente', 'Monitoramento semestral do óleo isolante de equipamento de potência.', 'Laboratório de Óleos e Combustíveis', 'Pacote de análises físico-químicas e cromatografia.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 14600.00, TIMESTAMP WITH TIME ZONE '2026-10-24 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-006', 'Compósito de carcaça - série B', 'InProgress', 'Aguardando entrega da amostra', 'Caracterização de material compósito da série B antes da produção.', 'Laboratório de Materiais e Polímeros', 'Ensaios mecânicos em corpos de prova fornecidos pela empresa.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 10300.00, TIMESTAMP WITH TIME ZONE '2026-10-23 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-007', 'Óleo isolante - lote de campo 07', 'InProgress', 'Recebido', 'Amostra recebida para análise de rotina de óleo isolante.', 'Laboratório de Óleos e Combustíveis', 'Análise físico-química de amostra de campo.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 6900.00, TIMESTAMP WITH TIME ZONE '2026-10-22 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-008', 'Compósito laminado - corpos de prova C12', 'InProgress', 'Protocolado', 'Lote de corpos de prova identificado e protocolado para ensaio.', 'Laboratório de Materiais e Polímeros', 'Verificação de resistência à tração e flexão.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 9600.00, TIMESTAMP WITH TIME ZONE '2026-10-21 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-009', 'Óleo isolante - unidade móvel U-04', 'InProgress', 'Em execução', 'Análise laboratorial em andamento para amostra da unidade móvel U-04.', 'Laboratório de Óleos e Combustíveis', 'Determinação de teor de água e acidez.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 7100.00, TIMESTAMP WITH TIME ZONE '2026-10-20 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-010', 'Compósito de suporte - lote industrial 09', 'InProgress', 'Completo', 'Ensaios concluídos para validação do lote industrial de suportes.', 'Laboratório de Materiais e Polímeros', 'Medição de propriedades mecânicas conforme escopo aprovado.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 11100.00, TIMESTAMP WITH TIME ZONE '2026-10-19 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-011', 'Óleo isolante - unidade TR-22', 'InProgress', 'Validado', 'Resultados revisados por responsável técnico para unidade TR-22.', 'Laboratório de Óleos e Combustíveis', 'Análise físico-química e revisão técnica dos resultados.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 8200.00, TIMESTAMP WITH TIME ZONE '2026-10-18 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-012', 'Compósito de proteção - lote 31', 'InProgress', 'Finalizado', 'Execução laboratorial finalizada e pronta para preparação do relatório.', 'Laboratório de Materiais e Polímeros', 'Ensaio de tração em corpos de prova do lote 31.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 8900.00, TIMESTAMP WITH TIME ZONE '2026-10-17 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-013', 'Óleo isolante - conjunto TR-24', 'InProgress', 'Elaborando relatório', 'Resultados aprovados e relatório técnico em preparação.', 'Laboratório de Óleos e Combustíveis', 'Análise de rotina com emissão de relatório técnico.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 7600.00, TIMESTAMP WITH TIME ZONE '2026-10-16 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-014', 'Compósito de cobertura - lote 14', 'Finished', 'Relatório publicado', 'Relatório técnico publicado após revisão dos ensaios do lote 14.', 'Laboratório de Materiais e Polímeros', 'Caracterização mecânica e emissão de laudo.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 12400.00, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00'),
    ('98765432000198', 'DEMO-PIPE-2026-015', 'Óleo isolante - solicitação suspensa', 'Cancelled', 'Cancelado', 'Solicitação cancelada antes do recebimento da amostra.', 'Laboratório de Óleos e Combustíveis', 'Avaliação de óleo isolante não iniciada.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 5300.00, TIMESTAMP WITH TIME ZONE '2026-10-14 09:00:00-03:00'),
    ('87654321000198', 'DEMO-PIPE-2026-016', 'Compósito de carenagem - consulta técnica', 'Refused', 'Recusado', 'Solicitação recusada após análise de escopo e disponibilidade técnica.', 'Laboratório de Materiais e Polímeros', 'Consulta para ensaio de material compósito.', 'Mariana Costa', 'demo.cliente.mariana@example.test', 6700.00, TIMESTAMP WITH TIME ZONE '2026-10-13 09:00:00-03:00')
) AS seed(cnpj, code, name, status, stage, description, laboratory_name, scope,
          contact_name, contact_email, total_price, created_date)
JOIN empresas empresa ON empresa.cnpj = seed.cnpj AND empresa.ativo = TRUE
WHERE NOT EXISTS (SELECT 1 FROM orcamentos existing WHERE existing.code = seed.code);

-- Cada orçamento demonstrativo recebe um item cujo valor fecha o total persistido.
INSERT INTO itens_orcamento (orcamento_id, position, test_type, sample_count, standard, unit_price)
SELECT o.id, 1, seed.test_type, 1, seed.standard, o.total_price
FROM (VALUES
    ('DEMO-PIPE-2026-001', 'Triagem físico-química de óleo', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-002', 'Avaliação de compósito', 'ASTM D3039'),
    ('DEMO-PIPE-2026-003', 'Teor de água em óleo isolante', 'ASTM D1533'),
    ('DEMO-PIPE-2026-004', 'Ensaio mecânico de painel compósito', 'ASTM D7264'),
    ('DEMO-PIPE-2026-005', 'Pacote de análises de óleo isolante', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-006', 'Caracterização de compósito', 'ASTM D3039'),
    ('DEMO-PIPE-2026-007', 'Análise de óleo de campo', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-008', 'Ensaio de corpos de prova compósitos', 'ASTM D3039'),
    ('DEMO-PIPE-2026-009', 'Análise de óleo isolante', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-010', 'Ensaio de tração em compósito', 'ASTM D3039'),
    ('DEMO-PIPE-2026-011', 'Análise físico-química de óleo', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-012', 'Ensaio de tração em compósito', 'ASTM D3039'),
    ('DEMO-PIPE-2026-013', 'Análise de óleo e relatório técnico', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-014', 'Ensaio de flexão em compósito', 'ASTM D7264'),
    ('DEMO-PIPE-2026-015', 'Avaliação de óleo isolante', 'ABNT NBR 10576'),
    ('DEMO-PIPE-2026-016', 'Consulta de ensaio de compósito', 'ASTM D3039')
) AS seed(code, test_type, standard)
JOIN orcamentos o ON o.code = seed.code
WHERE NOT EXISTS (
    SELECT 1 FROM itens_orcamento item
    WHERE item.orcamento_id = o.id AND item.position = 1
);

-- As etapas técnicas possuem amostra e execução com o estado técnico correspondente.
INSERT INTO amostras (codigo, descricao, orcamento_id)
SELECT 'A-PIPE-' || o.code, 'Amostra demonstrativa - ' || o.name, o.id
FROM orcamentos o
WHERE o.code IN ('DEMO-PIPE-2026-007', 'DEMO-PIPE-2026-008', 'DEMO-PIPE-2026-009',
                 'DEMO-PIPE-2026-010', 'DEMO-PIPE-2026-011',
                 'DEMO-PIPE-2026-012', 'DEMO-PIPE-2026-013', 'DEMO-PIPE-2026-014')
  AND NOT EXISTS (SELECT 1 FROM amostras a WHERE a.codigo = 'A-PIPE-' || o.code);

INSERT INTO execucoes_ensaios (codigo, amostra_id, orcamento_id, responsavel_id,
                              tipo, metodo, norma, data_agendada, status,
                              report_reference, published_at, version, atualizado_em)
SELECT 'E-' || seed.code || '-' || seed.status,
       amostra.id, orcamento.id, tecnico.id, seed.tipo, seed.metodo, seed.norma,
       DATE '2026-10-30', seed.status, seed.report_reference, seed.published_at,
       0, TIMESTAMP WITH TIME ZONE '2026-10-30 09:00:00-03:00'
FROM (VALUES
    ('DEMO-PIPE-2026-009', 'incomplete', 'Análise de óleo isolante', 'Análise físico-química', 'ABNT NBR 10576', NULL, NULL),
    ('DEMO-PIPE-2026-010', 'complete', 'Ensaio de compósito', 'Ensaio de tração', 'ASTM D3039', NULL, NULL),
    ('DEMO-PIPE-2026-011', 'validated', 'Análise de óleo isolante', 'Análise físico-química', 'ABNT NBR 10576', NULL, NULL),
    ('DEMO-PIPE-2026-012', 'finalized', 'Ensaio de compósito', 'Ensaio de tração', 'ASTM D3039', NULL, NULL),
    ('DEMO-PIPE-2026-013', 'drafting-report', 'Análise de óleo isolante', 'Análise físico-química', 'ABNT NBR 10576', NULL, NULL),
    ('DEMO-PIPE-2026-014', 'report-published', 'Ensaio de compósito', 'Ensaio de flexão', 'ASTM D7264', 'LAUDO-PIPE-2026-014-A', TIMESTAMP WITH TIME ZONE '2026-10-29 15:30:00-03:00')
) AS seed(code, status, tipo, metodo, norma, report_reference, published_at)
JOIN orcamentos orcamento ON orcamento.code = seed.code
JOIN amostras amostra ON amostra.codigo = 'A-PIPE-' || orcamento.code
JOIN usuarios tecnico ON tecnico.email_normalizado = 'demo.tecnico.helena@example.test' AND tecnico.perfil = 'TECNICO'
WHERE NOT EXISTS (
    SELECT 1 FROM execucoes_ensaios existente
    WHERE existente.codigo = 'E-' || seed.code || '-' || seed.status
);
