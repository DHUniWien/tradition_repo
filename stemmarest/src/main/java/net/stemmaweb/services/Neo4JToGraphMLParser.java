package net.stemmaweb.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Relations;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.sun.org.apache.commons.collections.IteratorUtils;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/**
 * This class provides methods for exporting GraphMl (XML) File from Neo4J
 * @author sevi
 * 
 */
public class Neo4JToGraphMLParser implements IResource
{
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	// This creates a static hashmap for the graphml definitions (Edge only)
	public static HashMap<String,String> createEdgeMap()
	{
		HashMap<String,String> map = new HashMap<String,String>();
		
		map.put("a_derivable_from_b", "de0");
		map.put("alters_meaning", "de1");
		map.put("annotation", "de2");
		map.put("b_derivable_from_a", "de3");
		map.put("displayform", "de4");
		map.put("extra", "de5");
		map.put("is_significant", "de6");
		map.put("non_independent", "de7");
		map.put("reading_a", "de8");
		map.put("reading_b", "de9");
		map.put("scope", "de10");
		map.put("type", "de11");
		map.put("witness", "de12");
		map.put("id", "id");
		map.put("lexemes", "de12");
		
		return map;
	}
	
	// This creates a static hashmap for the graphml definitions (Node only)
		public static HashMap<String,String> createNodeMap()
		{
			HashMap<String,String> map = new HashMap<String,String>();
			
			map.put("grammar_invalid", "dn0");
			map.put("id", "dn1");
			map.put("is_common", "dn2");
			map.put("is_end", "dn3");
			map.put("is_lacuna", "dn4");
			map.put("is_lemma", "dn5");
			map.put("is_nonsense", "dn6");
			map.put("is_ph", "dn7");
			map.put("is_start", "dn8");
			map.put("join_next", "dn9");
			map.put("join_prior", "dn10");
			map.put("language", "dn11");
			map.put("lexemes", "dn12");
			map.put("normal_form", "dn13");
			map.put("rank", "dn14");
			map.put("text", "dn15");
			map.put("dn99", "dn1");

			return map;
		}
	
	// This creates a static hashmap for the graphml definitions (graph only)
		public static HashMap<String,String> createGraphMap()
		{
			HashMap<String,String> map = new HashMap<String,String>();
			
			map.put("language", "dg0");
			map.put("name", "dg1");
			map.put("public", "dg2");
			map.put("stemmata", "dg3");
			map.put("stemweb_jobid", "dg4");
			map.put("user", "dg5");
			map.put("version", "dg6");
			
			return map;
		}

	public Response parseNeo4J(String tradId)
	{
		
		String filename = "upload/" + "output.xml";
		
		
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
    	
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
    		writer.writeAttribute("id", "dn0");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "id");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn1");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_common");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn2");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "is_end");
    		writer.writeAttribute("attr.type", "boolean");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn3");
    		
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
    		writer.writeAttribute("id", "dn10");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "language");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn11");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "lexemes");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn12");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "normal_form");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn13");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "rank");
    		writer.writeAttribute("attr.type", "int");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn14");
    		
    		writer.writeEmptyElement("key");
    		writer.writeAttribute("attr.name", "text");
    		writer.writeAttribute("attr.type", "string");
    		writer.writeAttribute("for", "node");
    		writer.writeAttribute("id", "dn15");
    		
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
    		writer.writeAttribute("id", traditionNode.getProperty("name").toString());
    		writer.writeAttribute("parse.edgeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.edges", );
    		writer.writeAttribute("parse.nodeids", "canonical");
    		// THIS NEEDS TO BE IMPLEMENTED LATER 
    		// writer.writeAttribute("parse.nodes", );
    		writer.writeAttribute("parse.order", "nodesfirst");
  
    		HashMap<String,String> mapGraph = createGraphMap();
    		HashMap<String,String> mapNode = createNodeMap();
    		HashMap<String,String> mapEdge = createEdgeMap();
    		
    		props = traditionNode.getPropertyKeys();
    		for(String prop : props)
    		{
    			String val = mapGraph.get(prop);
    			if(val!=null)
    			{
    				writer.writeStartElement("data");
    				writer.writeAttribute("key",val);
    				writer.writeCharacters(traditionNode.getProperty(prop).toString());
    				writer.writeEndElement();
    			}
    		}
    		
    		result = engine.execute("match (n:WORD) where n.id=~'"+ tradId + ".*' return n");
    		Iterator<Node> nodesIt = result.columnAs("n");
    		
    		while(nodesIt.hasNext())
    		{
    			Node node = nodesIt.next();
    			props = node.getPropertyKeys();
    			writer.writeStartElement("node");
        		for(String prop : props)
        		{
        			String val = mapNode.get(prop);
        			
        			if(val!=null)
        			{
	        			if(prop.equals("id"))
	        			{
	        				String[] id = node.getProperty("id").toString().split("_");
	        				writer.writeAttribute("id",id[id.length-1]);
	        			}
	        			else
	        			{
	        				writer.writeStartElement("data");
	        				writer.writeAttribute("key",val);
	        				writer.writeCharacters(node.getProperty(prop).toString());
	        				writer.writeEndElement();
	        			}
        			}
        		}
        		writer.writeEndElement(); // end node
    		}
        		
    		String startNode = "";
    		String endNode = "";
    		String[] startId = null;
    		String[] endId = null;
    		String id = "";
    		for ( Path position : db.traversalDescription()
    		        .relationships( Relations.NORMAL,Direction.OUTGOING)
    		        .traverse( graphNode ) )
    		{
        		Relationship rel = position.lastRelationship();
        		if(rel!=null)
        		{
        			props = rel.getPropertyKeys();
        			for(String prop : props)
            		{
            			String val = mapEdge.get(prop);
            			if(val!=null)
            			{
    	        			if(prop.equals("id"))
    	        			{
    	        				String[] id_string = rel.getProperty("id").toString().split("_");
    	        				id = id_string[id_string.length-1];
    	        				
    	        				Iterable<Node> nodeIterable = position.nodes();
    	        				
    	        				@SuppressWarnings("unchecked")
								List<Node> nds = IteratorUtils.toList(nodeIterable.iterator());
    	        				startNode = nds.get(nds.size()-2).getProperty("id").toString();
    	        				endNode = nds.get(nds.size()-1).getProperty("id").toString();
    	        				startId = startNode.split("_");
    	        				endId = endNode.split("_");
    	        				
    	        			}
    	        			else if(prop.equals("lexemes"))
    	        			{
    	        				int id_int = Integer.parseInt(id.substring(1, id.length()));
    	        				String[] lexemes = (String[]) rel.getProperty(prop);
    	        				for(int i = 0; i < lexemes.length; i++)
    	        				{
	    	        				writer.writeStartElement("edge");
	    	        				
	    	        				writer.writeAttribute("source", startId[startId.length-1]);
	    	        				writer.writeAttribute("target", endId[endId.length-1]);
	    	        				writer.writeAttribute("id",'e'+String.valueOf(id_int++));
	    	        				
	    	        				writer.writeStartElement("data");
	    	        				writer.writeAttribute("key",val);
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
    		
    		result = engine.execute("match (n:WORD) where n.id=~'"+ tradId + ".*' return n");
    		nodesIt = result.columnAs("n");
    		
    		while(nodesIt.hasNext())
    		{
    			Node nextNode = nodesIt.next();
    			
    			props = nextNode.getPropertyKeys();
    			writer.writeStartElement("node");
        		for(String prop : props)
        		{
        			String val = mapNode.get(prop);
        			
        			if(val!=null)
        			{
	        			if(prop.equals("id"))
	        			{
	        				String[] id_arr = nextNode.getProperty("id").toString().split("_");
	        				writer.writeAttribute("id",id_arr[id_arr.length-1]);
	        			}
	        			else if(prop.equals("dn99"))
	        			{
	        				writer.writeStartElement("data");
	        				writer.writeAttribute("key","dn1");
	        				writer.writeCharacters(nextNode.getProperty(prop).toString());
	        				writer.writeEndElement();
	        			}
        			}
        		}
        		writer.writeEndElement(); // end node
    		}
    		
    		result = engine.execute("match (n:WORD)-[r:RELATIONSHIP]->(e) where n.id=~'"+ tradId + ".*' return r");
    		Iterator<Relationship> relIt = result.columnAs("r");

    		while(relIt.hasNext())
    		{
    			Relationship nextRel = relIt.next();
    			props = nextRel.getPropertyKeys();
				writer.writeStartElement("edge");
    			for(String prop : props)
        		{
        			String val = mapEdge.get(prop);			
        			if(val!=null)
        			{
	        			if(prop.equals("id"))
	        			{    				
	        				startNode = nextRel.getStartNode().getProperty("id").toString();
	        				endNode = nextRel.getEndNode().getProperty("id").toString();
	        				startId = startNode.split("_");
	        				endId = endNode.split("_");
	        				String[] id_string = nextRel.getProperty("id").toString().split("_");
	        				id = id_string[id_string.length-1];
	        				writer.writeAttribute("source", startId[startId.length-1]);
	        				writer.writeAttribute("target", endId[endId.length-1]);
	        				writer.writeAttribute("id", id);
	        			}    	
            			else
            			{
	        				writer.writeStartElement("data");
	        				writer.writeAttribute("key",val);
	        				writer.writeCharacters(nextRel.getProperty(prop).toString());
	        				writer.writeEndElement();
            			}
        			}
        		}
    			writer.writeEndElement(); // end edge
    		}
    		writer.writeEndElement(); // end graph
    		writer.writeEndElement(); // end graphml
    		writer.flush();
    		out.close();	
		}
	    catch(Exception e)
	    {
	    	e.printStackTrace();
	    	db.shutdown();
	    	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error: Tradition could not be exported!").build();
	    }
		finally
		{
			db.shutdown();
		}
    	File outputFile = new File(filename);
		if(outputFile.exists())
			return Response.ok(outputFile, MediaType.APPLICATION_XML).build();
		else
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something went wrong").build();
	}
}