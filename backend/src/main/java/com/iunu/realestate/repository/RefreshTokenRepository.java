package com.iunu.realestate.repository;

import com.iunu.realestate.entity.RefreshToken;
import com.iunu.realestate.entity.RevocationReason;
import com.iunu.realestate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marks one live token used, as a single conditional UPDATE, and says
     * whether this caller was the one that did it.
     *
     * <p>This is what makes rotation atomic. Reading the row, checking
     * {@code revoked} and then writing it is a race under READ COMMITTED: two
     * requests carrying the same token both read "not revoked", both write, and
     * both walk away with a fresh pair - a thief and the real user each keeping
     * a session alive. Here the check and the write are one statement, the
     * database serialises the two updates on the row lock, and the second one
     * re-evaluates the WHERE clause, finds {@code revoked = true} and matches
     * nothing. Exactly one caller sees 1.
     *
     * @return 1 if this call consumed the token, 0 if it was already revoked,
     *         expired, or consumed by a concurrent request
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revoked = true, t.revokedAt = :now, t.revokedReason = :reason
             where t.id = :id and t.revoked = false and t.expiresAt > :now""")
    int consume(@Param("id") Long id, @Param("now") Instant now, @Param("reason") RevocationReason reason);

    /**
     * Revokes every still-live token of {@code user}. Tokens already revoked
     * keep their original reason and time, so a later investigation can still
     * see which one was rotated and which one was replayed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken r
               set r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason
             where r.user = :user and r.revoked = false""")
    int revokeAllForUser(@Param("user") User user, @Param("now") Instant now,
                         @Param("reason") RevocationReason reason);

    /**
     * Expired rows only. Revoked-but-unexpired rows are deliberately kept: a
     * rotated token is exactly what a thief replays, and reuse detection can
     * only recognise it while the row still exists. They go once they expire,
     * which is also the last moment anyone could have replayed them.
     */
    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
