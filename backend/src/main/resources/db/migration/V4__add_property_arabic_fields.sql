-- Arabic copies of the three admin-authored content fields on a project.
-- They are filled at save time from the English source (Google Cloud
-- Translation) and may be corrected by hand in the dashboard, so every column
-- is nullable: a save must still succeed when translation is off or fails.
--
-- The varchar limits are double their English counterparts (200 -> 400):
-- Arabic output is routinely longer than the English it came from, and a
-- truncated title is worse than a slightly oversized column.
ALTER TABLE properties ADD COLUMN title_ar       VARCHAR(400);
ALTER TABLE properties ADD COLUMN description_ar TEXT;
ALTER TABLE properties ADD COLUMN location_ar    VARCHAR(400);
