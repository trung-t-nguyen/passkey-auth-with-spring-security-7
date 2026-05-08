-- test user: username=demo, password=password (BCrypt strength 10)
INSERT INTO users (username, password, enabled)
VALUES ('demo', '$2a$10$65t0LBmeGhNXD8todBxWvOQbtEQFAq5ily/M6jg9WSnXswM2lKKpy', TRUE);
