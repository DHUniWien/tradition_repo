package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.Application;

import net.stemmaweb.services.DatabaseService;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * This is the main configuration and setup class.
 * It defines which services will be published by the server
 * @author PSE FS 2015 Team2
 */

public class ApplicationConfig extends Application {

    // private static final String DB_PATH_ = "database"; // this is the local path to stemmarest/database
    private static final String DB_PATH = "/usr/local/neo4j/data/graph.db";
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<>();
        s.add(Witness.class);
        s.add(User.class);
        s.add(Tradition.class);
        s.add(Relation.class);
        s.add(Stemma.class);
        s.add(Reading.class);
        
        return s;
    }
    
    @PostConstruct
    public void initializeApp()
    {
        GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
        GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

        DatabaseService.createRootNode(db);

        db.shutdown();
    }

}