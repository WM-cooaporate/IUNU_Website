package com.iunu.realestate.property;

import com.iunu.realestate.dto.request.PropertyRequest;
import com.iunu.realestate.entity.Property;
import com.iunu.realestate.entity.PropertyType;
import com.iunu.realestate.repository.PropertyRepository;
import com.iunu.realestate.service.ImageStorage;
import com.iunu.realestate.service.impl.PropertyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orphaned-image cleanup on save and delete.
 *
 * <p>The rule that matters: <strong>image storage is content-addressed</strong>
 * - the filename is the SHA-256 of the bytes - so uploading the same photo to
 * two properties produces one file with two references. Deleting it because the
 * property being edited dropped it blanks the image on the other property, and
 * the only sign is a broken image on a page nobody was looking at.
 */
@DisplayName("Property image cleanup")
class PropertyImageCleanupTest {

    private static final String SHARED = "http://localhost:8080/uploads/properties/shared.jpg";
    private static final String ONLY_MINE = "http://localhost:8080/uploads/properties/mine.jpg";

    private PropertyRepository repository;
    private ImageStorage imageStorage;
    private PropertyServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PropertyRepository.class);
        imageStorage = mock(ImageStorage.class);
        service = new PropertyServiceImpl(repository, imageStorage, mock(com.iunu.realestate.service.AuditLogService.class));
    }

    private static Property property(Long id, String cover, String... gallery) {
        return Property.builder()
                .id(id)
                .title("Test")
                .type(PropertyType.RESIDENTIAL)
                .coverImageUrl(cover)
                .imageUrls(new ArrayList<>(List.of(gallery)))
                .published(true)
                .build();
    }

    private static PropertyRequest requestWith(String cover, List<String> gallery) {
        return new PropertyRequest("Test", null, PropertyType.RESIDENTIAL, null, "New Cairo",
                null, null, null, null, null, cover, gallery, true);
    }

    @Test
    @DisplayName("an image still used by another property survives an update that drops it")
    void sharedImageSurvives() {
        Property existing = property(1L, SHARED);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        // Property 2 also points at SHARED.
        when(repository.isImageUsedByOtherProperty(SHARED, 1L)).thenReturn(true);

        service.update(1L, requestWith(null, List.of()));

        verify(imageStorage, never()).deleteIfStored(SHARED);
    }

    @Test
    @DisplayName("an image no other property uses is deleted when it is dropped")
    void removedImageIsDeleted() {
        Property existing = property(1L, ONLY_MINE);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.isImageUsedByOtherProperty(ONLY_MINE, 1L)).thenReturn(false);

        service.update(1L, requestWith(null, List.of()));

        verify(imageStorage).deleteIfStored(ONLY_MINE);
    }

    @Test
    @DisplayName("deleting a property removes only the images nothing else uses")
    void deleteCleansOwnImagesOnly() {
        Property existing = property(1L, ONLY_MINE, SHARED);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.isImageUsedByOtherProperty(SHARED, 1L)).thenReturn(true);
        when(repository.isImageUsedByOtherProperty(ONLY_MINE, 1L)).thenReturn(false);

        service.delete(1L);

        verify(imageStorage).deleteIfStored(ONLY_MINE);
        verify(imageStorage, never()).deleteIfStored(SHARED);
    }

    @Test
    @DisplayName("an image the property keeps is never even considered for deletion")
    void keptImageIsNotChecked() {
        Property existing = property(1L, SHARED);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, requestWith(SHARED, List.of()));

        verify(repository, never()).isImageUsedByOtherProperty(eq(SHARED), anyLong());
        verify(imageStorage, never()).deleteIfStored(any());
    }

    /**
     * The regression this replaced: cleanup used to call findAll() - a full
     * table scan, plus a query per row for its image collection - on every
     * admin save and delete, to answer a question one indexed lookup answers.
     */
    @Test
    @DisplayName("cleanup never loads every property row")
    void doesNotScanTheWholeTable() {
        Property existing = property(1L, ONLY_MINE);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.isImageUsedByOtherProperty(ONLY_MINE, 1L)).thenReturn(false);

        service.update(1L, requestWith(null, List.of()));

        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("the usage check excludes the property being edited")
    void usageCheckExcludesSelf() {
        Property existing = property(7L, ONLY_MINE);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.isImageUsedByOtherProperty(any(), anyLong())).thenReturn(false);

        service.update(7L, requestWith(null, List.of()));

        ArgumentCaptor<Long> excluded = ArgumentCaptor.forClass(Long.class);
        verify(repository).isImageUsedByOtherProperty(eq(ONLY_MINE), excluded.capture());
        // Without this the property's own row counts as a user of the image and
        // nothing is ever cleaned up.
        assertThat(excluded.getValue()).isEqualTo(7L);
    }
}
