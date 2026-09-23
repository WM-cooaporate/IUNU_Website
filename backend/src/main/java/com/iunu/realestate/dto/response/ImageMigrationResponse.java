package com.iunu.realestate.dto.response;

import java.util.List;

/**
 * @param enabled  false when the storage provider is local; nothing was scanned
 * @param migrated legacy /uploads/ images copied to Cloudinary and rewritten in the database
 * @param missing  legacy images whose file is already gone (the usual case after a
 *                 Render redeploy). Their rows are left untouched; the admin re-uploads them.
 */
public record ImageMigrationResponse(boolean enabled, int migrated, List<MissingImage> missing) {

    /** @param type PROPERTY or PROJECT */
    public record MissingImage(String type, Long id, String title, String url) {}
}
