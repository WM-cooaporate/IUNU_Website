-- Composite indexes for the two queries every public page runs.
--
-- V1 already created single-column indexes on (published) and (type). Those
-- are kept - they still serve the admin listing and any type-only filter - but
-- neither covers what listPublished() actually asks for, which is always a
-- WHERE on published (plus sometimes type) followed by an ORDER BY. PostgreSQL
-- can only use one of them per scan, so the sort spills to a sort node over
-- every published row.
--
-- The two below match the two shapes of that query exactly:
--
--   findByPublishedTrue(pageable)             -> (published, created_at)
--   findByPublishedTrueAndType(type, pageable) -> (published, type)
--
-- created_at is DESC because that is the order the site lists in, and a
-- descending index lets the planner walk it backwards-free for LIMIT/OFFSET
-- paging. Leading with published keeps drafts out of the scan entirely.
--
-- IF NOT EXISTS so re-running against a database where someone already added
-- one by hand is a no-op rather than a failed migration.
CREATE INDEX IF NOT EXISTS idx_properties_published_type
    ON properties (published, type);

CREATE INDEX IF NOT EXISTS idx_properties_published_created_at
    ON properties (published, created_at DESC);

-- The orphan-image check (isImageUsedByOtherProperty) asks "does any other
-- property use this URL?" against both the cover column and the gallery table.
-- property_images already has an index on property_id from V1, but that is the
-- wrong direction for this lookup: the search is by URL.
CREATE INDEX IF NOT EXISTS idx_properties_cover_image_url
    ON properties (cover_image_url);

CREATE INDEX IF NOT EXISTS idx_property_images_image_url
    ON property_images (image_url);
