package net.stemmaweb.services;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.fs.FileUtils;

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
    public GraphDatabaseServiceProvider(String db_location) throws KernelException, IOException {
    	if (db == null) {
    		if (db_location == null) {
				// Delete and recreate a database at a default location
    			Path path = Path.of("/tmp/stemmarest");
    			FileUtils.deleteDirectory(path);

				// This can probably go when the migration is done
    			dbService = new DatabaseManagementServiceBuilder(path)
    					.setConfig(GraphDatabaseInternalSettings.trace_cursors, true)
    					.build();
    		} else {
				Path db_path = Path.of(db_location);
				DatabaseManagementServiceBuilder dbBuilder = new DatabaseManagementServiceBuilder(db_path);
				Path configFile = db_path.resolve("conf/neo4j.conf");
				if (Files.exists(configFile))
					dbBuilder.loadPropertiesFromFile(configFile);
    			dbService = dbBuilder.build();

    		}
			db = dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
    		registerShutdownHook(dbService);
    	}
    }

    // Manage an existing (e.g. test) DB with its management service
    public GraphDatabaseServiceProvider(DatabaseManagementService managementService, GraphDatabaseService existingdb) {
        dbService = managementService;
        db = existingdb;
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

    public static void shutdown() {
    	if (dbService != null) {
    		dbService.shutdown();
    		dbService = null;
    	}
    	db = null;
    }

    private static void registerShutdownHook( final DatabaseManagementService managementService ) {
    	Runtime.getRuntime().addShutdownHook(new Thread(managementService::shutdown));
    }
}
