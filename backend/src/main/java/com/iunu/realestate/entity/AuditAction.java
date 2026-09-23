package com.iunu.realestate.entity;

/**
 * What an admin did. Stored by name in {@code audit_log.action} (VARCHAR(48)),
 * and offered as the filter in the dashboard's Activity tab.
 */
public enum AuditAction {
    /** Written for ADMIN logins only, and never for failures. */
    LOGIN_SUCCEEDED,
    PROPERTY_CREATED,
    PROPERTY_UPDATED,
    PROPERTY_DELETED,
    PROPERTY_PUBLISHED,
    PROPERTY_UNPUBLISHED,
    IMAGE_UPLOADED,
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_DELETED,
    ADMIN_USER_CREATED,
    PASSWORD_CHANGED,
    TRANSLATION_BACKFILL_RUN,
    IMAGES_MIGRATED,
    IMAGES_SWEPT,
    LEAD_MARKED_HANDLED
}
