package net.stemmaweb.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

import javax.ws.rs.core.Response;
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
 * This class provides methods for importing Graphml (XML) File into Neo4J
 * @author sevi
 * 
 */
public class GraphMLToNeo4JParser
{

	public static Response parseGraphML(String filename, String databasePath, String userId, String nameAbbrev) throws FileNotFoundException, XMLStreamException
	{
		String prefix = userId + "_" + nameAbbrev + "_";
		XMLInputFactory factory;
		XMLStreamReader reader;
		File file = new File(filename);
		InputStream in = new FileInputStream(file);
		factory = XMLInputFactory.newInstance();
		reader = factory.createXMLStreamReader(in);
		
		int depth = 0; 
		// 0 root, 1 <graphml>, 2 <graph>, 3 <node>, 4 <data>
		int type_nd = 0;
		// 0 = no, 1 = edge, 2 = node
		HashMap<String, String> map = new HashMap<String, String>();
		// to store all keys of the introduction part
		
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
    	GraphDatabaseService db= dbFactory.newEmbeddedDatabase(databasePath);
    	
    	ExecutionEngine engine = new ExecutionEngine(db);
    	Node tradRootNode = null;
    	String origPrefix = prefix;
    	
    	try (Transaction tx = db.beginTx()) 
    	{
	    	Node currNode = null;
	    	currNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
	    	Relationship rel = null;
			while (true) {
			    int event = reader.next();
			    if(event == XMLStreamConstants.END_ELEMENT)
			    {
			    	if(reader.getLocalName().equals("graph") ||
			    		reader.getLocalName().equals("graphml") ||
			    		reader.getLocalName().equals("node") ||
			    		reader.getLocalName().equals("edge"))
			    	{
			    		if(reader.getLocalName().equals("graph"))
			    			prefix = origPrefix;
			    		depth--;
			    		type_nd = 0;
			    	}
			    }
			    if (event == XMLStreamConstants.END_DOCUMENT) {
			       reader.close();
			       break;
			    }
			    if (event == XMLStreamConstants.START_ELEMENT) {
			    	
			        if(reader.getLocalName().equals("data"))
			        {
			        	if(depth==3)
			        	{
			        		if(type_nd==1) // edge
			        		{
			        			if(rel!=null)
			        			{
			        				if(map.get(reader.getAttributeValue(0)).equals("id"))
			        				{
			        					rel.setProperty(map.get(reader.getAttributeValue(0)),
				        						prefix + reader.getElementText());
			        				}
			        				else
			        				{
			        					rel.setProperty(map.get(reader.getAttributeValue(0)),
				        						reader.getElementText());
			        				}
			        			}	
			        		}
			        		else if(type_nd==2) // node
			        		{
			        			if(currNode!=null)
			        			{
			        				if(map.get(reader.getAttributeValue(0)).equals("id"))
			        				{
			        					currNode.setProperty(map.get(reader.getAttributeValue(0)), 
			        							prefix + reader.getElementText());
			        				}
			        				else
			        				{
			        					currNode.setProperty(map.get(reader.getAttributeValue(0)), 
			        							reader.getElementText());
			        				}
			        			}
			        				
			        		}
			        	}
			        	else if(depth==2)
			        	{
			        		String attr = reader.getAttributeValue(0);
			        		String text = reader.getElementText();
			        		// needs implementation of meta data here
			        		if(map.get(attr).equals("name"))
			        		{
			        			
			        			ExecutionResult result = engine.execute("match (n:TRADITION {name:'"+ text +"'}) return n");
			        			Iterator<Node> nodes = result.columnAs("n");
			        			if(nodes.hasNext())
			        			{
			        				throw new Exception("Error: A tradition with the same name already exists");
			        			}
			        			
			        			
			        			tradRootNode = currNode;
			        			prefix += attr.charAt(0) + attr.charAt(attr.length()-1) + "_";
			        			System.out.println(prefix);
			        			currNode.setProperty("id", prefix);
			        			
			        			
			        		}
			        		currNode.setProperty(map.get(attr), 
        							text);
			        	}
			        }
			        else if(reader.getLocalName().equals("edge"))
			        {
			        	
			        	ResourceIterable<Node> startNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", prefix + reader.getAttributeValue(0));
			        	ResourceIterable<Node> endNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", prefix + reader.getAttributeValue(1));
			        	Iterator<Node> st_it = startNodes.iterator();
			        	Iterator<Node> en_it = endNodes.iterator();
			        	if(st_it.hasNext() && en_it.hasNext())
			        	{
			        		rel = (st_it.next()).createRelationshipTo((en_it.next()), Relations.NORMAL);
			        		rel.setProperty("id", prefix + reader.getAttributeValue(2));
			        	}
			        	depth++;
			        	type_nd = 1;
			        }
			        else if(reader.getLocalName().equals("node"))
			        {
			        	currNode = db.createNode(Nodes.WORD);
			        	currNode.setProperty("id", prefix + reader.getAttributeValue(0));
			        	if(reader.getAttributeValue(0).equals("n1"))
			        	{
			        		currNode.createRelationshipTo(tradRootNode, Relations.NORMAL);
			        	}
			        	depth++;
			        	type_nd = 2;
			        }
			        else if(reader.getLocalName().equals("key"))
			        {
			        	String key = "";
			        	String value = "";
			  
			        	for(int i=0;i<reader.getAttributeCount();i++)
			        	{
			        		if(reader.getAttributeName(i).equals(new QName("attr.name")))
			        		{
			        			value = reader.getAttributeValue(i);
			        		}
			        		else if(reader.getAttributeName(i).equals(new QName("id")))
			        		{
			        			key = reader.getAttributeValue(i);
			        		}
			        	}
			        	map.put(key, value);
			        }
			        else if(reader.getLocalName().equals("graphml"))
			        {
			        	depth++;
			        }
			        else if(reader.getLocalName().equals("graph"))
			        {
			        	String attr = reader.getAttributeValue(1);
			        	prefix +=  attr.charAt(0) + attr.charAt(attr.length()-1) + "_";
			        	depth++;
			        }
			    }
			}
	    	
	   	    ExecutionResult userNodeSearch = engine.execute("match (user:USER {id:'" + userId + "'}) return user");
	   	    Node userNode = (Node) userNodeSearch.columnAs("user").next();
	   	    tradRootNode.createRelationshipTo(userNode, Relations.NORMAL);
	   		
			tx.success();
		}
	    catch(Exception e)
	    {
	    	e.printStackTrace();
	    	db.shutdown();
	    	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error: Tradition could not be imported!").build();
	    }
    	finally
    	{
    		db.shutdown();
    	}
    	return Response.status(Response.Status.OK).entity("Tradition imported successfully").build();
	}
	
}