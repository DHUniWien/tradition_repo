package net.stemmaweb;

/**
 * Utility functions for anything that needs a Response
 * Created by tla on 14/02/2018.
 */

public class Util {

    // Return a JSONified version of an error message
    public static String jsonerror (String message) {
        return jsonresp("error", message);
    }
    public static String jsonresp (String key, String message) {
        return String.format("{\"%s\": \"%s\"}", key, escape(message));
    }
    public static String jsonresp (String key, Long value) {
        return String.format("{\"%s\": %d}", key, value);
    }

    private static String escape(String raw) {
        String escaped = raw;
        escaped = escaped.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("\b", "\\b");
        escaped = escaped.replace("\f", "\\f");
        escaped = escaped.replace("\n", "\\n");
        escaped = escaped.replace("\r", "\\r");
        escaped = escaped.replace("\t", "\\t");
        // TODO: escape other non-printing characters using uXXXX notation
        return escaped;
    }

}
