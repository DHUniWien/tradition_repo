package net.stemmaweb.services;

import org.neo4j.graphdb.GraphDatabaseService;

import com.sun.jersey.spi.inject.Injectable;

public class InjectableGraphDatabaseService implements Injectable<GraphDatabaseService> {

	GraphDatabaseService db;
	
	public InjectableGraphDatabaseService(GraphDatabaseService db) {
		this.db = db;
	}
	
	@Override
	public GraphDatabaseService getValue() {
		return db;
	}

}
