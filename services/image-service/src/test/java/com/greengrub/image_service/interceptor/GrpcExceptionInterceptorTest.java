package com.greengrub.image_service.interceptor;

import com.greengrub.image_service.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GrpcExceptionInterceptorTest {

    private GrpcExceptionInterceptor interceptor;
    private ServerCall<Object, Object> mockCall;
    private Metadata headers;
    private ServerCallHandler<Object, Object> mockHandler;
    private ServerCall.Listener<Object> mockDelegate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        interceptor = new GrpcExceptionInterceptor();
        mockCall = mock(ServerCall.class);
        headers = new Metadata();
        mockHandler = mock(ServerCallHandler.class);
        mockDelegate = mock(ServerCall.Listener.class);

        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName()).thenReturn("ImageService/UploadImages");
        when(mockCall.getMethodDescriptor()).thenReturn(descriptor);
        when(mockHandler.startCall(any(), any())).thenReturn(mockDelegate);
    }

    // ── mapToGrpcStatus paths via onHalfClose ────────────────────────────────

    @Test
    void onHalfClose_imageNotFoundException_closesWithNotFound() {
        doThrow(new ImageNotFoundException("img-001"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.NOT_FOUND), any());
    }

    @Test
    void onHalfClose_invalidImageRequestException_closesWithInvalidArgument() {
        doThrow(new InvalidImageRequestException("ID empty"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.INVALID_ARGUMENT), any());
    }

    @Test
    void onHalfClose_gcpUploadException_closesWithUnavailable() {
        doThrow(new GcpUploadException("gcp failed"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.UNAVAILABLE), any());
    }

    @Test
    void onHalfClose_imageStorageException_closesWithUnavailable() {
        doThrow(new ImageStorageException("storage down", true))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.UNAVAILABLE), any());
    }

    @Test
    void onHalfClose_illegalArgumentException_closesWithInvalidArgument() {
        doThrow(new IllegalArgumentException("bad enum"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.INVALID_ARGUMENT), any());
    }

    @Test
    void onHalfClose_callNotPermittedException_closesWithUnavailable() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        cb.transitionToOpenState();
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);
        doThrow(ex).when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.UNAVAILABLE), any());
    }

    @Test
    void onHalfClose_unknownException_closesWithInternal() {
        doThrow(new RuntimeException("unexpected"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.INTERNAL), any());
    }

    // ── onMessage path ───────────────────────────────────────────────────────

    @Test
    void onMessage_imageNotFoundException_closesWithNotFound() {
        doThrow(new ImageNotFoundException("img-002"))
                .when(mockDelegate).onMessage(any());

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onMessage(new Object());

        verify(mockCall).close(argThat(s -> s.getCode() == Status.Code.NOT_FOUND), any());
    }

    @Test
    void onMessage_noException_doesNotCloseCall() {
        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onMessage(new Object());

        verify(mockCall, never()).close(any(), any());
    }

    // ── happy path — no exception, call not closed ────────────────────────────

    @Test
    void onHalfClose_noException_doesNotCloseCall() {
        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall, never()).close(any(), any());
    }

    // ── description propagation ──────────────────────────────────────────────

    @Test
    void onHalfClose_imageNotFoundException_descriptionContainsImageId() {
        doThrow(new ImageNotFoundException("img-999"))
                .when(mockDelegate).onHalfClose();

        ServerCall.Listener<Object> listener = interceptor.interceptCall(mockCall, headers, mockHandler);
        listener.onHalfClose();

        verify(mockCall).close(argThat(s ->
                s.getCode() == Status.Code.NOT_FOUND &&
                s.getDescription() != null &&
                s.getDescription().contains("img-999")), any());
    }
}
