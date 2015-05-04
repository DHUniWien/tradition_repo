package net.stemmaweb.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/**
 * 
 * This class provides methods for exporting GraphMl (XML) File from Neo4J
 * 
 * @author PSE FS 2015 Team2
 * 
 */
public class Neo4JToGraphMLParser implements IResource
{
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();

	public Response parseNeo4J(String tradId)
	{
		
		String filename = "upload/" + "output.xml";
    	
    	ExecutionEngine engine = new ExecutionEngine(db);
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		Node traditionNode = null;
    		ExecutionResult result = engine.execute("match (n:TRADITION {id: '"+ tradId +"'}) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext())
    			return Response.status(Status.NOT_FOUND).build();
    		
    		traditionNode = nodes.next();
    		
    		
    		File file = new File(filename);
    		//file.delete();
    		file.createNewFile();
    		OutputStream out = new FileOutputStream(file);
    		
    		XMLOutputFactory output = XMLOutputFactory.newInstance();
    		XMLStreamWriter writer = new IndentingXMLStreamWriter(output.createXMLStreamWriter(out));
    		
    		writer.writeStartDocument();
    		
    		writer.writeStartElement("graphml");
    		writer.writeAttribute("xmlns","http://graphml.graphdrawing.org/xmlns");
    		writer.writeAttribute("xmlns:xsi","http://www.w3.org/2001/XMLSchema-instance");
    		writer.writeAttribute("xsi:schemaLocation","http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd");
    		
    		// ####### KEYS START #######################################
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "language");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg0");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "name");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg1");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "public");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg2");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "stemmata");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg3");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "stemweb_jobid");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg4");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "user");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg5");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "version");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "graph");
    		writer.writeAttribute("id", "dg6");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "grammar_invalid");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "grammar_invalid");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "id");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_common");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "is_common");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_end");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "is_end");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_lacuna");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn4");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_lemma");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn5");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_nonsense");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn6");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_ph");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn7");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_start");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn8");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "join_next");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn9");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "join_prior");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id0");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "language");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id1");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "lexemes");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id2");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "normal_form");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id3");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "rank");
    		writer.writeAttribute("attr.type", "int");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id4");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "text");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "id5");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "a_derivable_from_b");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de0");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "alters_meaning");
    		writer.writeAttribute("attr.type", "int");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de1");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "annotation");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de2");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "b_derivable_from_a");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de3");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "displayform");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de4");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "extra");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de5");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_significant");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de6");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "non_independent");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de7");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "reading_a");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de8");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "reading_b");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de9");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "scope");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de10");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "type");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de11");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "witness");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "edge");
    		writer.writeAttribute("id", "de12");
    		
    		// ####### KEYS END #######################################
    		
    		result = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})-[:NORMAL]-(n:WORD) return n");
    		nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext())
    			return Response.status(Status.NOT_FOUND).entity("No graph found.").build();
    		
    		// graph 1
    		
    		Iterable<String> props = null;
    		
    		Node graphNode = nodes.next();
    			
    		writer.writeStartElement("graph");
    		writer.writeAttribute("edgedefault", "directed");
    		//writer.writeAttribute("id", traditionNode.getProperty("dg1").toString());
    		writer.writeAttribute("parse.edgeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.edges", );
    		writer.writeAttribute("parse.nodeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.nodes", );
    		writer.writeAttribute("parse.order", "nodesfirst");
    		
    		props = traditionNode.getPropertyKeys();
    		for(String prop : props)
    		{
    			String val = prop;
    			if(val!=null && !val.equals("id"))
    			{
    				writer.writeStartElement("data");
    				writer.writeAttribute("key",val);
    				writer.writeCharacters(traditionNode.getProperty(prop).toString());
    				writer.writeEndElement();
    			}
    		}
    		
    		result = engine.execute("match (n:TRADITION {id:'"+ tradId +"'})-[:NORMAL]->(s:WORD) return s");
			nodes = result.columnAs("s");
			Node startNodeTrad = nodes.next();
			
			long nodeId = 0;
			long edgeId = 0;
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNodeTrad).nodes()) {
				props = node.getPropertyKeys();
    			writer.writeStartElement("node");
    			writer.writeAttribute("id", String.valueOf(node.getId()));
    			writer.writeStartElement("data");
    			writer.writeAttribute("key","id");
    			writer.writeCharacters("n" + nodeId++);
    			writer.writeEndElement();
        		for(String prop : props)
        		{
        			String val = prop;
        			
        			if(val!=null)
        			{
	        			writer.writeStartElement("data");
	        			writer.writeAttribute("key",val);
	        			writer.writeCharacters(node.getProperty(prop).toString());
	        			writer.writeEndElement();
        			}
        		}
        		writer.writeEndElement(); // end node
			}
        		
    		String startNode = "";
    		String endNode = "";
    		for ( Relationship rel : db.traversalDescription()
    		        .relationships( ERelations.NORMAL,Direction.OUTGOING)
    		        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
    		        .traverse( graphNode ).relationships() )
    		{

        		if(rel!=null)
        		{
        			props = rel.getPropertyKeys();
        			for(String prop : props)
            		{
            			String val = prop;
            			if(val!=null)
            			{
    	        			if(prop.equals("lexemes"))
    	        			{
    	        				String[] lexemes = (String[]) rel.getProperty(prop);
    	        				for(int i = 0; i < lexemes.length; i++)
    	        				{
	    	        				writer.writeStartElement("edge");
	    	        				
	    	        				writer.writeAttribute("source", rel.getStartNode().getId() + "");
	    	        				writer.writeAttribute("target", rel.getEndNode().getId() + "");
	    	        				writer.writeAttribute("id","e" + edgeId++);
	    	        				
	    	        				writer.writeStartElement("data");
	    	        				writer.writeAttribute("key","de12");
	    	        				writer.writeCharacters(lexemes[i]);
	    	        				
	    	        				writer.writeEndElement();
	    	        				writer.writeEndElement(); // end edge
    	        				}
    	        			}
            			}
            		}
        		}
    		}
    		writer.writeEndElement(); // graph
    	
    		// graph 2
    		// get the same nodes again, but this time we will later also search for other relationships
    		result = engine.execute("match (t:TRADITION {id:'"+ tradId +"'})-[:NORMAL]-(n:WORD) return n");
    		nodes = result.columnAs("n");
    		
			writer.writeStartElement("graph");
    		writer.writeAttribute("edgedefault", "directed");
    		writer.writeAttribute("id", "relationships");
    		writer.writeAttribute("parse.edgeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.edges", );
    		writer.writeAttribute("parse.nodeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.nodes", );
    		writer.writeAttribute("parse.order", "nodesfirst");
    		
    		nodeId = 0;
    		edgeId = 0;
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNodeTrad).nodes()) {
    			
    			props = node.getPropertyKeys();
    			writer.writeStartElement("node");
    			writer.writeAttribute("id", node.getId() + "");
	        	writer.writeStartElement("data");
	        	writer.writeAttribute("key","id");
	        	writer.writeCharacters("n" + nodeId++);
	        	writer.writeEndElement();
        		writer.writeEndElement(); // end node
    		}
    		
			result = engine.execute("match (n:TRADITION {id:'"+ tradId +"'})-[:NORMAL]->(s:WORD) return s");
			nodes = result.columnAs("s");
			startNodeTrad = nodes.next();
			
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNodeTrad).nodes()) {
				
				Iterable<Relationship> rels = node.getRelationships(ERelations.RELATIONSHIP,Direction.OUTGOING);
				for(Relationship rel : rels)
				{
	    			props = rel.getPropertyKeys();
					writer.writeStartElement("edge");
					startNode = rel.getStartNode().getId() + "";
    				endNode = rel.getEndNode().getId() + "";
    				writer.writeAttribute("source", startNode);
    				writer.writeAttribute("target", endNode);
    				writer.writeAttribute("id", "e" + edgeId++);
	    			for(String prop : props)
	        		{
	        			String val = prop;			
	        			if(val!=null)
	        			{
		        			writer.writeStartElement("data");
		        			writer.writeAttribute("key",val);
		        			writer.writeCharacters(rel.getProperty(prop).toString());
		        			writer.writeEndElement();
	        			}
	        		}
	    			writer.writeEndElement(); // end edge
				}
    		}
    		writer.writeEndElement(); // end graph
    		writer.writeEndElement(); // end graphml
    		writer.flush();
    		out.close();	
		}
	    catch(Exception e)
	    {
	    	e.printStackTrace();
	    	
	    	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error: Tradition could not be exported!").build();
	    }
		finally
		{
			
		}
    	File outputFile = new File(filename);
		if(outputFile.exists())
			return Response.ok(outputFile, MediaType.APPLICATION_XML).build();
		else
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something went wrong").build();
	}
}