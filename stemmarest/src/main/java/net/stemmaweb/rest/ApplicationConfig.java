package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.core.Application;

import net.stemmaweb.services.DatabaseService;

import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;


public class ApplicationConfig extends Application {
	
	public static final String DB_PATH = "database"; // this is the local path to StemmaServer/database
	static boolean init = false;
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<Class<?>>();
        s.add(MyResource.class);
        s.add(Witness.class);
        s.add(User.class);
        s.add(Tradition.class);
        s.add(Relation.class);
        if(!init)
        	initializeApp();
        
        return s;
    }
    
    public void initializeApp()
    {
    	init = true;
    	
    	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	
    	DatabaseService dbService = new DatabaseService(db);
    	dbService.createRootNode();
    }

}