package net.stemmaweb.services;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Creates a global DatabaseService provider
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphDatabaseServiceProvider {
	
	private static GraphDatabaseService db;
	
	public GraphDatabaseServiceProvider() {

		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

		if(db==null){
			db = dbFactory.newEmbeddedDatabase("database");
		}
	}
	
	public GraphDatabaseService getDatabase(){
		return db;
	}

	public static void setImpermanentDatabase(){
		db =  new TestGraphDatabaseFactory().newImpermanentDatabase();
	}

}
