package net.stemmaweb.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.ERelations;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author sevi
 * 
 */
public class Neo4JToDotParser
{
	private GraphDatabaseService db;
	
	OutputStream out = null;

	public Neo4JToDotParser(GraphDatabaseService db){
		this.db = db;
	}

	public Response parseNeo4J(String tradId)
	{
		
		String filename = "upload/" + "output.dot";
    	
		Node startNode = DatabaseService.getStartNode(tradId,db);
    	
    	ExecutionEngine engine = new ExecutionEngine(db);
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		
    		
    		if(startNode==null)
    			return Response.status(Status.NOT_FOUND).build();
    		
    		
    		File file = new File(filename);
    		
    		file.createNewFile();
    		out = new FileOutputStream(file);
    		
    		write("digraph { \n");
    		
    		long edgeId = 0;
    		String subgraph = "";
    		for (Node node : db.traversalDescription().breadthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNode).nodes()) {
    			
    			write("n" + node.getId() + " [label=\"" + node.getProperty("dn15").toString() + "\"];\n");
    			
    			for(Relationship rel : node.getRelationships(Direction.OUTGOING,ERelations.NORMAL))
    			{
	    			if(rel!=null && rel.hasProperty("lexemes"))
	    			{
	    				String[] lexemes = (String[]) rel.getProperty("lexemes");
	    				String lex_str = "";
	    				Iterator<String> it = Arrays.asList(lexemes).iterator();
	    				while(it.hasNext())
	    				{
	    					lex_str += ""+it.next()+"";
	    					if(it.hasNext())
	    						lex_str += ",";
	    				}
	    			
	    				write("n" + rel.getStartNode().getId() + "->" + "n" + rel.getEndNode().getId() + "[label=\""+ lex_str +"\";id=\"e"+ edgeId++ +"\"];\n");
	    			}
    			}
    			for(Relationship rel : node.getRelationships(Direction.OUTGOING, ERelations.RELATIONSHIP))
    			{
    				subgraph += "n" + rel.getStartNode().getId() + "->" + "n" + rel.getEndNode().getId() + "[style=dotted;label=\""+ rel.getProperty("de11").toString() +"\";id=\"e"+ edgeId++ +"\"];\n";
    			}
    			
    		}
    		
    		write("subgraph { edge [dir=none]\n");
    		
    		write(subgraph);
    		
    		write(" } }");
    		
    		out.flush();
    		out.close();
    		
    		tx.success();
    	} catch (IOException e) {
			e.printStackTrace();
			throw new DataBaseException("Could not write file for export");
		}
    	
    	db.shutdown();
    	
		return null;
	}
	
	/**
	 * Parses a stemma of a tradition in a JSON string in DOT format
	 * don't throw error far enough
	 * 
	 * @param tradId
	 * @param stemmaTitle
	 * @return
	 */
	public Response parseNeo4JStemma(String tradId, String stemmaTitle)
	{
		    	
		String output="";

		ExecutionEngine engine = new ExecutionEngine(db);
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		ExecutionResult result = engine.execute("match (t:TRADITION {id:'"+ 
    						tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
    						stemmaTitle +"'}) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext()) {
    	    	db.shutdown();
    			return Response.status(Status.NOT_FOUND).build();
    		}
			Node startNodeStemma = nodes.next();
    		String stemmaType = startNodeStemma.getProperty("type").toString();
    		
    		
    		if(stemmaType.equals("digraph"))
	    		output+="digraph \"" + stemmaTitle + "\" { \n";
    		else
	    		output+="graph \"" + stemmaTitle + "\" { \n";
    		
	    		
	    		Evaluator e = new Evaluator(){
	    			@Override
	    			public Evaluation evaluate(org.neo4j.graphdb.Path path) {

	    				if (path.length() == 0)
	    					return Evaluation.EXCLUDE_AND_CONTINUE;

	    				boolean includes = false;

	    				if (path.endNode().hasProperty("class")) {
	    					includes = true;
	    				}
	    				return Evaluation.of(includes,includes);
	    			}
	    		};
	    		for (Relationship rel : db.traversalDescription().breadthFirst()
						.relationships(ERelations.STEMMA)
						.evaluator(e)
						.uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
						.traverse(startNodeStemma).relationships()) {
	    			if(rel.getStartNode().hasProperty("id")) {
		    			output += rel.getStartNode().getProperty("id").toString() + " [class=\"" + rel.getStartNode().getProperty("class").toString() + "\"];\n";
		    			
		    			
			    			if(rel.getStartNode().hasProperty("id")) {
			    				if(stemmaType.equals("digraph")) 
			    					output += rel.getStartNode().getProperty("id") + "->" + rel.getEndNode().getProperty("id") + ";\n";
			    				else 
			    					output += rel.getStartNode().getProperty("id") + "--" + rel.getEndNode().getProperty("id") + ";\n";
			    			}
	    			}
	    		}
	    		
	    	output += " }";
    		
    		tx.success();
    	}
    	
    	db.shutdown();
    	
		return Response.ok(output).build();
	}
	
	private void write(String str) throws IOException
	{
		out.write(str.getBytes());
	}
}