package net.stemmaweb.services;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

/**
 * Creates a global DatabaseService provider
 * @author jakob
 *
 */
public class GraphDatabaseServiceProvider {
	private static GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	private static GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
	
	public static GraphDatabaseService getDatabase(){
		return db;
	}
}
