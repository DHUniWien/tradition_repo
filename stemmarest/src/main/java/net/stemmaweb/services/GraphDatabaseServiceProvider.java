package net.stemmaweb.services;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Creates a global DatabaseService provider
 * @author jakob
 *
 */
public class GraphDatabaseServiceProvider {
	private static GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	private static GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
	
	public GraphDatabaseService getDatabase(){
		return db;
	}

}
