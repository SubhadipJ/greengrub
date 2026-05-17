package com.greengrub.image_service.interceptor;

import com.greengrub.image_service.exception.GcpUploadException;
import com.greengrub.image_service.exception.ImageNotFoundException;
import com.greengrub.image_service.exception.ImageStorageException;
import com.greengrub.image_service.exception.InvalidImageRequestException;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@Slf4j
@GrpcGlobalServerInterceptor
public class GrpcExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    handleException(e, call, headers);
                }
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Exception e) {
                    handleException(e, call, headers);
                }
            }
        };
    }

    private <ReqT, RespT> void handleException(Exception e, ServerCall<ReqT, RespT> call, Metadata headers) {
        Status status = mapToGrpcStatus(e);
        log.error("[gRPC] {} on method {}: {}",
                status.getCode(), call.getMethodDescriptor().getFullMethodName(), e.getMessage(), e);
        call.close(status, headers);
    }

    private Status mapToGrpcStatus(Exception e) {
        if (e instanceof ImageNotFoundException ex) {
            return Status.NOT_FOUND.withDescription(ex.getMessage());
        }
        if (e instanceof InvalidImageRequestException ex) {
            return Status.INVALID_ARGUMENT.withDescription(ex.getMessage());
        }
        if (e instanceof GcpUploadException ex) {
            return Status.UNAVAILABLE.withDescription(ex.getMessage());
        }
        if (e instanceof ImageStorageException ex) {
            return Status.UNAVAILABLE.withDescription(ex.getMessage());
        }
        if (e instanceof IllegalArgumentException ex) {
            return Status.INVALID_ARGUMENT.withDescription(ex.getMessage());
        }
        // Resilience4j wraps circuit breaker open in CallNotPermittedException
        if (e.getClass().getSimpleName().equals("CallNotPermittedException")) {
            return Status.UNAVAILABLE.withDescription("Service temporarily unavailable - circuit breaker open");
        }
        return Status.INTERNAL.withDescription("Internal server error: " + e.getMessage());
    }
}
