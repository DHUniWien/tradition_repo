package net.stemmaweb.services;


import java.io.File;
import java.nio.file.Path;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.GraphDatabaseService;

import org.neo4j.test.TestDatabaseManagementServiceBuilder;

/**
 * Creates a global DatabaseService provider, which holds a reference to the
 * database in use.
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {

    private static GraphDatabaseService db;
    private static DatabaseManagementService dbService;

    // Get the database that has been initialized for the app
    public GraphDatabaseServiceProvider() {
    }

    // Connect to a DB at a particular path
    public GraphDatabaseServiceProvider(String db_location) throws KernelException {
    	if (db == null) {
    		if (db_location == null) {
    			dbService = new TestDatabaseManagementServiceBuilder()
    					.impermanent()
    					.setDatabaseRootDirectory(null)
    					.build();
    			db = dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    		} else {
    			dbService = new DatabaseManagementServiceBuilder(Path.of(db_location + "/data/databases/graph.db")).build();

    			File config = new File(db_location + "/conf/neo4j.conf");
    			if (config.exists())
    				db = dbService.database(config.toString());
    			else
    				db = dbService.database("stemma");
    		}
    		registerShutdownHook(dbService);
    	}
    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) throws KernelException {
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

    public static void shutdown() {
    	if (dbService != null) {
    		dbService.shutdownDatabase(db.databaseName());
    		db = null;
    		dbService = null;
    	}
    }

    private static void registerShutdownHook( final DatabaseManagementService managementService ) {
    	Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }
}
