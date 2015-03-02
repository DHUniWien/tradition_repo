package net.stemmaweb.stemmaserver;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:8080/";
    public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database
    public static final String PROPS_PATH = "properties/"; // this is the path to StemmaServer/properties/

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() {
        // create a resource config that scans for JAX-RS resources and providers
        // in net.stemmaweb.stemmaserver package
        final ResourceConfig rc = new ResourceConfig().packages("net.stemmaweb.stemmaserver");
        
        /*
         * Moved the initialization of the graph db to the file myresource.java
         * GraphDatabaseService graphDb = new GraphDatabaseFactory()
        .newEmbeddedDatabaseBuilder( DB_PATH )
        .loadPropertiesFromFile( PROPS_PATH + "neo4j.properties" )
        .newGraphDatabase();
        */
        
        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	db.shutdown();

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\nHit enter to stop it...", BASE_URI));
        System.in.read();
        server.stop();
    }
}

