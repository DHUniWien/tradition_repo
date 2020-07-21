package net.stemmaweb.services;


import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphalgo.UnionFindProc;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.File;

/**
 * Creates a global DatabaseService provider, which holds a reference to the
 * database in use.
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {

    private static DatabaseManagementService managementService;
    private static GraphDatabaseService db;

    // Get the database that has been initialized for the app
    public GraphDatabaseServiceProvider() {
    }

    // Connect to a DB at a particular path
    public GraphDatabaseServiceProvider(String db_location, String db_name) throws KernelException {
        File config = new File(db_location + "/conf/neo4j.conf");
        File dbdata = new File(db_location + "/data");

        if (config.exists())
            managementService = new DatabaseManagementServiceBuilder(dbdata)
                    .loadPropertiesFromFile(config.getAbsolutePath()).build();
        else
            managementService = new DatabaseManagementServiceBuilder(dbdata).build();
        db = managementService.database(db_name);
        registerExtensions();

    }

    // Manage an existing (e.g. test) DB
    public GraphDatabaseServiceProvider(GraphDatabaseService existingdb) throws KernelException {
        db = existingdb;
        registerExtensions();
    }

    public DatabaseManagementService getManagementService() { return managementService; }

    public GraphDatabaseService getDatabase(){
        return db;
    }

    // Register any extensions we need in the database
    private static void registerExtensions() throws KernelException {
        GraphDatabaseAPI api = (GraphDatabaseAPI) db;
        // See if our procedure is already registered
        api.getDependencyResolver()
                .resolveDependency(GlobalProcedures.class)
                .registerProcedure(UnionFindProc.class, true);
    }

}
