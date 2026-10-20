-- Manual upgrade for an existing auth database created by the first auth-service version.
-- The service does not use a migration tool; run this script once with sqlcmd.
USE [$(AUTH_DB_NAME)];
GO

-- 1. Add the name column by renaming username and dropping its old unique constraint.
IF COL_LENGTH(N'auth_user', N'name') IS NULL
   AND COL_LENGTH(N'auth_user', N'username') IS NOT NULL
BEGIN
    DECLARE @usernameConstraint sysname;
    SELECT TOP 1 @usernameConstraint = kc.name
    FROM sys.key_constraints kc
    JOIN sys.index_columns ic
      ON ic.object_id = kc.parent_object_id
     AND ic.index_id = kc.unique_index_id
    JOIN sys.columns c
      ON c.object_id = ic.object_id
     AND c.column_id = ic.column_id
    WHERE kc.parent_object_id = OBJECT_ID(N'auth_user')
      AND c.name = N'username';

    IF @usernameConstraint IS NOT NULL
        EXEC(N'ALTER TABLE auth_user DROP CONSTRAINT ' + QUOTENAME(@usernameConstraint));

    EXEC sp_rename N'auth_user.username', N'name', N'COLUMN';
END
GO

-- 2. Add the role column. Existing accounts become portal users; the setup flow creates the first admin.
IF COL_LENGTH(N'auth_user', N'role') IS NULL
BEGIN
    ALTER TABLE auth_user ADD role varchar(20) NOT NULL
        CONSTRAINT df_auth_user_role DEFAULT 'USER';
    ALTER TABLE auth_user ADD CONSTRAINT ck_auth_user_role
        CHECK (role IN ('ADMIN', 'USER'));
END
GO

-- 3. Remove the old external-service mapping columns.
IF COL_LENGTH(N'auth_user', N'service') IS NOT NULL
    ALTER TABLE auth_user DROP COLUMN service;
IF COL_LENGTH(N'auth_user', N'service_id') IS NOT NULL
    ALTER TABLE auth_user DROP COLUMN service_id;
GO

-- 4. Create the company, assignment, and API key tables.
IF OBJECT_ID(N'company', N'U') IS NULL
BEGIN
    CREATE TABLE company (
        id varchar(36) NOT NULL PRIMARY KEY,
        name nvarchar(200) NOT NULL,
        cnpj varchar(14) NOT NULL UNIQUE,
        created_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
END
GO

IF OBJECT_ID(N'auth_user_company', N'U') IS NULL
BEGIN
    CREATE TABLE auth_user_company (
        user_id varchar(36) NOT NULL,
        company_id varchar(36) NOT NULL,
        CONSTRAINT pk_auth_user_company PRIMARY KEY (user_id, company_id),
        CONSTRAINT fk_auth_user_company_user FOREIGN KEY (user_id)
            REFERENCES auth_user (id) ON DELETE CASCADE,
        CONSTRAINT fk_auth_user_company_company FOREIGN KEY (company_id)
            REFERENCES company (id) ON DELETE CASCADE
    );
    CREATE INDEX ix_auth_user_company_company ON auth_user_company (company_id);
END
GO

IF OBJECT_ID(N'api_key', N'U') IS NULL
BEGIN
    CREATE TABLE api_key (
        id varchar(36) NOT NULL PRIMARY KEY,
        name nvarchar(100) NOT NULL,
        key_prefix varchar(16) NOT NULL,
        key_hash varchar(64) NOT NULL UNIQUE,
        enabled bit NOT NULL DEFAULT 1,
        created_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
        expires_at datetime2 NULL,
        last_used_at datetime2 NULL
    );
END
GO
