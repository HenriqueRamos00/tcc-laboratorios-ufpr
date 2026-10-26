#!/bin/sh
# Cria os dois usuarios do banco e o papel de leitura e escrita.
#
# Sao dois usuarios de proposito: o dono do esquema roda a migracao e e o unico com
# permissao de criar tabela; a aplicacao entra com um usuario que le e grava e nao tem
# comando de definicao nem cota de espaco. Injecao de SQL que escape da camada de dados
# nao encontra privilegio para derrubar tabela.
#
# O script e idempotente e fica no diretorio de inicializacao, que roda a cada subida.
# Medido nesta imagem: o diretorio de preparacao nao executou, porque ela ja traz um banco
# pronto e pula o caminho de criacao, e nenhum usuario era criado.
#
# A senha nunca aparece neste arquivo: ela vem do ambiente do servico, que o compose
# preenche a partir do .env, que esta fora do git.
set -eu

: "${AUTH_SCHEMA_OWNER_USERNAME:?defina AUTH_SCHEMA_OWNER_USERNAME}"
: "${AUTH_SCHEMA_OWNER_PASSWORD:?defina AUTH_SCHEMA_OWNER_PASSWORD}"
: "${AUTH_APPLICATION_USERNAME:?defina AUTH_APPLICATION_USERNAME}"
: "${AUTH_APPLICATION_PASSWORD:?defina AUTH_APPLICATION_PASSWORD}"

sqlplus -S -L "/ as sysdba" <<SQL
WHENEVER SQLERROR EXIT 1
ALTER SESSION SET CONTAINER = ${ORACLE_PDB:-FREEPDB1};

DECLARE
  objeto_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(objeto_ja_existe, -1920);
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER ${AUTH_SCHEMA_OWNER_USERNAME}
    IDENTIFIED BY "${AUTH_SCHEMA_OWNER_PASSWORD}"
    DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
EXCEPTION
  WHEN objeto_ja_existe THEN NULL;
END;
/

DECLARE
  objeto_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(objeto_ja_existe, -1920);
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER ${AUTH_APPLICATION_USERNAME}
    IDENTIFIED BY "${AUTH_APPLICATION_PASSWORD}"
    DEFAULT TABLESPACE USERS QUOTA 0 ON USERS';
EXCEPTION
  WHEN objeto_ja_existe THEN NULL;
END;
/

DECLARE
  papel_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(papel_ja_existe, -1921);
BEGIN
  EXECUTE IMMEDIATE 'CREATE ROLE AUTH_READWRITE';
EXCEPTION
  WHEN papel_ja_existe THEN NULL;
END;
/

GRANT CREATE SESSION, CREATE TABLE TO ${AUTH_SCHEMA_OWNER_USERNAME};
GRANT CREATE SESSION TO ${AUTH_APPLICATION_USERNAME};
GRANT AUTH_READWRITE TO ${AUTH_APPLICATION_USERNAME};
EXIT;
SQL

echo "usuarios do esquema de autenticacao prontos"
