-- One Postgres container, two logical databases: Keycloak and docs-api must not share tables.
-- Runs only on first start of an empty data volume.
CREATE DATABASE keycloak;
CREATE DATABASE docvault;
