package net.stemmaweb.parser;

import jakarta.ws.rs.core.Response;

public class StemmaImportException extends RuntimeException {
    private final Response.Status status;
    public StemmaImportException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }
}
