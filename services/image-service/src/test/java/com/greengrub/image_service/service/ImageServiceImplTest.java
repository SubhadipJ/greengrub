package com.greengrub.image_service.service;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.google.protobuf.ByteString;
import com.greengrub.image_service.entity.Image;
import com.greengrub.image_service.enumeration.CreatorType;
import com.greengrub.image_service.exception.ImageNotFoundException;
import com.greengrub.image_service.exception.ImageStorageException;
import com.greengrub.image_service.exception.InvalidImageRequestException;
import com.greengrub.proto.image.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceImplTest {

    @Mock
    private FirestoreTemplate firestoreTemplate;

    @Mock
    private GcpStorageService gcpStorageService;

    @InjectMocks
    private ImageServiceImpl service;

    private Image sampleImage;

    @BeforeEach
    void setUp() {
        sampleImage = Image.builder()
                .imageId("img-001")
                .creatorId("creator-001")
                .creatorType(CreatorType.CUSTOMER)
                .fileName("photo.jpg")
                .contentType("image/jpeg")
                .imageUrl("https://storage.googleapis.com/bucket/img-001")
                .createdDate(Timestamp.parseTimestamp("2024-01-15T10:30:00Z"))
                .build();
    }

    // ── uploadImages ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_singleImage_savesAndResponds() {
        when(firestoreTemplate.save(any(Image.class))).thenReturn(Mono.just(sampleImage));
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.CUSTOMER)
                .setFileName("photo.jpg")
                .setContentType("image/jpeg")
                .addImageData(ByteString.copyFrom(new byte[]{1, 2, 3}))
                .build();

        service.uploadImages(request, observer);

        verify(firestoreTemplate).save(any(Image.class));
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

        verify(firestoreTemplate, never()).save(any());
        ArgumentCaptor<UploadImagesResponse> captor = ArgumentCaptor.forClass(UploadImagesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getMessageList()).isEmpty();
        assertThat(captor.getValue().getTotalImageCount()).isEqualTo("0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_multipleImages_savesAll() {
        when(firestoreTemplate.save(any(Image.class))).thenReturn(Mono.just(sampleImage));
        StreamObserver<UploadImagesResponse> observer = mock(StreamObserver.class);

        UploadImagesRequest request = UploadImagesRequest.newBuilder()
                .setCreatorId("creator-001")
                .setCreatorType(com.greengrub.proto.image.CreatorType.FOOD_REQUEST)
                .setFileName("multi.jpg")
                .addImageData(ByteString.copyFrom(new byte[]{1}))
                .addImageData(ByteString.copyFrom(new byte[]{2}))
                .build();

        service.uploadImages(request, observer);

        verify(firestoreTemplate, times(2)).save(any(Image.class));
        ArgumentCaptor<UploadImagesResponse> captor = ArgumentCaptor.forClass(UploadImagesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getTotalImageCount()).isEqualTo("2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadImages_firestoreFails_throwsImageStorageException() {
        when(firestoreTemplate.save(any(Image.class))).thenReturn(Mono.error(new RuntimeException("firestore down")));
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
        when(firestoreTemplate.findAll(Image.class)).thenReturn(Flux.just(sampleImage));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        service.getImagesByCreator(
                GetImagesByCreatorRequest.newBuilder().setCreatorId("creator-001").build(),
                observer);

        ArgumentCaptor<GetImagesByCreatorResponse> captor = ArgumentCaptor.forClass(GetImagesByCreatorResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertThat(captor.getValue().getImagesList()).hasSize(1);
        assertThat(captor.getValue().getImages(0).getImageId()).isEqualTo("img-001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getImagesByCreator_noImages_returnsEmpty() {
        when(firestoreTemplate.findAll(Image.class)).thenReturn(Flux.empty());
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
    void getImagesByCreator_firestoreFails_throwsImageStorageException() {
        when(firestoreTemplate.findAll(Image.class)).thenReturn(Flux.error(new RuntimeException("firestore down")));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImagesByCreator(
                GetImagesByCreatorRequest.newBuilder().setCreatorId("creator-001").build(),
                observer))
                .isInstanceOf(ImageStorageException.class);
    }

    // ── deleteImagesByImageId ─────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_existingImage_deletesAndResponds() {
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.just(sampleImage));
        when(firestoreTemplate.delete(any(Mono.class))).thenReturn(Mono.empty());
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer);

        verify(firestoreTemplate).delete(any(Mono.class));
        ArgumentCaptor<DeleteImageByImageIdResponse> captor = ArgumentCaptor.forClass(DeleteImageByImageIdResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Successfully deleted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_notFound_returnsNotFoundMessage() {
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.empty());
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("missing").build(), observer);

        verify(firestoreTemplate, never()).delete(any());
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

    @Test
    @SuppressWarnings("unchecked")
    void deleteImagesByImageId_firestoreFails_throwsImageStorageException() {
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.error(new RuntimeException("firestore down")));
        StreamObserver<DeleteImageByImageIdResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.deleteImagesByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer))
                .isInstanceOf(ImageStorageException.class);
    }

    // ── getImageByImageId ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void getImageByImageId_existingImage_returnsImage() {
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.just(sampleImage));
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
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.empty());
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
    void getImageByImageId_firestoreFails_throwsImageStorageException() {
        when(firestoreTemplate.findById(any(Mono.class), eq(Image.class))).thenReturn(Mono.error(new RuntimeException("firestore down")));
        StreamObserver<GetImagesByCreatorResponse> observer = mock(StreamObserver.class);

        assertThatThrownBy(() -> service.getImageByImageId(
                ImageByImageIdRequest.newBuilder().setImageId("img-001").build(), observer))
                .isInstanceOf(ImageStorageException.class);
    }
}
