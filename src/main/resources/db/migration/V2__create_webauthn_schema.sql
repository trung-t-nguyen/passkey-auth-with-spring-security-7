CREATE TABLE user_entities (
    id           VARCHAR(255) NOT NULL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    display_name VARCHAR(255)
);

CREATE UNIQUE INDEX user_entities_name_idx ON user_entities (name);

CREATE TABLE user_credentials (
    credential_id                VARCHAR(255) NOT NULL PRIMARY KEY,
    user_entity_user_id          VARCHAR(255) NOT NULL,
    public_key                   BYTEA        NOT NULL,
    signature_count              BIGINT       NOT NULL DEFAULT 0,
    uv_initialized               BOOLEAN      NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN      NOT NULL DEFAULT FALSE,
    authenticator_transports     TEXT,
    public_key_credential_type   VARCHAR(255),
    backup_state                 BOOLEAN      NOT NULL DEFAULT FALSE,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP    NOT NULL,
    last_used                    TIMESTAMP    NOT NULL,
    label                        TEXT         NOT NULL,
    CONSTRAINT fk_user_entity FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id)
);
