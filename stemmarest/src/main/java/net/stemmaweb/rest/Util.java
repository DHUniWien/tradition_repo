package net.stemmaweb.rest;

/**
 * Utility functions for the REST modules
 * Created by tla on 14/02/2018.
 */

public class Util {

    // Return a JSONified version of an error message
    static String jsonerror (String message) {
        return jsonresp("error", message);
    }

    static String jsonresp (String key, String message) {
        return String.format("{\"%s\": \"%s\"}", key, message);
    }

    static String jsonresp (String key, Long value) {
        return String.format("{\"%s\": %d}", key, value);
    }
}
