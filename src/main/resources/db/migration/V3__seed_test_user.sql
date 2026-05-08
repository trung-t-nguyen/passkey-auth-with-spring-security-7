-- password: "password"
INSERT INTO users (username, password, first_name, last_name, enabled) VALUES
    ('alice', '$2a$10$dlHceSp.uoyDqAdgI5PJEeKJ.zbSeRKdc.ZkplpTvPCt5iZqKhJ7q', 'Alice', 'Smith',   TRUE),
    ('bob',   '$2a$10$KtexYg4jIBf0IPheKYC34uEID0oJf9e.p4b6lyp/TLwpmPRW9Elw.', 'Bob',   'Jones',   TRUE),
    ('carol', '$2a$10$yTYC4qNKUZNzKis43dpDqudwTIn.UHaeeVa0C69W.dIrQw8DeKweu', 'Carol', 'Johnson', TRUE);
