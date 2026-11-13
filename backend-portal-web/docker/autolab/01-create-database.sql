IF DB_ID(N'autolab') IS NULL
    CREATE DATABASE [autolab] COLLATE SQL_Latin1_General_CP1_CI_AS;
GO

IF CAST(DATABASEPROPERTYEX(N'autolab', 'Collation') AS nvarchar(128))
   <> N'SQL_Latin1_General_CP1_CI_AS'
    THROW 50001, N'O banco autolab existe com outra collation. Apague o banco antes de carregar a massa.', 1;
GO
