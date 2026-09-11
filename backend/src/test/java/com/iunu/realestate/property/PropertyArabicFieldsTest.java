package com.iunu.realestate.property;

import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.support.IntegrationTest;
import com.iunu.realestate.translation.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Arabic half of a project, end to end: an admin save fills it, the public
 * endpoint hands both languages to the site, and the backfill only ever writes
 * into fields that are still empty.
 *
 * The translator is mocked, so nothing here reaches Google.
 */
@DisplayName("Arabic project fields")
class PropertyArabicFieldsTest extends IntegrationTest {

    @MockBean private TranslationService translationService;
    @Autowired private PropertyRepository propertyRepository;

    @BeforeEach
    void enableTranslator() {
        when(translationService.isEnabled()).thenReturn(true);
        // The filler sends only the fields that need translating, so the stub
        // answers positionally with as many values as it was asked for.
        when(translationService.translateEnToAr(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(text -> text == null || text.isBlank() ? null : "[ar] " + text).toList();
        });
    }

    private long createProject(String title) throws Exception {
        String created = mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","description":"A quiet place","type":"RESIDENTIAL","location":"New Cairo"}
                                 """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    @DisplayName("a create with no Arabic comes back with the translated Arabic in the response")
    void createFillsArabic() throws Exception {
        String title = "Arabic-" + System.nanoTime();

        mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","description":"A quiet place","type":"RESIDENTIAL","location":"New Cairo"}
                                 """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titleAr").value("[ar] " + title))
                .andExpect(jsonPath("$.descriptionAr").value("[ar] A quiet place"))
                .andExpect(jsonPath("$.locationAr").value("[ar] New Cairo"));
    }

    @Test
    @DisplayName("Arabic the admin typed is stored as typed, not overwritten by the translator")
    void adminSuppliedArabicWins() throws Exception {
        String title = "Manual-" + System.nanoTime();

        mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"%s","type":"RESIDENTIAL","location":"New Cairo","titleAr":"عنوان يدوي"}
                                 """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titleAr").value("عنوان يدوي"))
                .andExpect(jsonPath("$.locationAr").value("[ar] New Cairo"));
    }

    @Test
    @DisplayName("the public endpoint exposes all three Arabic fields")
    void publicReadExposesArabic() throws Exception {
        long id = createProject("Public-" + System.nanoTime());

        mockMvc.perform(get("/api/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleAr").isNotEmpty())
                .andExpect(jsonPath("$.descriptionAr").value("[ar] A quiet place"))
                .andExpect(jsonPath("$.locationAr").value("[ar] New Cairo"));
    }

    @Test
    @DisplayName("a save still succeeds, with Arabic null, when the translator returns nothing")
    void translationFailureDoesNotBlockASave() throws Exception {
        when(translationService.translateEnToAr(anyList()))
                .thenAnswer(invocation -> java.util.Collections.nCopies(
                        ((List<?>) invocation.getArgument(0)).size(), null));

        mockMvc.perform(post("/api/properties")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"Failing-%d","type":"RESIDENTIAL","location":"New Cairo"}
                                 """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titleAr").doesNotExist())
                .andExpect(jsonPath("$.locationAr").doesNotExist());
    }

    @Test
    @DisplayName("preview translates without saving anything")
    void previewReturnsArabic() throws Exception {
        mockMvc.perform(post("/api/admin/translations/preview")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"Tower","description":"","location":"Sheikh Zayed"}
                                 """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.titleAr").value("[ar] Tower"))
                .andExpect(jsonPath("$.descriptionAr").doesNotExist())
                .andExpect(jsonPath("$.locationAr").value("[ar] Sheikh Zayed"));
    }

    @Test
    @DisplayName("preview answers enabled=false rather than failing when no key is configured")
    void previewReportsDisabledProvider() throws Exception {
        when(translationService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/admin/translations/preview")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"Tower"}
                                 """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.titleAr").doesNotExist());
    }

    @Test
    @DisplayName("the backfill fills empty Arabic and leaves an admin-edited title alone")
    void backfillOnlyTouchesEmptyFields() throws Exception {
        Property untranslated = propertyRepository.save(Property.builder()
                .title("Old project " + System.nanoTime())
                .description("Written before translation existed")
                .location("Maadi")
                .type(PropertyType.RESIDENTIAL)
                .build());

        Property handEdited = propertyRepository.save(Property.builder()
                .title("Hand edited " + System.nanoTime())
                .location("Zamalek")
                .titleAr("عنوان كتبه المدير")
                .type(PropertyType.COMMERCIAL)
                .build());

        mockMvc.perform(post("/api/admin/translations/properties/backfill")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updated").isNumber());

        Property filled = propertyRepository.findById(untranslated.getId()).orElseThrow();
        assertThat(filled.getTitleAr()).isEqualTo("[ar] " + untranslated.getTitle());
        assertThat(filled.getDescriptionAr()).isEqualTo("[ar] Written before translation existed");
        assertThat(filled.getLocationAr()).isEqualTo("[ar] Maadi");

        Property preserved = propertyRepository.findById(handEdited.getId()).orElseThrow();
        assertThat(preserved.getTitleAr()).isEqualTo("عنوان كتبه المدير");
        // The field that really was empty is still filled in for that row.
        assertThat(preserved.getLocationAr()).isEqualTo("[ar] Zamalek");
    }

    @Test
    @DisplayName("the backfill reports enabled=false and changes nothing when no key is configured")
    void backfillReportsDisabledProvider() throws Exception {
        when(translationService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/admin/translations/properties/backfill")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.scanned").value(0))
                .andExpect(jsonPath("$.updated").value(0));
    }
}
