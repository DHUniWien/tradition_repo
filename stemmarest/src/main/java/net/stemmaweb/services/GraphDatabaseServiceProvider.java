package net.stemmaweb.services;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.sun.jersey.spi.inject.SingletonTypeInjectableProvider;

@Provider
public class GraphDatabaseServiceProvider extends SingletonTypeInjectableProvider<Context, GraphDatabaseService>  {
	static GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	public GraphDatabaseServiceProvider() {
		super(GraphDatabaseService.class, dbFactory.newEmbeddedDatabase("database"));
	}
}
