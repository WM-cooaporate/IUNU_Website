-- PostgreSQL always appends new columns; there is no AFTER clause and column
-- order is not something the application depends on.
ALTER TABLE properties
    ADD COLUMN area DECIMAL(12, 2);
