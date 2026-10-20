-- Run once against a dedicated auth database. Auto-lab owns a different database/schema.
CREATE TABLE auth_user (
    id varchar(36) NOT NULL PRIMARY KEY,
    name nvarchar(150) NOT NULL,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    enabled bit NOT NULL DEFAULT 1,
    created_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_attempts int NOT NULL DEFAULT 0,
    locked_until datetime2 NULL
);

CREATE TABLE company (
    id varchar(36) NOT NULL PRIMARY KEY,
    name nvarchar(200) NOT NULL,
    cnpj varchar(14) NOT NULL UNIQUE,
    created_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

-- Spring Security's JDBC authorization schema, with SQL Server text/date types.
-- https://github.com/spring-projects/spring-security/blob/7.1.x/oauth2/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql
CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL PRIMARY KEY,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) NULL,
    attributes nvarchar(max) NULL,
    state varchar(500) NULL,
    authorization_code_value nvarchar(max) NULL,
    authorization_code_issued_at datetime2 NULL,
    authorization_code_expires_at datetime2 NULL,
    authorization_code_metadata nvarchar(max) NULL,
    access_token_value nvarchar(max) NULL,
    access_token_issued_at datetime2 NULL,
    access_token_expires_at datetime2 NULL,
    access_token_metadata nvarchar(max) NULL,
    access_token_type varchar(100) NULL,
    access_token_scopes varchar(1000) NULL,
    oidc_id_token_value nvarchar(max) NULL,
    oidc_id_token_issued_at datetime2 NULL,
    oidc_id_token_expires_at datetime2 NULL,
    oidc_id_token_metadata nvarchar(max) NULL,
    refresh_token_value nvarchar(max) NULL,
    refresh_token_issued_at datetime2 NULL,
    refresh_token_expires_at datetime2 NULL,
    refresh_token_metadata nvarchar(max) NULL,
    user_code_value nvarchar(max) NULL,
    user_code_issued_at datetime2 NULL,
    user_code_expires_at datetime2 NULL,
    user_code_metadata nvarchar(max) NULL,
    device_code_value nvarchar(max) NULL,
    device_code_issued_at datetime2 NULL,
    device_code_expires_at datetime2 NULL,
    device_code_metadata nvarchar(max) NULL
);
