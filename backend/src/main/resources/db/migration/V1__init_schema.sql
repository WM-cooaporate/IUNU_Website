-- Core schema for the IUNU real estate backend.
-- InnoDB + utf8mb4 everywhere; Hibernate is set to ddl-auto=validate so this
-- file (and any future V{n}__*.sql) is the single source of truth for DDL.

CREATE TABLE users (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name              VARCHAR(150)  NOT NULL,
    email                  VARCHAR(190)  NOT NULL,
    phone                  VARCHAR(30),
    password               VARCHAR(255)  NOT NULL,
    role                   VARCHAR(20)   NOT NULL DEFAULT 'USER',
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    account_locked         BOOLEAN       NOT NULL DEFAULT FALSE,
    failed_login_attempts  INT           NOT NULL DEFAULT 0,
    locked_until           TIMESTAMP(6)  NULL,
    created_at             TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6)  NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    expires_at  TIMESTAMP(6)  NOT NULL,
    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    expires_at  TIMESTAMP(6)  NOT NULL,
    used        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE properties (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200)    NOT NULL,
    description      TEXT,
    type             VARCHAR(20)     NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE',
    location         VARCHAR(200),
    price            DECIMAL(14, 2),
    cover_image_url  VARCHAR(500),
    published        BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_properties_type ON properties (type);
CREATE INDEX idx_properties_published ON properties (published);

CREATE TABLE property_images (
    property_id  BIGINT        NOT NULL,
    image_url    VARCHAR(500)  NOT NULL,
    CONSTRAINT fk_property_images_property FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_property_images_property ON property_images (property_id);

CREATE TABLE contact_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name  VARCHAR(100)  NOT NULL,
    last_name   VARCHAR(100)  NOT NULL,
    phone       VARCHAR(30)   NOT NULL,
    email       VARCHAR(190)  NOT NULL,
    message     TEXT          NOT NULL,
    handled     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_contact_messages_created_at ON contact_messages (created_at);

CREATE TABLE quote_requests (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    phone       VARCHAR(30)   NOT NULL,
    city        VARCHAR(100)  NOT NULL,
    email       VARCHAR(190)  NOT NULL,
    project     VARCHAR(30)   NOT NULL,
    whatsapp    VARCHAR(30),
    space_type  VARCHAR(30)   NOT NULL,
    handled     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_quote_requests_created_at ON quote_requests (created_at);

CREATE TABLE newsletter_subscribers (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(190)  NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_newsletter_subscribers_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
