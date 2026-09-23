-- Why and when a refresh token stopped working.
--
-- "revoked" alone cannot tell a token that was rotated a moment ago (normal:
-- two tabs refreshing at once) from one that was rotated days ago and is now
-- being replayed (a stolen token). The reason and the time are what make
-- reuse detection possible - see AuthServiceImpl.refresh().
--
-- Both nullable: rows revoked before this migration have neither, and are
-- treated as "revoked, reason unknown" - a plain 401 with no side effects.
ALTER TABLE refresh_tokens ADD COLUMN revoked_at     TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE refresh_tokens ADD COLUMN revoked_reason VARCHAR(32);
