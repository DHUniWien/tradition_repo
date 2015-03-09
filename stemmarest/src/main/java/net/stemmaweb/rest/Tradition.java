package net.stemmaweb.rest;

import java.awt.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Relations;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;


/**
 * 
 * @author ramona
 *
 **/

@Path("/tradition")
public class Tradition {
		public static final String DB_PATH = "database";

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitness() {
		
		List<Witness> witlist= new List<Witness>();

		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
		
		ExecutionEngine engine = new ExecutionEngine(db);
	
		
		try (Transaction tx = db.beginTx()) 
		{
			
			tx.success();
		}
		catch(Exception e)
	    {
	    	e.printStackTrace();
	    }	
		finally
		{
			db.shutdown();
		}
		return Response.status(Status.NOT_FOUND).build();
		
		return Response.ok().build();
	}

}
