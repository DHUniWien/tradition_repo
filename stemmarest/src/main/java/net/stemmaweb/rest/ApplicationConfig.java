package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.Application;

import net.stemmaweb.services.DatabaseService;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class ApplicationConfig extends Application {
	
	public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database
		
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<Class<?>>();
        s.add(MyResource.class);
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