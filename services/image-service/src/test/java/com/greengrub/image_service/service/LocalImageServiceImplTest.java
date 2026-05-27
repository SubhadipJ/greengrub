package com.greengrub.image_service.service;

import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.LocalImage;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.exception.ImageNotFoundException;
import com.greengrub.image_service.exception.ImageStorageException;
import com.greengrub.image_service.exception.InvalidImageRequestException;
import com.greengrub.image_service.repository.LocalStorageImageRepository;
import com.greengrub.proto.image.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalImageServiceImplTest {

    @Mock
    private LocalStorageImageRepository repository;

    @InjectMocks
    private LocalImageServiceImpl service;

    private LocalImage sampleImage;

    @BeforeEach
    void setUp() {
        sampleImage = LocalImage.builder()
                .imageId("img-001")
                .creatorId("creator-001")
                .creatorType(CreatorType.CUSTOMER)
                .fileName("photo.jpg")
                .contentType("image/jpeg")
                .imageData(new byte[]{1, 2, 3})
                .createdDate(LocalDateTime.of(2024, 6, 1, 12, 0, 0))
                .build();
    }

    // ── uploadImages ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_singleImage_savesAndResponds() {
        when(repository.save(any(LocalImage.class))).thenReturn(sampleImage);
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.CUSTOMER)
                .setFileName("photo.jpg")
                .setContentType("image/jpeg")
                .addImageData(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .build();

        service.uploadImages(request, observer);

        verify(repository).save(any(LocalImage.class));
        ArgumentCaptor<UploadImagesResponse> captor = ArgumentCaptor.forClass(UploadImagesResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertThat(captor.getValue().getMessageList()).hasSize(1);
        assertThat(captor.getValue().getTotalImageCount()).isEqualTo("1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_emptyByteString_skipsImage() {
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.CUSTOMER)
                .setFileName("empty.jpg")
                .addImageData(ByteString.EMPTY)
                .build();

        service.uploadImages(request, observer);

        verify(repository, never()).save(any());
        ArgumentCaptor<UploadImagesResponse> captor = ArgumentCaptor.forClass(UploadImagesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getMessageList()).isEmpty();
        assertThat(captor.getValue().getTotalImageCount()).isEqualTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_multipleImages_savesAll() {
        when(repository.save(any(LocalImage.class))).thenReturn(sampleImage);
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.FOOD_REQUEST)
                .setFileName("multi.jpg")
                .addImageData(ByteString.copyFrom(new byte[]{1}))
                .addImageData(ByteString.copyFrom(new byte[]{2}))
                .build();

        service.uploadImages(request, observer);

        verify(repository, times(2)).save(any(LocalImage.class));
        ArgumentCaptor<UploadImagesResponse> captor = ArgumentCaptor.forClass(UploadImagesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getTotalImageCount()).isEqualTo("2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_saveFails_throwsImageStorageException() {
        when(repository.save(any(LocalImage.class)))
                .thenThrow(new RuntimeException("mongo down"));
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.CUSTOMER)
                .setFileName("photo.jpg")
                .addImageData(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .build();

        assertThatThrownBy(() -> service.uploadImages(request, observer))
                .isInstanceOf(ImageStorageException.class);
    }

    // ── getImagesByCreator ───────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getImagesByCreator_returnsImages() {
        when(repository.findByCreatorId("creator-001")).thenReturn(List.of(sampleImage));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        GetImagesByCreatorRequest request = GetImagesByCreatorRequest.newBuilder()
                .setCreatorId("creator-001")
                .build();

        service.getImagesByCreator(request, observer);

        ArgumentCaptor<GetImagesByCreatorResponse> captor = ArgumentCaptor.forClass(GetImagesByCreatorResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertThat(captor.getValue().getImagesList()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImagesByCreator_noImages_returnsEmpty() {
        when(repository.findByCreatorId(anyString())).thenReturn(List.of());
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        service.getImagesByCreator(
                GetImagesByCreatorRequest.newBuilder().setCreatorId("nobody").build(),
                observer);

        ArgumentCaptor<GetImagesByCreatorResponse> captor = ArgumentCaptor.forClass(GetImagesByCreatorResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getImagesList()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImagesByCreator_repositoryFails_throwsImageStorageException() {
        when(repository.findByCreatorId(anyString()))
                .thenThrow(new RuntimeException("mongo down"));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImagesByCreator(
                GetImagesByCreatorRequest.newBuilder().setCreatorId("creator-001").build(), observer))
                .isInstanceOf(ImageStorageException.class);
    }

    // ── deleteImagesByImageId ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_existingImage_deletesAndResponds() {
        when(repository.findById("img-001")).thenReturn(Optional.of(sampleImage));
        when(repository.deleteByImageId("img-001")).thenReturn(true);
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer);

        verify(repository).deleteByImageId("img-001");
        ArgumentCaptor<DeleteImageByImageIdResponse> captor = ArgumentCaptor.forClass(DeleteImageByImageIdResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Successfully deleted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_notFound_returnsNotFoundMessage() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.deleteByImageId("missing")).thenReturn(false);
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("missing").build(), observer);

        ArgumentCaptor<DeleteImageByImageIdResponse> captor = ArgumentCaptor.forClass(DeleteImageByImageIdResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_emptyId_throwsInvalidImageRequestException() {
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("   ").build(), observer))
                .isInstanceOf(InvalidImageRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_blankId_throwsInvalidImageRequestException() {
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("").build(), observer))
                .isInstanceOf(InvalidImageRequestException.class);
    }

    // ── getImageByImageId ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getImageByImageId_existingImage_returnsImage() {
        when(repository.findById("img-001")).thenReturn(Optional.of(sampleImage));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        service.getImageByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer);

        ArgumentCaptor<GetImagesByCreatorResponse> captor = ArgumentCaptor.forClass(GetImagesByCreatorResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertThat(captor.getValue().getImagesList()).hasSize(1);
        assertThat(captor.getValue().getImages(0).getImageId()).isEqualTo("img-001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImageByImageId_notFound_throwsImageNotFoundException() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImageByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("missing").build(), observer))
                .isInstanceOf(ImageNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImageByImageId_emptyId_throwsInvalidImageRequestException() {
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImageByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("").build(), observer))
                .isInstanceOf(InvalidImageRequestException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImageByImageId_repositoryFails_throwsImageStorageException() {
        when(repository.findById("img-001")).thenThrow(new RuntimeException("mongo error"));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImageByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer))
                .isInstanceOf(ImageStorageException.class);
    }
}
