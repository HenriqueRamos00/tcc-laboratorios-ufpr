-- Dados locais de demonstração. Senha do usuário: lactec.
INSERT INTO empresas (
    legal_name, cnpj, address, city, state, contact_name, contact_phone, contact_email, ativo, origin
)
SELECT
    'Empresa Lactec de demonstração', '11222333000181', 'Rua de demonstração, 1',
    'Curitiba', 'PR', 'Contato Lactec', '41999999999', 'contato@lactec.com', TRUE, 'LOCAL'
WHERE NOT EXISTS (
    SELECT 1 FROM empresas WHERE cnpj = '11222333000181'
);

UPDATE usuarios
SET nome = 'Cliente Lactec',
    senha_hash = '$2y$10$DNdvFeSbdH8589xdrlFz5OZLuVhExy8hVAbVBwlMCY0U80ORxFd7K',
    email_normalizado = 'lactec@lactec.com',
    ativo = TRUE
WHERE lower(trim(email)) = 'lactec@lactec.com' AND perfil = 'CLIENTE';

INSERT INTO usuarios (nome, email, email_normalizado, senha_hash, perfil, ativo)
SELECT
    'Cliente Lactec', 'lactec@lactec.com', 'lactec@lactec.com',
    '$2y$10$DNdvFeSbdH8589xdrlFz5OZLuVhExy8hVAbVBwlMCY0U80ORxFd7K', 'CLIENTE', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM usuarios WHERE lower(trim(email)) = 'lactec@lactec.com'
);

INSERT INTO clientes (usuario_id, phone, position)
SELECT u.id, '41999999999', 'Responsável'
FROM usuarios u
WHERE lower(trim(u.email)) = 'lactec@lactec.com' AND u.perfil = 'CLIENTE'
  AND NOT EXISTS (SELECT 1 FROM clientes c WHERE c.usuario_id = u.id);

UPDATE clientes
SET phone = '41999999999', position = 'Responsável'
WHERE usuario_id = (
    SELECT id FROM usuarios WHERE lower(trim(email)) = 'lactec@lactec.com' AND perfil = 'CLIENTE'
);

INSERT INTO cliente_empresas (cliente_id, empresa_id)
SELECT c.id, e.id
FROM clientes c
JOIN usuarios u ON u.id = c.usuario_id
JOIN empresas e ON e.cnpj = '11222333000181'
WHERE lower(trim(u.email)) = 'lactec@lactec.com' AND u.perfil = 'CLIENTE'
  AND NOT EXISTS (
      SELECT 1 FROM cliente_empresas ce
      WHERE ce.cliente_id = c.id AND ce.empresa_id = e.id
  );

INSERT INTO orcamentos (
    empresa_id, code, name, status, stage, description, total_price, created_date
)
SELECT
    e.id, 'DEMO-LACTEC-001', 'Orçamento de demonstração', 'Em negociação', 'Em negociação',
    'Orçamento de demonstração usado para validar o acesso do cliente.', 1250.00, CURRENT_TIMESTAMP
FROM empresas e
WHERE e.cnpj = '11222333000181'
  AND NOT EXISTS (SELECT 1 FROM orcamentos WHERE code = 'DEMO-LACTEC-001');
