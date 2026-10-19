-- Base local de demonstração para as telas administrativas e consultas de cliente.
-- Todas as contas novas usam e-mails reservados .test e nomes identificados como demo.
-- Inserções são condicionais; apenas quatro status antigos de propostas demo são convertidos abaixo.

-- Converte status antigos apenas nas propostas de demonstração que recebiam esses valores.
UPDATE orcamentos SET status = 'PendingAcceptance'
WHERE (code = 'DEMO-SEED-2026-021' AND status = 'Em negociação')
   OR (code = 'DEMO-SEED-2026-022' AND status = 'Aberto')
   OR (code = 'DEMO-LACTEC-001' AND status = 'Em negociação');

UPDATE orcamentos SET status = 'InProgress'
WHERE code = 'DEMO-SEED-2026-023' AND status = 'Em andamento';

INSERT INTO empresas (
    legal_name, cnpj, address, city, state, contact_name, contact_phone, contact_email, ativo, origin
)
SELECT seed.legal_name, seed.cnpj, seed.address, seed.city, seed.state,
       seed.contact_name, seed.contact_phone, seed.contact_email, seed.ativo, 'LOCAL'
FROM (VALUES
    ('Empresa Lactec de demonstração', '11222333000181', 'Rua de demonstração, 1',
     'Curitiba', 'PR', 'Contato Lactec', '41999999999', 'contato@lactec.com', TRUE),
    ('Distribuidora de Energia - Demonstração', '98765432000198', 'Rua das Araucárias, 101',
     'Curitiba', 'PR', 'Contato Distribuidora', '41990001001', 'contato.distribuidora@example.test', TRUE),
    ('Geradora de Energia - Demonstração', '87654321000198', 'Avenida das Usinas, 202',
     'Curitiba', 'PR', 'Contato Geradora', '41990001002', 'contato.geradora@example.test', TRUE),
    ('Refinaria Nacional - Demonstração', '76543210000198', 'Rua do Petróleo, 303',
     'Araucária', 'PR', 'Contato Refinaria', '41990001003', 'contato.refinaria@example.test', FALSE),
    ('Companhia de Saneamento - Demonstração', '65432109000143', 'Rua das Águas, 404',
     'São José dos Pinhais', 'PR', 'Contato Saneamento', '41990001004', 'contato.saneamento@example.test', TRUE),
    ('Indústria de Polímeros - Demonstração', '54321098000108', 'Avenida dos Polímeros, 505',
     'Ponta Grossa', 'PR', 'Contato Polímeros', '42990001005', 'contato.polimeros@example.test', FALSE)
) AS seed(legal_name, cnpj, address, city, state, contact_name, contact_phone, contact_email, ativo)
WHERE NOT EXISTS (SELECT 1 FROM empresas e WHERE e.cnpj = seed.cnpj);

-- Mantém a conta de demonstração anterior sem redefinir senha, dados ou situação.
INSERT INTO usuarios (nome, email, email_normalizado, senha_hash, perfil, ativo)
SELECT 'Cliente Lactec', 'lactec@lactec.com', 'lactec@lactec.com',
       '$2y$10$DNdvFeSbdH8589xdrlFz5OZLuVhExy8hVAbVBwlMCY0U80ORxFd7K', 'CLIENTE', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios u WHERE lower(trim(u.email)) = 'lactec@lactec.com'
);

INSERT INTO clientes (usuario_id, phone, position)
SELECT u.id, '41999999999', 'Responsável'
FROM usuarios u
WHERE lower(trim(u.email)) = 'lactec@lactec.com'
  AND u.perfil = 'CLIENTE'
  AND u.senha_hash = '$2y$10$DNdvFeSbdH8589xdrlFz5OZLuVhExy8hVAbVBwlMCY0U80ORxFd7K'
  AND u.nome = 'Cliente Lactec'
  AND NOT EXISTS (SELECT 1 FROM clientes c WHERE c.usuario_id = u.id);

-- Senha compartilhada apenas pelas contas novas desta base: Demo@12345.
INSERT INTO usuarios (
    nome, email, email_normalizado, senha_hash, perfil, ativo, unidade, especialidade
)
SELECT seed.nome, seed.email, lower(trim(seed.email)), seed.senha_hash, seed.perfil,
       seed.ativo, seed.unidade, seed.especialidade
FROM (VALUES
    ('Demo Técnico - Helena Souza', 'demo.tecnico.helena@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', TRUE, 'DOLEO', 'Cromatografia (DGA)'),
    ('Demo Técnico - Marcelo Wagner', 'demo.tecnico.marcelo@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', TRUE, 'DIMAT', 'Materiais compósitos'),
    ('Demo Técnico - Paulo Lima', 'demo.tecnico.paulo@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', TRUE, 'DEMA', 'Ensaios mecânicos'),
    ('Demo Técnico - Camila Rocha', 'demo.tecnico.camila@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', TRUE, 'DOLEO', 'Análise físico-química de óleo'),
    ('Demo Técnico - Ricardo Alves', 'demo.tecnico.ricardo@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', FALSE, 'DEQA', 'Análise de água'),
    ('Demo Técnico - Ana Martins', 'demo.tecnico.ana@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'TECNICO', FALSE, 'DIMAT', 'Caracterização de polímeros'),
    ('Demo Cliente - Mariana Costa', 'demo.cliente.mariana@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', TRUE, NULL, NULL),
    ('Demo Cliente - Rafael Mendes', 'demo.cliente.rafael@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', TRUE, NULL, NULL),
    ('Demo Cliente - Juliana Pires', 'demo.cliente.juliana@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', TRUE, NULL, NULL),
    ('Demo Cliente - Bruno Pereira', 'demo.cliente.bruno@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', TRUE, NULL, NULL),
    ('Demo Cliente - Camila Souza', 'demo.cliente.camila@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', TRUE, NULL, NULL),
    ('Demo Cliente - Angelo Ribeiro', 'demo.cliente.angelo@example.test',
     '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.', 'CLIENTE', FALSE, NULL, NULL)
) AS seed(nome, email, senha_hash, perfil, ativo, unidade, especialidade)
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios u WHERE lower(trim(u.email)) = lower(trim(seed.email))
);

INSERT INTO clientes (usuario_id, phone, position)
SELECT u.id, seed.phone, seed.position
FROM (VALUES
    ('demo.cliente.mariana@example.test', '41990002001', 'Engenheira de qualidade'),
    ('demo.cliente.rafael@example.test', '41990002002', 'Responsável por manutenção'),
    ('demo.cliente.juliana@example.test', '41990002003', 'Analista ambiental'),
    ('demo.cliente.bruno@example.test', '41990002004', 'Compras'),
    ('demo.cliente.camila@example.test', '41990002005', 'Engenheira química'),
    ('demo.cliente.angelo@example.test', '41990002006', 'Responsável técnico')
) AS seed(email, phone, position)
JOIN usuarios u ON lower(trim(u.email)) = seed.email
WHERE u.perfil = 'CLIENTE'
  AND u.nome LIKE 'Demo Cliente - %'
  AND u.senha_hash = '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.'
  AND NOT EXISTS (SELECT 1 FROM clientes c WHERE c.usuario_id = u.id);

-- Vínculos de demonstração são explícitos por e-mail .test e CNPJ de demonstração.
INSERT INTO cliente_empresas (cliente_id, empresa_id)
SELECT c.id, e.id
FROM (VALUES
    ('demo.cliente.mariana@example.test', '98765432000198'),
    ('demo.cliente.mariana@example.test', '87654321000198'),
    ('demo.cliente.rafael@example.test', '76543210000198'),
    ('demo.cliente.juliana@example.test', '65432109000143'),
    ('demo.cliente.juliana@example.test', '54321098000108'),
    ('demo.cliente.bruno@example.test', '87654321000198'),
    ('demo.cliente.bruno@example.test', '65432109000143'),
    ('demo.cliente.camila@example.test', '98765432000198'),
    ('demo.cliente.angelo@example.test', '54321098000108')
) AS seed(email, cnpj)
JOIN usuarios u ON lower(trim(u.email)) = seed.email
JOIN clientes c ON c.usuario_id = u.id
JOIN empresas e ON e.cnpj = seed.cnpj
WHERE u.perfil = 'CLIENTE'
  AND u.nome LIKE 'Demo Cliente - %'
  AND u.senha_hash = '$2b$12$ZO84sHp9SxnsJKc./08n3OjAt7ihISuOE/KGGpoCJUvorDEfmJgv.'
  AND NOT EXISTS (
      SELECT 1 FROM cliente_empresas ce
      WHERE ce.cliente_id = c.id AND ce.empresa_id = e.id
  );

INSERT INTO cliente_empresas (cliente_id, empresa_id)
SELECT c.id, e.id
FROM clientes c
JOIN usuarios u ON u.id = c.usuario_id
JOIN empresas e ON e.cnpj = '11222333000181'
WHERE lower(trim(u.email)) = 'lactec@lactec.com'
  AND u.perfil = 'CLIENTE'
  AND u.senha_hash = '$2y$10$DNdvFeSbdH8589xdrlFz5OZLuVhExy8hVAbVBwlMCY0U80ORxFd7K'
  AND u.nome = 'Cliente Lactec'
  AND NOT EXISTS (
      SELECT 1 FROM cliente_empresas ce
      WHERE ce.cliente_id = c.id AND ce.empresa_id = e.id
  );

-- Os três registros antigos mantêm campos de detalhe nulos e nenhum item.
-- Os demais percorrem os cinco status comerciais e têm laboratório/contato próprios.
INSERT INTO orcamentos (
    empresa_id, code, name, status, stage, description, laboratory_name, scope,
    external_contact_name, external_contact_email, total_price, created_date
)
SELECT e.id, seed.code, seed.name, seed.status, seed.stage, seed.description,
       seed.laboratory_name, seed.scope, seed.external_contact_name,
       seed.external_contact_email, seed.total_price, seed.created_date
FROM (VALUES
    ('98765432000198', 'DEMO-SEED-2026-001', 'Análise de óleo isolante - lote 01',
     'PendingAcceptance', 'Negociação', 'Avaliação de propriedades físico-químicas de amostras de óleo isolante.',
     'Laboratório de Óleos e Combustíveis', 'Caracterização de óleo isolante em duas amostras.',
     'Demo - Camila Souza', 'demo.contato.camila@example.test', 12500.00, TIMESTAMP WITH TIME ZONE '2026-10-17 09:00:00-03:00'),
    ('54321098000108', 'DEMO-SEED-2026-002', 'Análise de polímeros - lote 01',
     'InProgress', 'Execução', 'Caracterização mecânica de material polimérico para controle de qualidade.',
     'Laboratório de Materiais e Polímeros', 'Ensaios de tração e flexão em corpos de prova.',
     'Demo - Bruno Pereira', 'demo.contato.bruno@example.test', 56292.30, TIMESTAMP WITH TIME ZONE '2026-10-16 09:00:00-03:00'),
    ('65432109000143', 'DEMO-SEED-2026-003', 'Análise de água potável - lote 01',
     'Finished', 'Relatório Publicado', 'Análise físico-química de amostras de água para demonstração.',
     'Laboratório de Química e Água', 'Medição de parâmetros físico-químicos da amostra.',
     'Demo - Juliana Pires', 'demo.contato.juliana@example.test', 8500.00, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00'),
    ('87654321000198', 'DEMO-SEED-2026-004', 'Ensaio de materiais compósitos - lote 01',
     'Refused', 'Fechado Perdido', 'Avaliação de propriedades mecânicas de material compósito.',
     'Laboratório de Materiais e Polímeros', 'Ensaios mecânicos em corpos de prova fornecidos.',
     'Demo - Mariana Costa', 'demo.contato.mariana@example.test', 18200.00, TIMESTAMP WITH TIME ZONE '2026-10-14 09:00:00-03:00'),
    ('76543210000198', 'DEMO-SEED-2026-005', 'Qualidade do ar - ciclo 01',
     'Cancelled', 'Fechado Perdido', 'Monitoramento demonstrativo de qualidade do ar em ponto fixo.',
     'Laboratório de Química Ambiental', 'Coleta e avaliação de amostras em dois ciclos.',
     'Demo - Rafael Mendes', 'demo.contato.rafael@example.test', 12400.00, TIMESTAMP WITH TIME ZONE '2026-10-13 09:00:00-03:00'),
    ('98765432000198', 'DEMO-SEED-2026-006', 'Análise de óleo isolante - lote 02',
     'PendingAcceptance', 'Negociação', 'Avaliação complementar de propriedades de óleo isolante.',
     'Laboratório de Óleos e Combustíveis', 'Comparação de duas amostras coletadas em períodos distintos.',
     'Demo - Camila Souza', 'demo.contato.camila@example.test', 12500.00, TIMESTAMP WITH TIME ZONE '2026-10-12 09:00:00-03:00'),
    ('54321098000108', 'DEMO-SEED-2026-007', 'Análise de polímeros - lote 02',
     'InProgress', 'Execução', 'Caracterização de polímeros com avaliação de resistência e flexão.',
     'Laboratório de Materiais e Polímeros', 'Ensaios mecânicos de controle do lote.',
     'Demo - Bruno Pereira', 'demo.contato.bruno@example.test', 56292.30, TIMESTAMP WITH TIME ZONE '2026-10-11 09:00:00-03:00'),
    ('65432109000143', 'DEMO-SEED-2026-008', 'Análise de água potável - lote 02',
     'Finished', 'Relatório Publicado', 'Análise físico-química demonstrativa de água potável.',
     'Laboratório de Química e Água', 'Avaliação de pH e sólidos dissolvidos.',
     'Demo - Juliana Pires', 'demo.contato.juliana@example.test', 8500.00, TIMESTAMP WITH TIME ZONE '2026-10-10 09:00:00-03:00'),
    ('87654321000198', 'DEMO-SEED-2026-009', 'Materiais compósitos - lote 02',
     'Refused', 'Fechado Perdido', 'Ensaio de resistência em material compósito de demonstração.',
     'Laboratório de Materiais e Polímeros', 'Avaliação de tração e flexão em corpos de prova.',
     'Demo - Mariana Costa', 'demo.contato.mariana@example.test', 18200.00, TIMESTAMP WITH TIME ZONE '2026-10-09 09:00:00-03:00'),
    ('76543210000198', 'DEMO-SEED-2026-010', 'Qualidade do ar - ciclo 02',
     'Cancelled', 'Fechado Perdido', 'Monitoramento demonstrativo de partículas em suspensão.',
     'Laboratório de Química Ambiental', 'Coleta e avaliação em um ponto de monitoramento.',
     'Demo - Rafael Mendes', 'demo.contato.rafael@example.test', 12400.00, TIMESTAMP WITH TIME ZONE '2026-10-08 09:00:00-03:00'),
    ('98765432000198', 'DEMO-SEED-2026-011', 'Análise de óleo isolante - lote 03',
     'PendingAcceptance', 'Negociação', 'Caracterização de óleo isolante para acompanhamento periódico.',
     'Laboratório de Óleos e Combustíveis', 'Análises físico-químicas de amostras do lote.',
     'Demo - Camila Souza', 'demo.contato.camila@example.test', 12500.00, TIMESTAMP WITH TIME ZONE '2026-10-07 09:00:00-03:00'),
    ('54321098000108', 'DEMO-SEED-2026-012', 'Análise de polímeros - lote 03',
     'InProgress', 'Execução', 'Ensaios de resistência em material polimérico.',
     'Laboratório de Materiais e Polímeros', 'Caracterização mecânica de corpos de prova.',
     'Demo - Bruno Pereira', 'demo.contato.bruno@example.test', 56292.30, TIMESTAMP WITH TIME ZONE '2026-10-06 09:00:00-03:00'),
    ('65432109000143', 'DEMO-SEED-2026-013', 'Análise de água potável - lote 03',
     'Finished', 'Relatório Publicado', 'Análise demonstrativa de amostras de água para consumo.',
     'Laboratório de Química e Água', 'Avaliação de parâmetros físico-químicos.',
     'Demo - Juliana Pires', 'demo.contato.juliana@example.test', 8500.00, TIMESTAMP WITH TIME ZONE '2026-10-05 09:00:00-03:00'),
    ('87654321000198', 'DEMO-SEED-2026-014', 'Materiais compósitos - lote 03',
     'Refused', 'Fechado Perdido', 'Avaliação de propriedades de material compósito.',
     'Laboratório de Materiais e Polímeros', 'Ensaios mecânicos em amostras do lote.',
     'Demo - Mariana Costa', 'demo.contato.mariana@example.test', 18200.00, TIMESTAMP WITH TIME ZONE '2026-10-04 09:00:00-03:00'),
    ('76543210000198', 'DEMO-SEED-2026-015', 'Qualidade do ar - ciclo 03',
     'Cancelled', 'Fechado Perdido', 'Monitoramento de partículas e compostos em amostra ambiental.',
     'Laboratório de Química Ambiental', 'Coleta demonstrativa para análise complementar.',
     'Demo - Rafael Mendes', 'demo.contato.rafael@example.test', 12400.00, TIMESTAMP WITH TIME ZONE '2026-10-03 09:00:00-03:00'),
    ('98765432000198', 'DEMO-SEED-2026-016', 'Análise de óleo isolante - lote 04',
     'PendingAcceptance', 'Negociação', 'Avaliação comparativa de amostras de óleo isolante.',
     'Laboratório de Óleos e Combustíveis', 'Análise de parâmetros do lote e amostra de referência.',
     'Demo - Camila Souza', 'demo.contato.camila@example.test', 12500.00, TIMESTAMP WITH TIME ZONE '2026-10-02 09:00:00-03:00'),
    ('54321098000108', 'DEMO-SEED-2026-017', 'Análise de polímeros - lote 04',
     'InProgress', 'Execução', 'Caracterização de material polimérico de demonstração.',
     'Laboratório de Materiais e Polímeros', 'Ensaios de tração e flexão em amostras.',
     'Demo - Bruno Pereira', 'demo.contato.bruno@example.test', 56292.30, TIMESTAMP WITH TIME ZONE '2026-10-01 09:00:00-03:00'),
    ('65432109000143', 'DEMO-SEED-2026-018', 'Análise de água potável - lote 04',
     'Finished', 'Relatório Publicado', 'Avaliação físico-química de amostras de água.',
     'Laboratório de Química e Água', 'Medição dos parâmetros definidos para o lote.',
     'Demo - Juliana Pires', 'demo.contato.juliana@example.test', 8500.00, TIMESTAMP WITH TIME ZONE '2026-09-30 09:00:00-03:00'),
    ('87654321000198', 'DEMO-SEED-2026-019', 'Materiais compósitos - lote 04',
     'Refused', 'Fechado Perdido', 'Caracterização mecânica demonstrativa de material compósito.',
     'Laboratório de Materiais e Polímeros', 'Ensaios de resistência em corpos de prova.',
     'Demo - Mariana Costa', 'demo.contato.mariana@example.test', 18200.00, TIMESTAMP WITH TIME ZONE '2026-09-29 09:00:00-03:00'),
    ('76543210000198', 'DEMO-SEED-2026-020', 'Qualidade do ar - ciclo 04',
     'Cancelled', 'Fechado Perdido', 'Análise demonstrativa de amostras de qualidade do ar.',
     'Laboratório de Química Ambiental', 'Avaliação de partículas e compostos voláteis.',
     'Demo - Rafael Mendes', 'demo.contato.rafael@example.test', 12400.00, TIMESTAMP WITH TIME ZONE '2026-09-28 09:00:00-03:00'),
    ('65432109000143', 'DEMO-SEED-2026-021', 'Registro anterior - análise de água',
     'PendingAcceptance', 'Em negociação', 'Registro antigo usado para demonstrar campos de detalhe ausentes.',
     NULL, NULL, NULL, NULL, 8500.00, TIMESTAMP WITH TIME ZONE '2026-09-27 09:00:00-03:00'),
    ('87654321000198', 'DEMO-SEED-2026-022', 'Registro anterior - materiais compósitos',
     'PendingAcceptance', 'Qualificação', 'Registro antigo sem laboratório, escopo, contato próprio ou itens.',
     NULL, NULL, NULL, NULL, 18200.00, TIMESTAMP WITH TIME ZONE '2026-09-26 09:00:00-03:00'),
    ('76543210000198', 'DEMO-SEED-2026-023', 'Registro anterior - qualidade do ar',
     'InProgress', 'Laboratório', 'Orçamento anterior com campos opcionais não persistidos.',
     NULL, NULL, NULL, NULL, 12400.00, TIMESTAMP WITH TIME ZONE '2026-09-25 09:00:00-03:00'),
    ('98765432000198', 'DEMO-SEED-2026-024', 'Análise de óleo isolante - lote 05',
     'PendingAcceptance', 'Negociação', 'Avaliação de propriedades físico-químicas de óleo isolante.',
     'Laboratório de Óleos e Combustíveis', 'Análise de duas amostras de óleo.',
     'Demo - Camila Souza', 'demo.contato.camila@example.test', 12500.00, TIMESTAMP WITH TIME ZONE '2026-09-24 09:00:00-03:00'),
    ('54321098000108', 'DEMO-SEED-2026-025', 'Análise de polímeros - lote 05',
     'InProgress', 'Execução', 'Ensaio mecânico demonstrativo de material polimérico.',
     'Laboratório de Materiais e Polímeros', 'Ensaios de tração e flexão do material.',
     'Demo - Bruno Pereira', 'demo.contato.bruno@example.test', 56292.30, TIMESTAMP WITH TIME ZONE '2026-09-23 09:00:00-03:00')
) AS seed(cnpj, code, name, status, stage, description, laboratory_name, scope,
          external_contact_name, external_contact_email, total_price, created_date)
JOIN empresas e ON e.cnpj = seed.cnpj
WHERE NOT EXISTS (SELECT 1 FROM orcamentos existing WHERE existing.code = seed.code);

-- Os itens usam preços de referência das propostas demonstrativas e somam o total persistido.
INSERT INTO itens_orcamento (orcamento_id, position, test_type, sample_count, standard, unit_price)
SELECT o.id, 1, seed.test_type, 1, seed.standard, seed.unit_price
FROM (VALUES
    ('DEMO-SEED-2026-001', 'Teor de água', 'ASTM D1533', 6250.00),
    ('DEMO-SEED-2026-002', 'Resistência à tração', 'ASTM D638', 28146.15),
    ('DEMO-SEED-2026-003', 'pH da amostra', 'Standard Methods 4500-H+', 4250.00),
    ('DEMO-SEED-2026-004', 'Tração em corpo de prova', 'ASTM D3039', 9100.00),
    ('DEMO-SEED-2026-005', 'Partículas em suspensão', NULL, 6200.00),
    ('DEMO-SEED-2026-006', 'Teor de água', 'ASTM D1533', 6250.00),
    ('DEMO-SEED-2026-007', 'Resistência à tração', 'ASTM D638', 28146.15),
    ('DEMO-SEED-2026-008', 'pH da amostra', 'Standard Methods 4500-H+', 4250.00),
    ('DEMO-SEED-2026-009', 'Tração em corpo de prova', 'ASTM D3039', 9100.00),
    ('DEMO-SEED-2026-010', 'Partículas em suspensão', NULL, 6200.00),
    ('DEMO-SEED-2026-011', 'Teor de água', 'ASTM D1533', 6250.00),
    ('DEMO-SEED-2026-012', 'Resistência à tração', 'ASTM D638', 28146.15),
    ('DEMO-SEED-2026-013', 'pH da amostra', 'Standard Methods 4500-H+', 4250.00),
    ('DEMO-SEED-2026-014', 'Tração em corpo de prova', 'ASTM D3039', 9100.00),
    ('DEMO-SEED-2026-015', 'Partículas em suspensão', NULL, 6200.00),
    ('DEMO-SEED-2026-016', 'Teor de água', 'ASTM D1533', 6250.00),
    ('DEMO-SEED-2026-017', 'Resistência à tração', 'ASTM D638', 28146.15),
    ('DEMO-SEED-2026-018', 'pH da amostra', 'Standard Methods 4500-H+', 4250.00),
    ('DEMO-SEED-2026-019', 'Tração em corpo de prova', 'ASTM D3039', 9100.00),
    ('DEMO-SEED-2026-020', 'Partículas em suspensão', NULL, 6200.00),
    ('DEMO-SEED-2026-024', 'Teor de água', 'ASTM D1533', 6250.00),
    ('DEMO-SEED-2026-025', 'Resistência à tração', 'ASTM D638', 28146.15)) AS seed(code, test_type, standard, unit_price)
JOIN orcamentos o ON o.code = seed.code
WHERE NOT EXISTS (
    SELECT 1 FROM itens_orcamento existing
    WHERE existing.orcamento_id = o.id AND existing.position = 1
);

INSERT INTO itens_orcamento (orcamento_id, position, test_type, sample_count, standard, unit_price)
SELECT o.id, 2, seed.test_type, 1, seed.standard, seed.unit_price
FROM (VALUES
    ('DEMO-SEED-2026-001', 'Tensão interfacial', 'ASTM D971', 6250.00),
    ('DEMO-SEED-2026-002', 'Flexão de material', 'ASTM D790', 28146.15),
    ('DEMO-SEED-2026-003', 'Sólidos dissolvidos', 'Standard Methods 2540', 4250.00),
    ('DEMO-SEED-2026-004', 'Flexão em material compósito', 'ASTM D7264', 9100.00),
    ('DEMO-SEED-2026-005', 'Compostos voláteis', NULL, 6200.00),
    ('DEMO-SEED-2026-006', 'Tensão interfacial', 'ASTM D971', 6250.00),
    ('DEMO-SEED-2026-007', 'Flexão de material', 'ASTM D790', 28146.15),
    ('DEMO-SEED-2026-008', 'Sólidos dissolvidos', 'Standard Methods 2540', 4250.00),
    ('DEMO-SEED-2026-009', 'Flexão em material compósito', 'ASTM D7264', 9100.00),
    ('DEMO-SEED-2026-010', 'Compostos voláteis', NULL, 6200.00),
    ('DEMO-SEED-2026-011', 'Tensão interfacial', 'ASTM D971', 6250.00),
    ('DEMO-SEED-2026-012', 'Flexão de material', 'ASTM D790', 28146.15),
    ('DEMO-SEED-2026-013', 'Sólidos dissolvidos', 'Standard Methods 2540', 4250.00),
    ('DEMO-SEED-2026-014', 'Flexão em material compósito', 'ASTM D7264', 9100.00),
    ('DEMO-SEED-2026-015', 'Compostos voláteis', NULL, 6200.00),
    ('DEMO-SEED-2026-016', 'Tensão interfacial', 'ASTM D971', 6250.00),
    ('DEMO-SEED-2026-017', 'Flexão de material', 'ASTM D790', 28146.15),
    ('DEMO-SEED-2026-018', 'Sólidos dissolvidos', 'Standard Methods 2540', 4250.00),
    ('DEMO-SEED-2026-019', 'Flexão em material compósito', 'ASTM D7264', 9100.00),
    ('DEMO-SEED-2026-020', 'Compostos voláteis', NULL, 6200.00),
    ('DEMO-SEED-2026-024', 'Tensão interfacial', 'ASTM D971', 6250.00),
    ('DEMO-SEED-2026-025', 'Flexão de material', 'ASTM D790', 28146.15)) AS seed(code, test_type, standard, unit_price)
JOIN orcamentos o ON o.code = seed.code
WHERE NOT EXISTS (
    SELECT 1 FROM itens_orcamento existing
    WHERE existing.orcamento_id = o.id AND existing.position = 2
);

-- Preserva a proposta de demonstração já usada no fluxo de cliente.
INSERT INTO orcamentos (
    empresa_id, code, name, status, stage, description, total_price, created_date
)
SELECT e.id, 'DEMO-LACTEC-001', 'Orçamento de demonstração', 'PendingAcceptance', 'Em negociação',
       'Orçamento de demonstração usado para validar o acesso do cliente.', 1250.00, CURRENT_TIMESTAMP
FROM empresas e
WHERE e.cnpj = '11222333000181'
  AND NOT EXISTS (SELECT 1 FROM orcamentos WHERE code = 'DEMO-LACTEC-001');
