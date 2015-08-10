package net.stemmaweb.services;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * Creates a global DatabaseService provider
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {

    private static GraphDatabaseService db;

    public GraphDatabaseServiceProvider() {

        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

        if(db == null){
            String db_location = System.getenv("DATABASE_HOME");
            if(db_location == null)
                db_location = "/var/lib/stemmarest";
            db = dbFactory.newEmbeddedDatabase(db_location);
        }
    }

    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) {
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

}
