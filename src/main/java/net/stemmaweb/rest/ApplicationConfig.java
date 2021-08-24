package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;

/**
 * This is the main configuration and setup class.
 * It defines which services will be published by the server
 * @author PSE FS 2015 Team2
 */

public class ApplicationConfig extends Application {
    // Get the correct path to the database location
    private static final String DB_ENV = System.getenv("STEMMAREST_HOME");
    private static final String DB_PATH = DB_ENV == null ? "/var/lib/stemmarest" : DB_ENV;

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(Root.class);

        return s;
    }

}
