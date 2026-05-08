CREATE TABLE user_entities (
    id           TEXT NOT NULL,
    name         VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT user_entities_name_unique UNIQUE (name)
);

CREATE TABLE user_credentials (
    credential_id                TEXT      NOT NULL,
    user_entity_user_id          TEXT      NOT NULL REFERENCES user_entities (id),
    public_key                   BYTEA     NOT NULL,
    signature_count              BIGINT    NOT NULL DEFAULT 0,
    uv_initialized               BOOLEAN   NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN   NOT NULL DEFAULT FALSE,
    authenticator_transports     TEXT,
    public_key_credential_type   TEXT,
    backup_state                 BOOLEAN   NOT NULL DEFAULT FALSE,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP NOT NULL,
    last_used                    TIMESTAMP,
    label                        TEXT      NOT NULL,
    PRIMARY KEY (credential_id)
);
