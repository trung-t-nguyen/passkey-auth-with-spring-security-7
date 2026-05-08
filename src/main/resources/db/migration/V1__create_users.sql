CREATE TABLE users (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT users_username_unique UNIQUE (username)
);
