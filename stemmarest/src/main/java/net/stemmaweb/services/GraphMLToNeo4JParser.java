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

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * This class provides a method for importing GraphMl (XML) File into Neo4J
 * 
 * @author PSE FS 2015 Team2
 */
public class GraphMLToNeo4JParser implements IResource
{
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();
	
	/**
	 * Reads xml file and imports it into Neo4J Database
	 * 
	 * @param filename
	 *            - the graphMl file
	 * @param userId
	 *            - the user id who will own the tradition
	 * @param tradName
	 *            tradition name that should be used
	 * @return Http Response with the id of the imported tradition
	 * @throws FileNotFoundException
	 * @throws XMLStreamException
	 */
	public Response parseGraphML(String filename, String userId, String tradName) throws FileNotFoundException
	{
		XMLInputFactory factory;
		XMLStreamReader reader;
		File file = new File(filename);
		InputStream in = new FileInputStream(file);
		factory = XMLInputFactory.newInstance();
		
		HashMap<String, Long> idToNeo4jId = new HashMap<String, Long>();
		
		int depth = 0; 
		// 0 root, 1 <graphml>, 2 <graph>, 3 <node>, 4 <data>
		int type_nd = 0;
		// 0 = no, 1 = edge, 2 = node
		HashMap<String, String> map = new HashMap<String, String>();
		// to store all keys of the introduction part
		
    	ExecutionEngine engine = new ExecutionEngine(db);
    	
    	Node from = null;			// a round-trip store for the start node of a path
    	Node to = null;				// a round-trip store for the end node of a path
    	
    	LinkedList<String> lexemes = new LinkedList<String>();
    								// a round-trip store for witness names of a single relationship
    	int last_inserted_id = 0;
    	
    	String stemmata = ""; // holds Stemmatas for this GraphMl
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		reader = factory.createXMLStreamReader(in);
        	// retrieves the last inserted Tradition id
        	String prefix = db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node")
        												.iterator()
        												.next()
        												.getProperty("LAST_INSERTED_TRADITION_ID")
        												.toString();
        	last_inserted_id = Integer.parseInt(prefix);
        	last_inserted_id++;
        	prefix = String.valueOf(last_inserted_id) + "_";
        	
        	
        	Node tradRootNode = null;	// this will be the entry point of the graph
        	
	    	Node currNode = null;	// holds the current node
	    	currNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
	    	Relationship rel = null;	// holds the current relationship
	    	
	    	int graphNumber = 0; 	// holds the current graph number 
	    	
	    	int firstNode = 0; // flag to get START NODE (always == n1) == 2
	    	
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
			        				String attr = reader.getAttributeValue(0);
			        				String val = reader.getElementText();
			        				
			        				if(map.get(attr)!=null)
			        				{
			        					if(map.get(attr).equals("id"))
			        					{
			        						rel.setProperty("id", prefix + val);
			        						rel.setProperty(attr,val);
			        					}
			        					else if(map.get(attr).equals("witness"))
			        					{
			        						lexemes.add(val);
			        					//rel.setProperty(map.get(reader.getAttributeValue(0)),reader.getElementText());
			        					}
			        					else
			        					{
			        						rel.setProperty(map.get(attr),val);
			        					//System.out.println(map.get(reader.getElementText()));
			        					}
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
			        					currNode.setProperty(map.get(reader.getAttributeValue(0)),
			        											reader.getElementText());
			        				}
			        				else if(map.get(reader.getAttributeValue(0)).equals("rank"))
			        				{
			        					currNode.setProperty(map.get(reader.getAttributeValue(0)), 
			        							Long.parseLong(reader.getElementText()));
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
			        			String tradNameToUse = text;
			        			if(!tradName.equals(""))
			        				tradNameToUse = tradName;
			        				
			        			tradRootNode = currNode;
			        			
			        			//System.out.println(prefix);
			        			currNode.setProperty("id", prefix.substring(0, prefix.length()-1));
			        			
			        			currNode.setProperty("name", tradNameToUse);
			        		}
			        		else if(map.get(attr).equals("stemmata"))
			        		{
			        			stemmata = text;
			        		}
			        		else
			        		{
			        			currNode.setProperty(map.get(attr),text);
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
				        		Node fromTmp = db.getNodeById(idToNeo4jId.get(fromNodeName));
				        		Node toTmp = db.getNodeById(idToNeo4jId.get(toNodeName));
						        if(!(fromTmp.equals(from) && toTmp.equals(to)))
						        {
						        	to = toTmp;
						        	from = fromTmp;
						        	if(rel!=null)
						        	{
						        		//System.out.println(lexemes.toString());
						        		String[] lexemeArray = new String[lexemes.size()];
						        		lexemeArray = lexemes.toArray(lexemeArray);
						        		if(lexemeArray.length>0)
						        			rel.setProperty("lexemes", lexemeArray);
						        		lexemes.clear();
						        	}
						        	if(graphNumber<=1)
						        	{
						        		rel = fromTmp.createRelationshipTo(toTmp, ERelations.NORMAL);
						        	}
						        	else
						        	{
						        		rel = fromTmp.createRelationshipTo(toTmp, ERelations.RELATIONSHIP);
						        	}
						        	rel.setProperty("id", prefix + reader.getAttributeValue(2));
						        }
				        	}
			        	}
			        	else
			        	{
			        		Node fromTmp = null;
			        		if(idToNeo4jId.get(fromNodeName)!=null)
			        			 fromTmp = db.getNodeById(idToNeo4jId.get(fromNodeName));
			        		Node toTmp = db.getNodeById(idToNeo4jId.get(toNodeName));

					        if(fromTmp!=null && !(fromTmp.equals(from) && toTmp.equals(to)))
					        {
					        	to = toTmp;
					        	from = fromTmp;
					        	if(rel!=null)
					        	{
					        		//System.out.println(lexemes.toString());
					        		String[] lexemeArray = new String[lexemes.size()];
					        		lexemeArray = lexemes.toArray(lexemeArray);
					        		rel.setProperty("lexemes", lexemeArray);
					        		lexemes.clear();
					        	}
					        	if(graphNumber<=1)
					        	{
					        		rel = fromTmp.createRelationshipTo(toTmp, ERelations.NORMAL);
					        	}
					        	else
					        	{
					        		rel = fromTmp.createRelationshipTo(toTmp, ERelations.RELATIONSHIP);
					        	}
					        	rel.setProperty("id", prefix + reader.getAttributeValue(2));
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
			        		
			        		idToNeo4jId.put(prefix + reader.getAttributeValue(0), currNode.getId());
			        		
			        		if(firstNode==1)
			        		{
			        			tradRootNode.createRelationshipTo(currNode, ERelations.NORMAL);
			        			firstNode++;
			        		}
			        		if(firstNode<1)
			        			firstNode++;
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
			        	depth++;
			        	graphNumber++;
			        }
			    }
			}
			if(rel!=null)	// add relationship props to last relationship
			{
				String[] lexemeArray = new String[lexemes.size()];
				lexemeArray = lexemes.toArray(lexemeArray);
				rel.setProperty("lexemes", lexemeArray);
				lexemes.clear();
			}
			
			ExecutionResult result = engine.execute("match (n:TRADITION {id:'"+ last_inserted_id +"'})-[:NORMAL]->(s:WORD) return s");
			Iterator<Node> nodes = result.columnAs("s");
			Node startNode = nodes.next();
			for (Node node : db.traversalDescription().breadthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNode).nodes()) {
				if(node.hasProperty("id"))
				{
					node.removeProperty("id");
				}
				if(node.hasProperty("id"))
				{
					node.removeProperty("id");
				}
				for(Relationship relation : node.getRelationships())
				{
					if(relation.hasProperty("id"))
					{
						relation.removeProperty("id");
					}
				}
			}
	    	
	   	    ExecutionResult userNodeSearch = engine.execute("match (user:USER {id:'" + userId + "'}) return user");
	   	    Node userNode = (Node) userNodeSearch.columnAs("user").next();
	   	    userNode.createRelationshipTo(tradRootNode, ERelations.NORMAL);

	   	    db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node")
	   	    								.iterator()
	   	    								.next()
	   	    								.setProperty("LAST_INSERTED_TRADITION_ID", prefix.substring(0, prefix.length()-1));

			tx.success();
		}
	    catch(Exception e)
	    {
	    	e.printStackTrace();
	    	
	    	return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error: Tradition could not be imported!").build();
	    }
    	
    	String[] graphs = stemmata.split("\\}");
    	
   	    for(String graph : graphs)
   	    {
   	    	DotToNeo4JParser parser = new DotToNeo4JParser(db);
   	    	parser.parseDot(graph, last_inserted_id + "");
		}
    	
    	return Response.status(Response.Status.OK).entity("{\"tradId\":" + last_inserted_id + "}").build();
	}
	
}