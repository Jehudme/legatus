package com.legatus.studio.transport;

import com.legatus.studio.protocol.RestDto;

/**
 * Exception thrown when an HTTP REST call returns a non-2xx status code.
 */
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final RestDto.ApiErrorResponse errorResponse;

    public ApiException(int statusCode, String message, RestDto.ApiErrorResponse errorResponse) {
        super(message);
        this.statusCode = statusCode;
        this.errorResponse = errorResponse;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public RestDto.ApiErrorResponse getErrorResponse() {
        return errorResponse;
    }

    @Override
    public String toString() {
        if (errorResponse != null) {
            return "ApiException HTTP " + statusCode + " [" + errorResponse.error_code() + "]: " + errorResponse.message();
        }
        return "ApiException HTTP " + statusCode + ": " + getMessage();
    }
}
