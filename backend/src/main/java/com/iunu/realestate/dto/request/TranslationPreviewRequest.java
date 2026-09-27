package com.iunu.realestate.dto.request;

import jakarta.validation.constraints.Size;

/**
 * The English side of the admin form, sent by the "Translate from English"
 * button. Every field is optional - the admin may preview a title before the
 * description exists. Limits match PropertyRequest's English fields.
 */
public record TranslationPreviewRequest(
        @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @Size(max = 200) String location
) {
}
