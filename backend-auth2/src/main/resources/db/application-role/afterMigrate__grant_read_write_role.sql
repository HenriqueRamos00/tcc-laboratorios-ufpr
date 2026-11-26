BEGIN
  FOR owned IN (
    SELECT table_name
      FROM user_tables
     WHERE table_name <> 'flyway_schema_history'
       AND dropped = 'NO'
  ) LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON "' || owned.table_name || '" TO AUTH_READWRITE';
  END LOOP;
END;
/
