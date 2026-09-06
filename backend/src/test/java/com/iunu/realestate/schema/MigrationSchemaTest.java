package com.iunu.realestate.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway migrations, which the other tests never touch (they let
 * Hibernate generate the schema from the entities on H2).
 *
 * The failure this catches is the expensive one: a column named or sized
 * differently in V3__create_projects.sql than the Project entity expects.
 * On MySQL that surfaces only at boot, as a ddl-auto=validate error.
 */
@SpringBootTest
@ActiveProfiles({"test", "migcheck"})
@DisplayName("Flyway migrations")
class MigrationSchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private Map<String, String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                        "SELECT column_name, data_type FROM information_schema.columns WHERE lower(table_name) = ?",
                        table)
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("COLUMN_NAME")).toLowerCase(Locale.ROOT),
                        row -> String.valueOf(row.get("DATA_TYPE")).toUpperCase(Locale.ROOT)));
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
        assertThat(columns.get("title")).contains("CHARACTER VARYING");
    }

    @Test
    @DisplayName("make title and published NOT NULL, and leave every descriptive field nullable")
    void nullabilityMatchesEntity() {
        Map<String, String> nullability = jdbcTemplate.queryForList(
                        "SELECT column_name, is_nullable FROM information_schema.columns "
                                + "WHERE lower(table_name) = 'projects'")
                .stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("COLUMN_NAME")).toLowerCase(Locale.ROOT),
                        row -> String.valueOf(row.get("IS_NULLABLE")).toUpperCase(Locale.ROOT)));

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
}
