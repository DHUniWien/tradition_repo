package net.stemmaweb.services;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.internal.kernel.api.exceptions.KernelException;

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
    public GraphDatabaseServiceProvider(String db_location) throws KernelException {

        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
        GraphDatabaseBuilder dbbuilder = dbFactory.newEmbeddedDatabaseBuilder(new File(db_location + "/data/databases/graph.db"));
        File config = new File(db_location + "/conf/neo4j.conf");
        if (config.exists())
            db = dbbuilder.loadPropertiesFromFile(config.toString()).newGraphDatabase();
        else
            db = dbbuilder.newGraphDatabase();

    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) throws KernelException {
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

}
