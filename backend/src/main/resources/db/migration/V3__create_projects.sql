-- Developments shown on the public "Projects" page and managed from the
-- admin dashboard. Deliberately narrower than `properties`: a project is
-- editorial content (title, blurb, cover image, published flag), whereas a
-- property is a sellable unit with a type, area and price.
--
-- Only `title` is required. Every other descriptive column is nullable so
-- the admin dashboard can save a draft before the full copy exists.

CREATE TABLE projects (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200)    NOT NULL,
    description      TEXT,
    location         VARCHAR(200),
    status           VARCHAR(20)     NULL,
    price_range      VARCHAR(100),
    cover_image_url  VARCHAR(500),
    published        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- GET /api/projects filters on published and orders by created_at DESC.
CREATE INDEX idx_projects_published_created_at ON projects (published, created_at);
