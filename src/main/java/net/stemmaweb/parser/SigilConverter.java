package net.stemmaweb.parser;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts witness sigla that are not valid NCNames (see {@link Util#validateSigil}) into ones
 * that are, for importing data from older versions that did not enforce this. One instance should
 * be used for the whole of an import, so that a given sigil is converted the same way wherever it
 * occurs (witness nodes, and the witness lists on every SEQUENCE link of every section).
 * <p>
 * Valid sigla are never changed. If two different sigla would end up with the same name, whether
 * both are converted or only one, that is an error rather than a silent merge of two witnesses.
 */
public class SigilConverter {
    private static final Pattern EDGE_JUNK =
            Pattern.compile("^[^" + Util.NCNAME_CHARS + "]+|[^" + Util.NCNAME_CHARS + "]+$");
    private static final Pattern INNER_JUNK = Pattern.compile("[^" + Util.NCNAME_CHARS + "]+");
    private static final Pattern START_CHAR = Pattern.compile("^[" + Util.NCNAME_START_CHARS + "]");

    // original sigil -> converted sigil, for the sigla we actually had to change
    private final Map<String, String> converted = new LinkedHashMap<>();
    // every sigil we have handed out -> the original sigil it came from
    private final Map<String, String> owners = new HashMap<>();

    /**
     * Returns a valid sigil for the given one: the same sigil if it is already valid, otherwise a
     * converted form. The same input always gives the same output.
     *
     * @throws IllegalArgumentException if the sigil is null, empty or only whitespace, or if it, or
     * its converted form, would be the same as that of a different sigil seen earlier
     */
    public String convert(String sigil) throws IllegalArgumentException {
        if (sigil == null)
            throw new IllegalArgumentException("A witness sigil may not be null.");
        // Older versions allowed a witness with no sigil. There is nothing sensible to convert it
        // to, and we cannot tell which witness it was meant to be.
        if (sigil.isBlank())
            throw new IllegalArgumentException("A witness sigil may not be empty.");
        if (converted.containsKey(sigil))
            return converted.get(sigil);
        if (Util.isValidSigil(sigil)) {
            claim(sigil, sigil);
            return sigil;
        }
        String result = sanitize(sigil);
        claim(result, sigil);
        converted.put(sigil, result);
        return result;
    }

    /**
     * Whether the given original sigil was converted to something else.
     */
    public boolean wasConverted(String original) {
        return converted.containsKey(original);
    }

    /**
     * The conversions made so far, as a map from converted sigil to original sigil.
     */
    public Map<String, String> conversions() {
        Map<String, String> result = new LinkedHashMap<>();
        converted.forEach((original, newSigil) -> result.put(newSigil, original));
        return result;
    }

    private void claim(String result, String original) {
        String owner = owners.putIfAbsent(result, original);
        if (owner != null && !owner.equals(original))
            throw new IllegalArgumentException(String.format(
                    "Cannot import: the witness sigla \"%s\" and \"%s\" would both be named \"%s\". "
                            + "Sigla must be valid names (letters, digits, underscores, hyphens or periods, "
                            + "not starting with a digit, hyphen or period), and these two cannot be told apart "
                            + "once converted. Please rename one of them in the source data.",
                    owner, original, result));
    }

    private static String sanitize(String sigil) {
        String s = Normalizer.normalize(sigil, Normalizer.Form.NFC).trim();
        s = EDGE_JUNK.matcher(s).replaceAll("");
        s = INNER_JUNK.matcher(s).replaceAll("_");
        if (s.isEmpty())
            return "_";
        if (!START_CHAR.matcher(s).find())
            s = "_" + s;
        return s;
    }
}
