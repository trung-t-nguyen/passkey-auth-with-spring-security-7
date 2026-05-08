CREATE TABLE users (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username    VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE
);
