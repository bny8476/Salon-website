CREATE TABLE processed_webhooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_processed_webhooks_event_provider UNIQUE (event_id, provider)
);

CREATE INDEX idx_users_reset_password_token ON users(reset_password_token);
