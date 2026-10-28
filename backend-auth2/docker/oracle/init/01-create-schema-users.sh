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
# Idempotente nao basta: ele precisa **convergir**. A versao anterior descartava o erro de
# usuario ja existente e seguia adiante, entao senha trocada no .env ficava so na aplicacao
# e o banco respondia ORA-01017 para sempre. O ALTER USER no fim resolve isso.
#
# A senha nunca aparece neste arquivo: ela vem do ambiente do servico, que o compose
# preenche a partir do .env, que esta fora do git.
set -eu

: "${AUTH_SCHEMA_OWNER_USERNAME:?defina AUTH_SCHEMA_OWNER_USERNAME}"
: "${AUTH_SCHEMA_OWNER_PASSWORD:?defina AUTH_SCHEMA_OWNER_PASSWORD}"
: "${AUTH_APPLICATION_USERNAME:?defina AUTH_APPLICATION_USERNAME}"
: "${AUTH_APPLICATION_PASSWORD:?defina AUTH_APPLICATION_PASSWORD}"

# O valor de exemplo do .env.example e uma senha sintaticamente valida, e o perfil do banco
# nao tem funcao de verificacao: medido, PASSWORD_VERIFY_FUNCTION do perfil DEFAULT e nula,
# entao o banco aceita o texto de aviso como senha. Copiar o arquivo e subir deixaria o
# servico com credencial publicada neste repositorio, e o marcador :? do compose so pega
# ausencia, nao pega esquecimento.
refuse_example_value() {
  case "$2" in
    troque-*)
      echo "$1 continua com o valor de exemplo do .env.example" >&2
      exit 1
      ;;
  esac
}

refuse_example_value AUTH_SCHEMA_OWNER_PASSWORD "${AUTH_SCHEMA_OWNER_PASSWORD}"
refuse_example_value AUTH_APPLICATION_PASSWORD "${AUTH_APPLICATION_PASSWORD}"

# O valor entra num literal de PL/SQL delimitado por aspa simples, executado como SYSDBA:
# aspa simples no valor encerra o literal e o que vem depois vira comando com o privilegio
# mais alto do banco. A aspa dupla nao tem escape, porque a forma IDENTIFIED BY entre aspas
# duplas do Oracle aceita qualquer caractere menos ela mesma, entao o valor e recusado em vez
# de escapado.
escape_for_pl_sql_literal() {
  case "$1" in
    *'"'*)
      echo "senha com aspa dupla nao e aceita pelo Oracle" >&2
      exit 1
      ;;
  esac
  printf '%s' "$1" | sed "s/'/''/g"
}

# Nome de usuario tambem nao e escapado, e sim validado: ele vai para este SQL e tambem para
# o CURRENT_SCHEMA da sessao da aplicacao, onde escape nao se aplica.
refuse_strange_username() {
  case "$2" in
    *[!A-Za-z0-9_]* | [!A-Za-z]*)
      echo "$1 precisa comecar com letra e ter so letra, numero e sublinhado" >&2
      exit 1
      ;;
  esac
}

refuse_strange_username AUTH_SCHEMA_OWNER_USERNAME "${AUTH_SCHEMA_OWNER_USERNAME}"
refuse_strange_username AUTH_APPLICATION_USERNAME "${AUTH_APPLICATION_USERNAME}"

SCHEMA_OWNER_PASSWORD_LITERAL=$(escape_for_pl_sql_literal "${AUTH_SCHEMA_OWNER_PASSWORD}")
APPLICATION_PASSWORD_LITERAL=$(escape_for_pl_sql_literal "${AUTH_APPLICATION_PASSWORD}")

sqlplus -S -L "/ as sysdba" <<SQL
WHENEVER SQLERROR EXIT 1
ALTER SESSION SET CONTAINER = ${ORACLE_PDB:-FREEPDB1};

-- Senha de servico nao e senha de pessoa: quem a troca e o operador, por mudanca de .env, e
-- nao o calendario do banco. Medido neste Oracle: o perfil DEFAULT vence a senha em 180
-- dias com 7 de carencia, e a EXPIRY_DATE dos dois usuarios tinha caido seis meses depois
-- da criacao. No dia em que ela vencesse, o pool deixaria de abrir conexao com ORA-28001 e
-- o unico servico de autenticacao pararia por inteiro, sem que reiniciar o contentor
-- resolvesse nada.
--
-- INACTIVE_ACCOUNT_TIME tambem sai: o dono do esquema so abre sessao quando a migracao roda,
-- e um ano sem migracao trancaria a conta dele.
--
-- O freio de tentativa fica, porque ele protege de adivinhacao, mas com espera de um minuto
-- em vez de um dia: dez recusas de conexao, que um laco de reinicio produz sozinho, nao
-- podem custar um dia de servico fora do ar.
--
-- PASSWORD_REUSE_TIME e PASSWORD_REUSE_MAX precisam ser ilimitados porque o ALTER USER
-- abaixo reapresenta a mesma senha a cada subida.
DECLARE
  perfil_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(perfil_ja_existe, -2380);
BEGIN
  EXECUTE IMMEDIATE 'CREATE PROFILE AUTH_SERVICE LIMIT
    PASSWORD_LIFE_TIME UNLIMITED
    PASSWORD_GRACE_TIME UNLIMITED
    INACTIVE_ACCOUNT_TIME UNLIMITED
    PASSWORD_REUSE_TIME UNLIMITED
    PASSWORD_REUSE_MAX UNLIMITED
    FAILED_LOGIN_ATTEMPTS 10
    PASSWORD_LOCK_TIME 1/1440';
EXCEPTION
  WHEN perfil_ja_existe THEN NULL;
END;
/

DECLARE
  objeto_ja_existe EXCEPTION;
  PRAGMA EXCEPTION_INIT(objeto_ja_existe, -1920);
BEGIN
  EXECUTE IMMEDIATE 'CREATE USER ${AUTH_SCHEMA_OWNER_USERNAME}
    IDENTIFIED BY "${SCHEMA_OWNER_PASSWORD_LITERAL}"
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
    IDENTIFIED BY "${APPLICATION_PASSWORD_LITERAL}"
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

-- A convergencia que faltava. Os dois blocos acima descartam "usuario ja existe", entao sem
-- estas duas linhas a senha e o perfil ficam congelados no estado da primeira subida.
ALTER USER ${AUTH_SCHEMA_OWNER_USERNAME}
  IDENTIFIED BY "${SCHEMA_OWNER_PASSWORD_LITERAL}" PROFILE AUTH_SERVICE;
ALTER USER ${AUTH_APPLICATION_USERNAME}
  IDENTIFIED BY "${APPLICATION_PASSWORD_LITERAL}" PROFILE AUTH_SERVICE;

GRANT CREATE SESSION, CREATE TABLE TO ${AUTH_SCHEMA_OWNER_USERNAME};
GRANT CREATE SESSION TO ${AUTH_APPLICATION_USERNAME};
GRANT AUTH_READWRITE TO ${AUTH_APPLICATION_USERNAME};
EXIT;
SQL

# Marcador que o healthcheck do compose consulta.
#
# A sonda da imagem so olha o estado do banco, e os scripts de inicializacao rodam depois de
# ela ja responder zero: sem este arquivo, depends_on service_healthy libera a api antes de
# os usuarios existirem, e a api cai com ORA-01017 em laco de reinicio. Pior, o
# runUserScripts.sh carrega este script com ponto e ninguem confere o status dele, entao
# falha aqui deixaria o contentor sadio com o banco vazio. Como a linha so e alcancada
# quando tudo acima passou, ausencia do arquivo significa inicializacao incompleta.
#
# Ele vive em /tmp porque /tmp nao e volume: a cada subida ele precisa ser reescrito, que e o
# que se espera de uma sonda.
: > /tmp/auth-schema-users-ready

echo "usuarios do esquema de autenticacao prontos"
