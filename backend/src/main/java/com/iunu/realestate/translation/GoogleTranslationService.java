package com.iunu.realestate.translation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.iunu.realestate.metrics.AbuseMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Google Cloud Translation API (Basic / v2), English to Arabic.
 *
 * Three things this class is careful about:
 *
 * 1. It never throws. Translation is a nicety layered onto an admin save; a
 *    403, a timeout or a malformed response leaves the Arabic null and the
 *    save goes through unchanged.
 * 2. It never sends the API key in the URL - only in the X-goog-api-key
 *    header - and never logs the key or the text being translated.
 * 3. It keeps each request under Google's recommended size by splitting long
 *    text on paragraph, then line, then sentence boundaries, and rejoins the
 *    translated pieces with the very separator it split on, so the paragraph
 *    structure of a description survives the round trip.
 */
public class GoogleTranslationService implements TranslationService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTranslationService.class);

    private static final String PATH = "/language/translate/v2";

    /**
     * Google recommends staying near 5,000 characters per request; a
     * description is allowed up to 20,000. 4,500 leaves room for the JSON
     * envelope without splitting text that does not need splitting.
     */
    static final int MAX_CHARS = 4500;

    /** Google's cap on the number of "q" values in one v2 request. */
    static final int MAX_ITEMS = 100;

    /** Tried in order; the first one that actually divides the text wins. */
    private static final String[] SEPARATORS = {"\n\n", "\n", ". "};

    private final RestClient restClient;
    private final GoogleTranslateProperties properties;
    private final TranslationBudget budget;
    private final AbuseMetrics metrics;

    public GoogleTranslationService(
            RestClient.Builder builder,
            GoogleTranslateProperties properties,
            TranslationBudget budget,
            AbuseMetrics metrics
    ) {
        this.properties = properties;
        this.budget = budget;
        this.metrics = metrics;
        this.restClient = builder.build();
    }

    @PostConstruct
    void logConfiguration() {
        if (isEnabled()) {
            log.info("Google translation enabled: English project content will be auto-translated to Arabic on save");
        } else {
            log.info("Google translation disabled: GOOGLE_TRANSLATE_API_KEY not set");
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    @Override
    public List<String> translateEnToAr(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>(Collections.nCopies(texts.size(), null));
        if (!isEnabled()) {
            return results;
        }

        // Every chunk of every input, flattened, so short strings can share a
        // request and a long description can span several.
        List<Chunk> chunks = new ArrayList<>();
        List<Split> splits = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                continue; // stays null; nothing is sent for a blank input
            }
            Split split = split(text);
            splits.set(i, split);
            for (int part = 0; part < split.parts().size(); part++) {
                chunks.add(new Chunk(split, part, split.parts().get(part)));
            }
        }

        for (List<Chunk> batch : batch(chunks)) {
            translateBatch(batch);
        }

        for (int i = 0; i < texts.size(); i++) {
            Split split = splits.get(i);
            if (split != null && split.isComplete()) {
                results.set(i, split.join());
            }
        }
        return results;
    }

    /** One HTTP call for a batch; on any failure the batch's chunks stay untranslated. */
    private void translateBatch(List<Chunk> batch) {
        List<String> payload = batch.stream().map(Chunk::source).toList();
        long billableChars = payload.stream().mapToLong(String::length).sum();

        // Reserved before the call, not after: an over-budget batch must not be
        // sent at all. Leaving the chunks untranslated is the same outcome as
        // any other failure here - the field stays null and the save succeeds.
        if (!budget.tryReserve(billableChars)) {
            return;
        }
        metrics.translationCall(billableChars);

        try {
            TranslateResponse response = restClient.post()
                    .uri(properties.baseUrlOrDefault() + PATH)
                    .header("X-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "q", payload,
                            "source", "en",
                            "target", "ar",
                            // Without this Google treats the input as HTML: it
                            // returns entities such as &#39; and collapses the
                            // line breaks a description depends on.
                            "format", "text"))
                    .retrieve()
                    .body(TranslateResponse.class);

            List<Translation> translations = (response == null || response.data() == null)
                    ? List.of()
                    : response.data().translationsOrEmpty();

            if (translations.size() != batch.size()) {
                log.warn("Arabic translation failed: expected {} translations, got {}",
                        batch.size(), translations.size());
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).accept(translations.get(i).translatedText());
            }
        } catch (Exception exception) {
            // Deliberately the message only: the API key is in the headers and
            // the project copy is in the body, and neither belongs in a log.
            log.warn("Arabic translation failed: {}", exception.getMessage());
        }
    }

    /** Groups chunks into requests of at most MAX_ITEMS entries and MAX_CHARS characters. */
    static List<List<Chunk>> batch(List<Chunk> chunks) {
        List<List<Chunk>> batches = new ArrayList<>();
        List<Chunk> current = new ArrayList<>();
        int size = 0;
        for (Chunk chunk : chunks) {
            if (chunk.source().isBlank()) {
                // A separator run can leave an empty piece. It needs no
                // translation and Google rejects empty "q" values.
                chunk.accept(chunk.source());
                continue;
            }
            int length = chunk.source().length();
            if (!current.isEmpty() && (current.size() >= MAX_ITEMS || size + length > MAX_CHARS)) {
                batches.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(chunk);
            size += length;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    /**
     * Splits text into pieces of at most MAX_CHARS, remembering the separator
     * used between each pair so join() can put the text back together exactly
     * as it was.
     */
    static Split split(String text) {
        List<String> parts = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        splitInto(text, "", 0, parts, separators);
        return new Split(parts, separators);
    }

    private static void splitInto(String text, String separatorBefore, int level,
                                  List<String> parts, List<String> separators) {
        if (text.length() <= MAX_CHARS) {
            add(text, separatorBefore, parts, separators);
            return;
        }
        if (level >= SEPARATORS.length) {
            // Nothing left to split on - a single enormous unbroken run. A hard
            // cut mid-sentence translates worse than a clean break, but it is
            // the only way to stay inside the request limit.
            for (int start = 0; start < text.length(); start += MAX_CHARS) {
                String piece = text.substring(start, Math.min(text.length(), start + MAX_CHARS));
                add(piece, start == 0 ? separatorBefore : "", parts, separators);
            }
            return;
        }

        String separator = SEPARATORS[level];
        List<String> pieces = Arrays.asList(text.split(java.util.regex.Pattern.quote(separator), -1));
        if (pieces.size() == 1) {
            splitInto(text, separatorBefore, level + 1, parts, separators);
            return;
        }

        String pending = separatorBefore;
        StringBuilder buffer = new StringBuilder();
        for (String piece : pieces) {
            int candidate = buffer.isEmpty() ? piece.length() : buffer.length() + separator.length() + piece.length();
            if (candidate <= MAX_CHARS) {
                if (!buffer.isEmpty()) {
                    buffer.append(separator);
                }
                buffer.append(piece);
                continue;
            }
            if (!buffer.isEmpty()) {
                add(buffer.toString(), pending, parts, separators);
                pending = separator;
                buffer.setLength(0);
            }
            if (piece.length() <= MAX_CHARS) {
                buffer.append(piece);
            } else {
                splitInto(piece, pending, level + 1, parts, separators);
                pending = separator;
            }
        }
        if (!buffer.isEmpty()) {
            add(buffer.toString(), pending, parts, separators);
        }
    }

    private static void add(String part, String separatorBefore, List<String> parts, List<String> separators) {
        if (!parts.isEmpty()) {
            separators.add(separatorBefore);
        }
        parts.add(part);
    }

    /** One piece of one input text, wired back to the Split it belongs to. */
    static final class Chunk {
        private final Split split;
        private final int partIndex;
        private final String source;

        Chunk(Split split, int partIndex, String source) {
            this.split = split;
            this.partIndex = partIndex;
            this.source = source;
        }

        String source() {
            return source;
        }

        void accept(String value) {
            split.accept(partIndex, value);
        }
    }

    /**
     * The pieces of one input text plus the separators between them. Pieces are
     * overwritten in place as translations arrive; isComplete() is false while
     * any piece is still missing, which is what makes a partial failure leave
     * the whole field null rather than half-Arabic.
     */
    static final class Split {
        private final List<String> parts;
        private final List<String> separators;

        Split(List<String> parts, List<String> separators) {
            this.parts = parts;
            this.separators = separators;
        }

        List<String> parts() {
            return parts;
        }

        private final List<String> translated = new ArrayList<>();

        boolean isComplete() {
            return translated.size() == parts.size() && translated.stream().allMatch(java.util.Objects::nonNull);
        }

        void accept(int index, String value) {
            while (translated.size() <= index) {
                translated.add(null);
            }
            translated.set(index, value);
        }

        String join() {
            StringBuilder joined = new StringBuilder(translated.get(0));
            for (int i = 1; i < translated.size(); i++) {
                joined.append(separators.get(i - 1)).append(translated.get(i));
            }
            return joined.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TranslateResponse(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Translation> translations) {
        List<Translation> translationsOrEmpty() {
            return translations == null ? List.of() : translations;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Translation(String translatedText) {
    }
}
