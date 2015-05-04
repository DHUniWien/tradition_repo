package net.stemmaweb.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.printer.GraphViz;
import net.stemmaweb.rest.ERelations;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * 
 * This class provides methods for exporting Dot File from Neo4J
 * 
 * @author PSE FS 2015 Team2
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

    	try (Transaction tx = db.beginTx()) 
    	{	
    		if(startNode==null)
    			return Response.status(Status.NOT_FOUND).build();
    		
    		File file = new File(filename);
    		
    		file.createNewFile();
    		out = new FileOutputStream(file);
    		
    		write("digraph { ");
    		
    		long edgeId = 0;
    		String subgraph = "";
    		for (Node node : db.traversalDescription().breadthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNode).nodes()) {
    			
    			write("n" + node.getId() + " [label=\"" + node.getProperty("text").toString() + "\"];");
    			
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
	    			
	    				write("n" + rel.getStartNode().getId() + "->" + "n" + rel.getEndNode().getId() + "[label=\""+ lex_str +"\";id=\"e"+ edgeId++ +"\"];");
	    			}
    			}
    			for(Relationship rel : node.getRelationships(Direction.OUTGOING, ERelations.RELATIONSHIP))
    			{
    				subgraph += "n" + rel.getStartNode().getId() + "->" + "n" + rel.getEndNode().getId() + "[style=dotted;label=\""+ rel.getProperty("type").toString() +"\";id=\"e"+ edgeId++ +"\"];";
    			}
    		}
    		
    		write("subgraph { edge [dir=none]");
    		
    		write(subgraph);
    		
    		write(" } }");
    		
    		out.flush();
    		out.close();
    		
    		tx.success();
    	} catch (IOException e) {
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Could not write file for export").build();
		}
    	
    	writePNGFromDotFile(filename,"upload/file");
    	writeSVGFromDotFile(filename,"upload/file");
    	
		return Response.ok().build();
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
		String outputNodes="";
		String outputRelationships="";
		ArrayList<Node> nodes = new ArrayList<Node>();
		ArrayList<Relationship> relationships = new ArrayList<Relationship>();


		ExecutionEngine engine = new ExecutionEngine(db);
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		ExecutionResult result = engine.execute("match (t:TRADITION {id:'"+ 
    						tradId + "'})-[:STEMMA]->(n:STEMMA { name:'" + 
    						stemmaTitle +"'}) return n");
    		Iterator<Node> stNodes = result.columnAs("n");
    		
    		
    		if(!stNodes.hasNext()) {
    	    	db.shutdown();
    			return Response.status(Status.NOT_FOUND).build();
    		}
			Node startNodeStemma = stNodes.next();
    		String stemmaType = startNodeStemma.getProperty("type").toString();
    		
    		
    		if(stemmaType.equals("digraph"))
	    		outputNodes+="digraph \"" + stemmaTitle + "\" {";
    		else
	    		outputNodes+="graph \"" + stemmaTitle + "\" {";
    		
	    		
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
	    		for (Path path : db.traversalDescription().breadthFirst()
						.relationships(ERelations.STEMMA)
						.evaluator(e)
						.uniqueness(Uniqueness.NODE_GLOBAL)
						.traverse(startNodeStemma)) {
	    			
	    			if(!nodes.contains(path.endNode()))
    					nodes.add(path.endNode());	
	    		}
	    		for(Node n : nodes) {
		    			outputNodes += "  " + n.getProperty("id").toString() + " [ class=" + n.getProperty("class").toString() + " ];";
		    			Iterable<Relationship> rels = n.getRelationships();
		    			
		    			for(Relationship rel: rels){
		    				if(!relationships.contains(rel))
		    					relationships.add(rel);
		    			}
	    				
    			}
	    		for(Relationship rel: relationships) {
			    			if(rel.getStartNode().hasProperty("id")) {
			    				if(stemmaType.equals("digraph")) 
			    					outputRelationships += " " + rel.getStartNode().getProperty("id") + " -> " + rel.getEndNode().getProperty("id") + "; ";
			    				else 
			    					outputRelationships += " " + rel.getStartNode().getProperty("id") + " -- " + rel.getEndNode().getProperty("id") + "; ";
			    			}
			    		}
	    	output = outputNodes + outputRelationships;
	    	output += "}";
    		
    		tx.success();
    	}
    	
    	writePNGFromDot(output,"upload/file");
    	writeSVGFromDot(output,"upload/file");
		return Response.ok(output).build();
	}
	
	public String getAllStemmataAsDot(String tradId)
	{
		String dot = "";
		
		try(Transaction tx = db.beginTx())
		{
			ExecutionEngine engine = new ExecutionEngine(db);
			// find all stemmata associated with this tradition
			ExecutionResult result = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})-[:STEMMA]->(s:STEMMA) return s");
			
			Iterator<Node> stemmata = result.columnAs("s");
			while(stemmata.hasNext())
			{
				String stemma = stemmata.next().getProperty("name").toString();
				Response resp = parseNeo4JStemma(tradId, stemma);
				
				dot = dot + resp.getEntity();
			}
		}
		
		return dot;
	}
	
	private void write(String str) throws IOException
	{
		out.write(str.getBytes());
	}
	
	
	public void writePNGFromDot(String dot, String outFile)
	{
		GraphViz gv = new GraphViz();
		gv.add(dot);
	    
	    String type = "png";
//	      String type = "plain";
	    
	    File out = new File(outFile + "." + type);   // Linux
//	      File out = new File("c:/eclipse.ws/graphviz-java-api/out." + type);    // Windows
	    gv.writeGraphToFile( gv.getGraph( gv.getDotSource(), type ), out );
	}
	
	public void writePNGFromDotFile(String inFile, String outFile)
	{	
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(inFile))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		writePNGFromDot(everything, outFile);
	}
	
	public void writeSVGFromDot(String dot, String outFile)
	{
		GraphViz gv = new GraphViz();
		gv.add(dot);
	    
	    String type = "svg";
//	      String type = "plain";
	    
	    File out = new File(outFile + "." + type);   // Linux
//	      File out = new File("c:/eclipse.ws/graphviz-java-api/out." + type);    // Windows
	    gv.writeGraphToFile( gv.getGraph( gv.getDotSource(), type ), out );
	}

	public void writeSVGFromDotFile(String inFile, String outFile)
	{	
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(inFile))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		writeSVGFromDot(everything, outFile);
	}
	
}