package net.stemmaweb.services;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.shell.ShellSettings;

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
            db = dbFactory.newEmbeddedDatabaseBuilder(db_location)
                    .setConfig(ShellSettings.remote_shell_enabled, "true")
                    .setConfig(ShellSettings.remote_shell_port, "1337")
                    .newGraphDatabase();
        }
    }

    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) {
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

}
