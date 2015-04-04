package net.stemmaweb.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.DuplicateModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import org.eclipse.persistence.exceptions.DatabaseException;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * 
 * @author ramona
 *
 **/

@Path("/stemma")
public class Stemma implements IResource {
	
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	/**
	 * Returns GraphML file from specified tradition owned by user
	 * 
	 * @param tratitionId
	 * @param stemmaTitle
	 * @return DOT data
	 */
	@GET
	@Path("/{tradId}/{stemmaTitle}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getStemma(@PathParam("tradId") String tradId,@PathParam("stemmaTitle") String stemmaTitle) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4JStemma(tradId, stemmaTitle);
		
		return resp;
	}

	/**
	 * Returns GraphML file from specified tradition owned by user
	 * 
	 * @param tratitionId
	 * @param stemmaTitle
	 * @return DOT data
	 */
	@POST
	@Path("/{tradId}/{stemmaTitle}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response setStemma(@PathParam("tradId") String tradId) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4J(tradId);
		
		String filename = "upload/" + "output.dot";
		
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return Response.ok(everything).build();
	}
}