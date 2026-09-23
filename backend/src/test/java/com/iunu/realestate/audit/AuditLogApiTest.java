package com.iunu.realestate.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.iunu.realestate.entity.AuditAction;
import com.iunu.realestate.entity.AuditLogEntry;
import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.AuditLogRepository;
import com.iunu.realestate.service.AuditLogService;
import com.iunu.realestate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Admin audit log")
class AuditLogApiTest extends IntegrationTest {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private PlatformTransactionManager transactionManager;

    private List<AuditLogEntry> rowsFor(String targetType, Object targetId) {
        return auditLogRepository.findAll().stream()
                .filter(row -> targetType.equals(row.getTargetType()) && String.valueOf(targetId).equals(row.getTargetId()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
    }

    private String propertyJson(String title, boolean published) throws Exception {
        return objectMapper.writeValueAsString(Map.of("title", title, "type", "RESIDENTIAL", "published", published));
    }

    @Test
    @DisplayName("create, update, publish and delete a property: four rows, right actions, right actor")
    void propertyLifecycleIsAudited() throws Exception {
        User admin = createUser("audit-admin-" + System.nanoTime() + "@iunu.test", "Password1", Role.ADMIN);
        String bearer = bearerFor(admin);

        String created = mockMvc.perform(post("/api/properties").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Draft villa", false)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/properties/" + id).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Renamed villa", false)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/properties/" + id).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Renamed villa", true)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/properties/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        List<AuditLogEntry> rows = rowsFor("PROPERTY", id);
        assertThat(rows).extracting(AuditLogEntry::getAction).containsExactly(
                AuditAction.PROPERTY_CREATED, AuditAction.PROPERTY_UPDATED,
                AuditAction.PROPERTY_PUBLISHED, AuditAction.PROPERTY_DELETED);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getActorId()).isEqualTo(admin.getId());
            assertThat(row.getActorEmail()).isEqualTo(admin.getEmail());
            assertThat(row.getOccurredAt()).isNotNull();
            assertThat(row.getRequestId()).hasSize(36);
        });
        assertThat(rows.get(1).getSummary()).isEqualTo("title changed");
        assertThat(rows.get(2).getSummary()).isEqualTo("published false→true");
        // Field names, never field values.
        assertThat(rows).allSatisfy(row -> assertThat(row.getSummary()).doesNotContain("villa"));
    }

    @Test
    @DisplayName("an audit row written inside a transaction that rolls back is rolled back with it")
    void rolledBackChangeLeavesNoRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String marker = "rollback-probe-" + System.nanoTime();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            auditLogService.recordFor(1L, "x@iunu.test", AuditAction.PROJECT_UPDATED, "PROJECT", marker, "updated");
            throw new IllegalStateException("the change failed after the audit call");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowsFor("PROJECT", marker)).isEmpty();
    }

    @Test
    @DisplayName("a failed change writes no row")
    void failedChangeWritesNoRow() throws Exception {
        long before = auditLogRepository.count();
        mockMvc.perform(put("/api/properties/999999").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(propertyJson("Nope", true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/properties").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"RESIDENTIAL\"}"))
                .andExpect(status().isBadRequest());
        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("creating an admin user is audited")
    void adminCreationIsAudited() throws Exception {
        String created = mockMvc.perform(post("/api/admin/users").header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Second Admin",
                                "email", "second-" + System.nanoTime() + "@iunu.test",
                                "password", "Password1"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        assertThat(rowsFor("USER", id)).extracting(AuditLogEntry::getAction)
                .containsExactly(AuditAction.ADMIN_USER_CREATED);
    }

    @Test
    @DisplayName("the endpoint is newest first, filterable, and capped at 50 per page")
    void endpointPagesNewestFirst() throws Exception {
        String bearer = adminBearer();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/properties").header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(propertyJson("P" + i, false)))
                    .andExpect(status().isCreated());
        }

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/admin/audit-log")
                        .param("action", "PROPERTY_CREATED").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("size").asInt()).isEqualTo(50);
        JsonNode content = page.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(3);
        for (JsonNode row : content) {
            assertThat(row.get("action").asText()).isEqualTo("PROPERTY_CREATED");
        }
        for (int i = 1; i < content.size(); i++) {
            assertThat(content.get(i - 1).get("occurredAt").asText())
                    .isGreaterThanOrEqualTo(content.get(i).get("occurredAt").asText());
        }
    }

    @Test
    @DisplayName("an unknown action filter is a 400, not a 500")
    void unknownActionIs400() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log").param("action", "DROP_TABLE")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("anonymous gets 401 and a USER gets 403")
    void endpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit-log").header(HttpHeaders.AUTHORIZATION, userBearer()))
                .andExpect(status().isForbidden());
    }
}
