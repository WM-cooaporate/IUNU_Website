package com.iunu.realestate.dto.response;

/**
 * @param enabled false when the storage provider is local; nothing was scanned
 * @param scanned this environment's assets old enough to be considered
 * @param orphans of those, the ones nothing references
 * @param deleted orphans actually deleted (0 on a dry run; at most 200 per run)
 * @param dryRun  true when nothing was deleted on purpose
 */
public record ImageSweepResponse(boolean enabled, int scanned, int orphans, int deleted, boolean dryRun) {
}
