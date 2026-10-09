package net.stemmaweb.services;

/**
 * Thrown by {@link DatabaseService#ensureNameUnique} when a candidate name/sigil is
 * already in use by a different sibling node of the same label under the same parent.
 * Callers are expected to catch this explicitly and map it to 409 Conflict.
 */
public class NameConflictException extends RuntimeException {
    public NameConflictException(String message) {
        super(message);
    }
}
