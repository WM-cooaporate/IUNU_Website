package com.iunu.realestate.translation;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.entity.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rule this class encodes: Arabic the admin typed always wins, blank
 * Arabic is machine-filled, and blank English produces nothing.
 */
@DisplayName("Property translation filler")
class PropertyTranslationFillerTest {

    private TranslationService translationService;
    private PropertyTranslationFiller filler;

    @BeforeEach
    void setUp() {
        translationService = mock(TranslationService.class);
        filler = new PropertyTranslationFiller(translationService);
    }

    private static PropertyRequest request(String title, String description, String location,
                                           String titleAr, String descriptionAr, String locationAr) {
        return new PropertyRequest(title, description, PropertyType.RESIDENTIAL, null, location,
                titleAr, descriptionAr, locationAr, null, null, null, null, null);
    }

    @Test
    @DisplayName("translates a blank Arabic field whose English is filled in")
    void translatesMissingArabic() {
        when(translationService.translateEnToAr(anyList()))
                .thenReturn(List.of("إيونو ريزيدنس", "وصف", "القاهرة الجديدة"));

        PropertyRequest filled = filler.fill(
                request("IUNU Residence", "A description", "New Cairo", "", null, "  "));

        assertThat(filled.titleAr()).isEqualTo("إيونو ريزيدنس");
        assertThat(filled.descriptionAr()).isEqualTo("وصف");
        assertThat(filled.locationAr()).isEqualTo("القاهرة الجديدة");
        // The English side is passed through untouched.
        assertThat(filled.title()).isEqualTo("IUNU Residence");
        assertThat(filled.type()).isEqualTo(PropertyType.RESIDENTIAL);
    }

    @Test
    @DisplayName("keeps Arabic the admin supplied and never sends that field to the translator")
    void keepsAdminAuthoredArabic() {
        when(translationService.translateEnToAr(anyList())).thenReturn(List.of("وصف"));

        PropertyRequest filled = filler.fill(
                request("IUNU Residence", "A description", "New Cairo",
                        "عنوان من المدير", null, "موقع من المدير"));

        assertThat(filled.titleAr()).isEqualTo("عنوان من المدير");
        assertThat(filled.locationAr()).isEqualTo("موقع من المدير");
        assertThat(filled.descriptionAr()).isEqualTo("وصف");

        ArgumentCaptor<List<String>> sent = ArgumentCaptor.forClass(List.class);
        verify(translationService).translateEnToAr(sent.capture());
        assertThat(sent.getValue()).containsExactly("A description");
    }

    @Test
    @DisplayName("leaves Arabic null when the English is blank, and calls nothing at all")
    void blankEnglishTranslatesToNothing() {
        PropertyRequest filled = filler.fill(request("", "  ", null, null, null, null));

        assertThat(filled.titleAr()).isNull();
        assertThat(filled.descriptionAr()).isNull();
        assertThat(filled.locationAr()).isNull();
        verify(translationService, never()).translateEnToAr(anyList());
    }

    @Test
    @DisplayName("batches all three fields into exactly one translator call")
    void usesASingleCall() {
        when(translationService.translateEnToAr(anyList())).thenReturn(List.of("أ", "ب", "ج"));

        filler.fill(request("Title", "Description", "Location", null, null, null));

        verify(translationService, times(1)).translateEnToAr(anyList());
    }

    @Test
    @DisplayName("leaves Arabic null when the translator returns nulls, so the save still goes through")
    void nullResultsLeaveArabicNull() {
        when(translationService.translateEnToAr(anyList()))
                .thenReturn(Arrays.asList(null, null, null));

        PropertyRequest filled = filler.fill(request("Title", "Description", "Location", null, null, null));

        assertThat(filled.titleAr()).isNull();
        assertThat(filled.descriptionAr()).isNull();
        assertThat(filled.locationAr()).isNull();
        // Everything the admin actually typed is still there to be saved.
        assertThat(filled.title()).isEqualTo("Title");
        assertThat(filled.description()).isEqualTo("Description");
        assertThat(filled.location()).isEqualTo("Location");
    }

    @Test
    @DisplayName("truncates an over-long translated title to the column width")
    void truncatesShortFields() {
        when(translationService.translateEnToAr(anyList())).thenReturn(List.of("ب".repeat(600)));

        PropertyRequest filled = filler.fill(request("Title", null, null, null, null, null));

        assertThat(filled.titleAr()).hasSize(400);
    }
}
