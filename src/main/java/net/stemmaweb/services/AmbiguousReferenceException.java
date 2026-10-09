package net.stemmaweb.services;

/**
 * Thrown by {@link DatabaseService#resolveManagedRef} when a REST path-segment reference
 * is not numeric and matches more than one candidate node by name/sigil. Only reachable
 * for pre-existing duplicate data (name/sigil uniqueness is enforced going forward by
 * {@link DatabaseService#ensureNameUnique}).
 *
 * Extends {@link IllegalArgumentException} so every existing {@code catch (IllegalArgumentException e)}
 * block in the codebase already handles it as a 400, the same trick the original entity-id
 * system used for {@link NumberFormatException}.
 */
public class AmbiguousReferenceException extends IllegalArgumentException {
    public AmbiguousReferenceException(String message) {
        super(message);
    }
}
