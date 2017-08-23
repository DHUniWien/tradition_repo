package net.stemmaweb.services;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.shell.ShellSettings;

import java.io.File;

/**
 * Creates a global DatabaseService provider, which holds a reference to the
 * database in use.
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {

    private static GraphDatabaseService db;

    // Get the database that has been initialized for the app
    public GraphDatabaseServiceProvider() {
    }

    // Connect to a DB at a particular path
    public GraphDatabaseServiceProvider(String db_location) {

        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
        db = dbFactory.newEmbeddedDatabaseBuilder(new File(db_location))
                    .setConfig(ShellSettings.remote_shell_enabled, "true")
                    .setConfig(ShellSettings.remote_shell_port, "1337")
                    .newGraphDatabase();
    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) {
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

}
