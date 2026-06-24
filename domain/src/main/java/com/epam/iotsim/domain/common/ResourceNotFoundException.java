package com.epam.iotsim.domain.common;

/** A requested resource does not exist. Mapped to HTTP 404 by the API layer. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}
