package com.iunu.realestate.translation;

import java.util.List;

/** English -> Arabic machine translation for admin-authored content. */
public interface TranslationService {

    /** True when a provider is configured. When false, translate returns nulls for every input. */
    boolean isEnabled();

    /**
     * Translate each text from English to Arabic, preserving order.
     *
     * Returns a list the same size as the input. An element is null when the
     * input was blank or when that element failed. Never throws: a translation
     * problem must never turn into a failed save.
     */
    List<String> translateEnToAr(List<String> texts);
}
