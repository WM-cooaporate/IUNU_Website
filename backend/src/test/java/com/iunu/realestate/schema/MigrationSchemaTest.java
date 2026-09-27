package com.iunu.realestate.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway migrations, which the rest of the suite never touches
 * (those tests let Hibernate generate the schema from the entities on H2).
 *
 * This runs against a real PostgreSQL, not a compatibility mode, because the
 * two failures that would otherwise only surface on the first deploy are
 * engine-specific: a migration that PostgreSQL refuses to apply, and a schema
 * that applies cleanly but that Hibernate's ddl-auto=validate then rejects.
 * Both are asserted here - the second simply by the context starting, since
 * validate is switched on below.
 *
 * Skipped, not failed, when Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("Flyway migrations on PostgreSQL")
class MigrationSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private JdbcTemplate jdbcTemplate;

    /** PostgreSQL folds unquoted identifiers to lower case, so every key here is lower case. */
    private Map<String, String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                        "SELECT column_name, data_type FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ?",
                        table)
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("column_name")).toLowerCase(Locale.ROOT),
                        row -> String.valueOf(row.get("data_type")).toUpperCase(Locale.ROOT)));
    }

    @Test
    @DisplayName("create a projects table with exactly the columns the Project entity maps")
    void projectsTableMatchesEntity() {
        Map<String, String> columns = columnsOf("projects");

        // Names must match Spring Boot's snake_case mapping of the entity fields.
        assertThat(columns).containsOnlyKeys(
                "id", "title", "description", "location", "status",
                "price_range", "cover_image_url", "published", "created_at", "updated_at");

        assertThat(columns.get("id")).isEqualTo("BIGINT");
        assertThat(columns.get("published")).isEqualTo("BOOLEAN");
        assertThat(columns.get("title")).isEqualTo("CHARACTER VARYING");
        // Not "TEXT" by accident: the entity dropped @Lob precisely so this
        // stays a text column rather than becoming a large-object oid.
        assertThat(columns.get("description")).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("give properties the three Arabic content columns V4 adds")
    void propertiesTableCarriesArabicColumns() {
        Map<String, String> columns = columnsOf("properties");

        assertThat(columns).containsKeys("title_ar", "description_ar", "location_ar");
        assertThat(columns.get("title_ar")).isEqualTo("CHARACTER VARYING");
        assertThat(columns.get("location_ar")).isEqualTo("CHARACTER VARYING");
        // TEXT rather than a large-object oid, for the same reason as description.
        assertThat(columns.get("description_ar")).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("keep every Arabic column nullable, so a save works with translation off")
    void arabicColumnsAreNullable() {
        Map<String, String> nullability = jdbcTemplate.queryForList(
                        "SELECT column_name, is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'properties'")
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("column_name")).toLowerCase(Locale.ROOT),
                        row -> String.valueOf(row.get("is_nullable")).toUpperCase(Locale.ROOT)));

        assertThat(nullability.get("title_ar")).isEqualTo("YES");
        assertThat(nullability.get("description_ar")).isEqualTo("YES");
        assertThat(nullability.get("location_ar")).isEqualTo("YES");
    }

    @Test
    @DisplayName("make title and published NOT NULL, and leave every descriptive field nullable")
    void nullabilityMatchesEntity() {
        Map<String, String> nullability = jdbcTemplate.queryForList(
                        "SELECT column_name, is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'projects'")
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("column_name")).toLowerCase(Locale.ROOT),
                        row -> String.valueOf(row.get("is_nullable")).toUpperCase(Locale.ROOT)));

        assertThat(nullability.get("title")).isEqualTo("NO");
        assertThat(nullability.get("published")).isEqualTo("NO");
        assertThat(nullability.get("created_at")).isEqualTo("NO");

        // Drafts are saved before the copy exists, so these must accept NULL.
        assertThat(nullability.get("description")).isEqualTo("YES");
        assertThat(nullability.get("location")).isEqualTo("YES");
        assertThat(nullability.get("status")).isEqualTo("YES");
        assertThat(nullability.get("price_range")).isEqualTo("YES");
        assertThat(nullability.get("cover_image_url")).isEqualTo("YES");
    }

    @Test
    @DisplayName("default published to false so a new row is a draft even when inserted directly")
    void publishedDefaultsToDraft() {
        jdbcTemplate.update("INSERT INTO projects (title) VALUES ('Direct insert')");

        Boolean published = jdbcTemplate.queryForObject(
                "SELECT published FROM projects WHERE title = 'Direct insert'", Boolean.class);

        assertThat(published).isFalse();
    }

    @Test
    @DisplayName("give every Instant-backed column a time zone, as Hibernate 6 expects")
    void timestampColumnsAreTimeZoneAware() {
        // A plain TIMESTAMP here is the classic MySQL-to-PostgreSQL carry-over:
        // it applies fine and then fails ddl-auto=validate at boot, because
        // Hibernate 6 maps java.time.Instant to TIMESTAMP WITH TIME ZONE.
        assertThat(columnsOf("projects").get("created_at")).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(columnsOf("projects").get("updated_at")).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(columnsOf("users").get("created_at")).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(columnsOf("users").get("locked_until")).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(columnsOf("refresh_tokens").get("expires_at")).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(columnsOf("contact_messages").get("created_at")).isEqualTo("TIMESTAMP WITH TIME ZONE");
    }

    @Test
    @DisplayName("reject two users whose emails differ only by case")
    void emailUniquenessIsCaseInsensitive() {
        jdbcTemplate.update(
                "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, 'ADMIN')",
                "Case Test", "Case.Test@iunu.example", "irrelevant-hash");

        // MySQL's collation made this a duplicate for free. On PostgreSQL only
        // the lower(email) unique index stops it, and that index is what keeps
        // two accounts from answering to the same login.
        assertThatDuplicateInsertFails("case.test@iunu.example");
    }

    private void assertThatDuplicateInsertFails(String email) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, 'ADMIN')",
                        "Duplicate", email, "irrelevant-hash"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
