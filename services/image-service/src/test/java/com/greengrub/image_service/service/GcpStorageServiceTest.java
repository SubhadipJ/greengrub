package com.greengrub.image_service.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.greengrub.image_service.exception.GcpUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GcpStorageServiceTest {

    @Mock
    private Storage storage;

    @InjectMocks
    private GcpStorageService gcpStorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gcpStorageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(gcpStorageService, "storageBaseUrl", "https://storage.googleapis.com");
    }

    // ── uploadImage — success paths ───────────────────────────────────────────

    @Test
    void uploadImage_success_returnsPublicUrl() {
        byte[] bytes = {1, 2, 3};
        when(storage.create(any(BlobInfo.class), eq(bytes))).thenReturn(null);

        String url = gcpStorageService.uploadImage(bytes, "img-001", "creator-001", "image/jpeg");

        assertThat(url).isEqualTo("https://storage.googleapis.com/test-bucket/creator-001/img-001.jpg");
    }

    @Test
    void uploadImage_urlContainsBucketAndCreatorAndImageId() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);

        String url = gcpStorageService.uploadImage(new byte[]{1}, "img-42", "user-99", "image/png");

        assertThat(url).contains("test-bucket");
        assertThat(url).contains("user-99");
        assertThat(url).contains("img-42");
    }

    @Test
    void uploadImage_nullContentType_defaultsToImageJpeg() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);
        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);

        gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", null);

        verify(storage).create(captor.capture(), any(byte[].class));
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadImage_blankContentType_defaultsToImageJpeg() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);
        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);

        gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "   ");

        verify(storage).create(captor.capture(), any(byte[].class));
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadImage_explicitContentType_isPreserved() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);
        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);

        gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "image/png");

        verify(storage).create(captor.capture(), any(byte[].class));
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    void uploadImage_blobIdMatchesExpectedPath() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);
        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);

        gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "image/jpeg");

        verify(storage).create(captor.capture(), any(byte[].class));
        BlobId blobId = captor.getValue().getBlobId();
        assertThat(blobId.getBucket()).isEqualTo("test-bucket");
        assertThat(blobId.getName()).isEqualTo("creator-001/img-001.jpg");
    }

    @Test
    void uploadImage_storageCalledOnce() {
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(null);

        gcpStorageService.uploadImage(new byte[]{1, 2}, "img-001", "creator-001", "image/jpeg");

        verify(storage, times(1)).create(any(BlobInfo.class), any(byte[].class));
    }

    // ── uploadImage — failure paths ───────────────────────────────────────────

    @Test
    void uploadImage_storageThrows_throwsGcpUploadException() {
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new RuntimeException("GCS unavailable"));

        assertThatThrownBy(() ->
                gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "image/jpeg"))
                .isInstanceOf(GcpUploadException.class);
    }

    @Test
    void uploadImage_storageThrows_exceptionMessageContainsImageId() {
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new RuntimeException("GCS unavailable"));

        assertThatThrownBy(() ->
                gcpStorageService.uploadImage(new byte[]{1}, "img-XYZ", "creator-001", "image/jpeg"))
                .isInstanceOf(GcpUploadException.class)
                .hasMessageContaining("img-XYZ");
    }

    @Test
    void uploadImage_storageThrows_causeIsPreserved() {
        RuntimeException cause = new RuntimeException("network error");
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenThrow(cause);

        assertThatThrownBy(() ->
                gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "image/jpeg"))
                .isInstanceOf(GcpUploadException.class)
                .hasCause(cause);
    }

    @Test
    void uploadImage_gcpUploadException_isRetryable() {
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new RuntimeException("timeout"));

        GcpUploadException ex = catchThrowableOfType(
                () -> gcpStorageService.uploadImage(new byte[]{1}, "img-001", "creator-001", "image/jpeg"),
                GcpUploadException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.isRetryable()).isTrue();
    }
}
