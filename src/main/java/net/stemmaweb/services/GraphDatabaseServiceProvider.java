package net.stemmaweb.services;


import java.io.File;
import java.nio.file.Path;

import org.neo4j.common.DependencyResolver.SelectionStrategy;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
import org.neo4j.gds.wcc.WccStreamProc;
//import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
//import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
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

//        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
//        GraphDatabaseBuilder dbbuilder = dbFactory.newEmbeddedDatabaseBuilder(new File(db_location + "/data/databases/graph.db"));
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
    	registerExtensions();

    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) throws KernelException {
        db = existingdb;
        registerExtensions();
    }

    public GraphDatabaseService getDatabase(){
        return db;
    }

    // Register any extensions we need in the database
    private static void registerExtensions() throws KernelException {
        GraphDatabaseAPI api = (GraphDatabaseAPI) db;
        // See if our procedure is already registered
        api.getDependencyResolver()
                .resolveDependency(GlobalProcedures.class, SelectionStrategy.SINGLE)
//                .registerProcedure(UnionGraphIntersectFactory.class, true);
                .registerProcedure(WccStreamProc.class, true);
    }
    
    public static void shutdown() {
    	if (dbService != null) {
    		dbService.shutdownDatabase(db.databaseName());
    		db = null;
    		dbService = null;
    	}
    }

    private static void registerShutdownHook( final DatabaseManagementService managementService ) {
    	Runtime.getRuntime().addShutdownHook( new Thread()
    	{
    		@Override
    		public void run()
    		{
    			managementService.shutdown();
    		}
    	} );
    }
}
