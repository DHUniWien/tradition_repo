package net.stemmaweb.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

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
 * This class provides a method for importing Graphml (XML) File into Neo4J
 * @author sevi
 * 
 */
public class GraphMLToNeo4JParser
{
	
	/**
	 * Reads xml file and imports it into Neo4J Database 
	 * @param filename - the graphMl file
	 * @param databasePath - the path to the Neo4J database folder
	 * @param userId - the user id who will own the tradition
	 * @param nameAbbrev - an abbreviation for the tradition (used as prefix in db)
	 * @return Http Response
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	public static Response parseGraphML(String filename, String databasePath, String userId) throws FileNotFoundException, XMLStreamException
	{
		
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
		GraphDatabaseService db= dbFactory.newEmbeddedDatabase("database");
		
    	ExecutionEngine engine = new ExecutionEngine(db);
    	
    	Node from = null;			// a round-trip store for the start node of a path
    	Node to = null;				// a round-trip store for the end node of a path
    	
    	LinkedList<String> leximes = new LinkedList<String>();
    								// a round-trip store for witness names of a single relationship
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		
        	
        	// retrieves the last inserted Tradition id
        	String prefix = db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node")
        												.iterator()
        												.next()
        												.getProperty("LAST_INSERTED_TRADITION_ID")
        												.toString();
        	int last_inserted_id = Integer.parseInt(prefix);
        	last_inserted_id++;
        	prefix = String.valueOf(last_inserted_id) + "_";
        	
        	
        	Node tradRootNode = null;	// this will be the entry point of the graph
        	
	    	Node currNode = null;	// holds the current node
	    	currNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
	    	Relationship rel = null;	// holds the current relationship
	    	
	    	int graphNumber = 0; 	// holds the current graph number 
	    	
			while (true) {
				// START READING THE GRAPHML FILE
			    int event = reader.next(); // gets the next <element>
			    
			    if(event == XMLStreamConstants.END_ELEMENT)
			    {
			    	if(reader.getLocalName().equals("graph") ||
			    		reader.getLocalName().equals("graphml") ||
			    		reader.getLocalName().equals("node") ||
			    		reader.getLocalName().equals("edge"))
			    	{
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
			        				else if(map.get(reader.getAttributeValue(0)).equals("witness"))
			        				{
			        					leximes.add(reader.getElementText());
			        					//rel.setProperty(map.get(reader.getAttributeValue(0)),reader.getElementText());
			        				}
			        				else
			        				{
			        					rel.setProperty(map.get(reader.getAttributeValue(0)),reader.getElementText());
			        					//System.out.println(map.get(reader.getElementText()));
			        				}
			        			}	
			        		}
			        		else if(type_nd==2) // node
			        		{
			        			if(currNode!=null)
			        			{
			        				if(map.get(reader.getAttributeValue(0)).equals("id"))
			        				{
			        					//System.out.println(currNode.getProperty("id"));
			        					currNode.setProperty("dn99", reader.getElementText());
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
			        			ExecutionResult result = engine.execute("match (n:TRADITION {name:'"+ map.get(attr) +"'}) return n");
			        			
			        			Iterator<Node> nodes = result.columnAs("n");
			        			if(nodes.hasNext())
			        			{
			        				throw new Exception("Error: A tradition with the same name already exists");
			        			}
			        			tradRootNode = currNode;
			        			
			        			//System.out.println(prefix);
			        			currNode.setProperty("id", prefix.substring(0, prefix.length()-1));
			        			
			        			currNode.setProperty(map.get(attr), 
	        							text);
			        		}
			        		else if(map.get(attr).equals("stemmata"))
			        		{
			        			// the stemma tree is available as 'text' here
			        		}
			        		else
			        		{
			        			currNode.setProperty(map.get(attr), 
	        							text);
			        		}
			        		
			        	}
			        }
			        else if(reader.getLocalName().equals("edge")) // this definitely needs refactoring!
			        {
			        	String fromNodeName = prefix + reader.getAttributeValue(0);
			        	String toNodeName = prefix + reader.getAttributeValue(1);
			        	if(from!=null && to!=null)
			        	{
			        		if(!(from.getProperty("id").equals(fromNodeName) 
				        			&& to.getProperty("id").equals(toNodeName)))
				        	{
				        		ResourceIterable<Node> startNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", fromNodeName);
					        	ResourceIterable<Node> endNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", toNodeName);
					        	Iterator<Node> st_it = startNodes.iterator();
					        	Iterator<Node> en_it = endNodes.iterator();
					        	if(st_it.hasNext() && en_it.hasNext())
					        	{
					        		Node fromTmp = st_it.next();
					        		Node toTmp = en_it.next();
					        		if(!(fromTmp.equals(from) && toTmp.equals(to)))
					        		{
					        			to = toTmp;
					        			from = fromTmp;
					        			if(rel!=null)
					        			{
					        				//System.out.println(leximes.toString());
					        				String[] leximArray = new String[leximes.size()];
					        				leximArray = leximes.toArray(leximArray);
					        				if(leximArray.length>0)
					        					rel.setProperty("lexemes", leximArray);
					        				leximes.clear();
					        			}
					        			if(graphNumber<=1)
					        			{
					        				rel = fromTmp.createRelationshipTo(toTmp, Relations.NORMAL);
					        			}
					        			else
					        			{
					        				rel = fromTmp.createRelationshipTo(toTmp, Relations.RELATIONSHIP);
					        			}
					        			rel.setProperty("id", prefix + reader.getAttributeValue(2));
					        		}
					        	}
				        	}
			        	}
			        	else
			        	{
				        		ResourceIterable<Node> startNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", fromNodeName);
					        	ResourceIterable<Node> endNodes = db.findNodesByLabelAndProperty(Nodes.WORD, "id", toNodeName);
					        	Iterator<Node> st_it = startNodes.iterator();
					        	Iterator<Node> en_it = endNodes.iterator();
					        	if(st_it.hasNext() && en_it.hasNext())
					        	{
					        		Node fromTmp = st_it.next();
					        		Node toTmp = en_it.next();
					        		if(!(fromTmp.equals(from) && toTmp.equals(to)))
					        		{
					        			to = toTmp;
					        			from = fromTmp;
					        			if(rel!=null)
					        			{
					        				//System.out.println(leximes.toString());
					        				String[] leximArray = new String[leximes.size()];
					        				leximArray = leximes.toArray(leximArray);
					        				rel.setProperty("leximes", leximArray);
					        				leximes.clear();
					        			}
					        			if(graphNumber<=1)
					        			{
					        				rel = fromTmp.createRelationshipTo(toTmp, Relations.NORMAL);
					        			}
					        			else
					        			{
					        				rel = fromTmp.createRelationshipTo(toTmp, Relations.RELATIONSHIP);
					        			}
					        			rel.setProperty("id", prefix + reader.getAttributeValue(2));
					        		}
					        	}
			        	}
			        	
			        		
			        	
			        	depth++;
			        	type_nd = 1;
			        }
			        else if(reader.getLocalName().equals("node"))
			        {
			        	if(graphNumber<=1)
			        	{	// only store nodes for graph 1, ignore all others (unused)
			        		currNode = db.createNode(Nodes.WORD);
			        	
			        		currNode.setProperty("id", prefix + reader.getAttributeValue(0));
			        	
			        		if(reader.getAttributeValue(0).equals("n1"))
			        		{
			        			tradRootNode.createRelationshipTo(currNode, Relations.NORMAL);
			        		}
			        	
			        		depth++;
			        		type_nd = 2;
			        	}
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
			        	depth++;
			        	graphNumber++;
			        	System.out.println("graph " + graphNumber);
			        }
			    }
			}
			if(rel!=null)	// add relationship props to last rel
			{
				String[] leximArray = new String[leximes.size()];
				leximArray = leximes.toArray(leximArray);
				rel.setProperty("leximes", leximArray);
				leximes.clear();
			}
	    	
	   	    ExecutionResult userNodeSearch = engine.execute("match (user:USER {id:'" + userId + "'}) return user");
	   	    Node userNode = (Node) userNodeSearch.columnAs("user").next();
	   	    userNode.createRelationshipTo(tradRootNode, Relations.NORMAL);
	   	    
	   	    
	   	    db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node")
	   	    								.iterator()
	   	    								.next()
	   	    								.setProperty("LAST_INSERTED_TRADITION_ID", prefix.substring(0, prefix.length()-1));
	   		
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