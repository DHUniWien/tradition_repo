package net.stemmaweb.stemmaserver.benachmarktests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;


/**
 * A helper class which helps to populate a complete random graph
 * 
 * @author jakob
 *
 */
public class RandomGraphGenerator {
	
	static String loremIpsum = "Lorem ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy eirmod tempor "
	+ "invidunt ut labore et dolore magna aliquyam erat sed diam voluptua At vero eos et "
	+ "accusam et justo duo dolores et ea rebum Stet clita kasd gubergren no sea takimata "
	+ "sanctus est Lorem ipsum dolor sit amet Lorem ipsum dolor sit amet consetetur sadipscing "
	+ "elitr sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat "
	+ "sed diam voluptua At vero eos et accusam et justo duo dolores et ea rebum Stet clita "
	+ "kasd gubergren no sea takimata sanctus est Lorem ipsum dolor sit amet";


	/**
	 * A method which fills the database with randomly generated valid traditions
	 * 
	 * @precondition the database should be initialized but empty
	 * @param db
	 * @param cardOfUsers
	 * @param cardOfTraditionsPerUser
	 * @param cardOfWitnesses
	 * @param maxRanks
	 * @postcondition the database if full
	 */
	public void role(GraphDatabaseService db, int cardOfUsers, int cardOfTraditionsPerUser, 
			int cardOfWitnesses, int maxRank){
    	ExecutionEngine engine = new ExecutionEngine(db);
    	
    	Random randomGenerator = new Random();
    	
    	String[] loremIpsumArray = loremIpsum.split(" ");
    	
    	try(Transaction tx = db.beginTx())
    	{
    		ExecutionResult result = engine.execute("match (n:ROOT) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		if(!nodes.hasNext())
    		{
    			Node node = db.createNode(Nodes.ROOT);
    			node.setProperty("name", "Root node");
    			node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
    		}
    		tx.success();
    	}
    	
    	for(int k=0;k<cardOfUsers;k++)
    	{
    		engine = new ExecutionEngine(db);
    		Node currentUser = null;
    		try (Transaction tx = db.beginTx()) {
    			ExecutionResult rootNodeSearch = engine.execute("match (n:ROOT) return n");
    			Node rootNode = (Node) rootNodeSearch.columnAs("n").next();

    			currentUser = db.createNode(Nodes.USER);
    			currentUser.setProperty("id", Integer.toString(k));
    			currentUser.setProperty("isAdmin", Integer.toString(randomGenerator.nextInt(2)));

    			rootNode.createRelationshipTo(currentUser, ERelations.NORMAL);

    			tx.success();
    		}
    		
    		/**
    		 * Create the Traditions
    		 */
        	for(int i=0;i<cardOfTraditionsPerUser;i++){
        		int ind=0;
        		System.out.print("Import User: " +(k+1)+"/"+cardOfUsers+" [");
        		for( ;ind<(int)((double)i/cardOfTraditionsPerUser*20.0);ind++){
        			System.out.print("#");
        		}
        		for(;ind<20;ind++)
        			System.out.print(" ");
        		System.out.println("]");
        		ArrayList<WitnessBranch> witnessUnconnectedBranchs = new ArrayList<RandomGraphGenerator.WitnessBranch>();
            	try (Transaction tx = db.beginTx()) {
            		String prefix = db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node")
            							.iterator()
            							.next()
            							.getProperty("LAST_INSERTED_TRADITION_ID")
            							.toString();
	            	Node traditionRootNode = db.createNode(Nodes.TRADITION);
	            	Node rootNode = db.findNodesByLabelAndProperty(Nodes.ROOT, "name", "Root node").iterator().next();
	            	rootNode.setProperty("LAST_INSERTED_TRADITION_ID", 
	            			Integer.toString(Integer.parseInt(prefix) + 1));

	            	traditionRootNode.setProperty("dg1", "TestTradition_"+prefix);
	            	traditionRootNode.setProperty("id", prefix);
	            	currentUser.createRelationshipTo(traditionRootNode, ERelations.NORMAL);
	            	
	            	/**
	            	 * Create start node
	            	 */
	            	Node startNode = db.createNode(Nodes.WORD);
	            	startNode.setProperty("dn15", "#START#");
	            	startNode.setProperty("dn8", "1");
	            	startNode.setProperty("dn14", "0");
	            	startNode.setProperty("dn2", "0");
	            	
	            	traditionRootNode.createRelationshipTo(startNode, ERelations.NORMAL);
	            	
	            	for(int l=0;l<cardOfWitnesses;l++){
	         
	            		WitnessBranch witnessBranch = new WitnessBranch();
	            		witnessBranch.setLastNode(startNode); 
	            		witnessBranch.setName("W"+l);
	            		
	            		witnessUnconnectedBranchs.add(witnessBranch);	
	            	}
	            	
	            	tx.success();
            	}

            	/**
            	 * Create Nodes for each rank
            	 */
            	ArrayList<WitnessBranch> witnessConnectedBranchs = new ArrayList<RandomGraphGenerator.WitnessBranch>();
        		for(int u=1;u<maxRank;u++){

    				ArrayList<Node> nodesOfCurrentRank = new ArrayList<Node>();
    				int numberOfNodesOnThisRank = randomGenerator.nextInt(cardOfWitnesses)+1;
        			try(Transaction tx = db.beginTx()){
            			for(int m=0;m<numberOfNodesOnThisRank;m++){
            				Node wordNode = db.createNode(Nodes.WORD);

            				wordNode.setProperty("dn15", loremIpsumArray[randomGenerator.nextInt(loremIpsumArray.length)]);
            				wordNode.setProperty("dn14", u);
            				wordNode.setProperty("dn2", 0);
            				wordNode.setProperty("dn11", "latin");
            				
            				nodesOfCurrentRank.add(wordNode);
            			}
            			tx.success();
        			}
        			try(Transaction tx = db.beginTx()){
        				
        				int moduloIndex=0;
        				
        				/**
        				 * Connect the words randomly
        				 */
            			for(int n=cardOfWitnesses; n>0;n--){
            				WitnessBranch witnessBranch = witnessUnconnectedBranchs.remove(randomGenerator.nextInt(n));
            				Node lastNode = witnessBranch.getLastNode();
            				Node nextNode = nodesOfCurrentRank.get(moduloIndex);
            				
            	    		Iterable<Relationship> relationships = lastNode.getRelationships(ERelations.NORMAL);

            	    		Relationship relationshipAtoB = null;
            	    		for (Relationship relationship : relationships) {
            	    			if((relationship.getStartNode().equals(lastNode)||relationship.getEndNode().equals(lastNode)) &&
            	    					relationship.getStartNode().equals(nextNode)||relationship.getEndNode().equals(nextNode)){
            	    				relationshipAtoB = relationship;
            	    			}
            				}
            	    		
            	    		if(relationshipAtoB==null) {
            	    			String[] lexemesArray = {witnessBranch.getName()};
            	    			Relationship rel = lastNode.createRelationshipTo(nextNode, ERelations.NORMAL);
            	    			rel.setProperty("lexemes", lexemesArray);
            	    		} else {
            					String[] arr = (String[]) relationshipAtoB.getProperty("lexemes");
            	    			
            	    			String[] lexemesArray = new String[arr.length + 1];
            					for (int index = 0;index < arr.length;index++) {
            						lexemesArray[index] = arr[index];
            					}
            					lexemesArray[arr.length] = witnessBranch.getName();
            	    			
            	    			relationshipAtoB.setProperty("lexemes", lexemesArray);
            	    		}
            				
            				witnessBranch.setLastNode(nextNode);
            				witnessConnectedBranchs.add(witnessBranch);
            				moduloIndex = (moduloIndex+1)%numberOfNodesOnThisRank;
            			}
            			witnessUnconnectedBranchs = witnessConnectedBranchs;
            			tx.success();
        			}
        		}
        		
        		/**
        		 * Create End node
        		 */
        		Node endNode;
        		try(Transaction tx = db.beginTx()){
	            	endNode = db.createNode(Nodes.WORD);
	            	endNode.setProperty("dn15", "#END#");
	            	endNode.setProperty("dn8", maxRank);
	            	endNode.setProperty("dn14", "0");
	            	endNode.setProperty("dn2", "0");
	            	tx.success();
        		}
            	
        		/**
        		 * Connect to end node
        		 */
        		for(WitnessBranch witnessBranch : witnessUnconnectedBranchs){
            		try(Transaction tx = db.beginTx()){
            			Node lastNode = witnessBranch.getLastNode();
            			
        	    		Iterable<Relationship> relationships = lastNode.getRelationships(ERelations.NORMAL);

        	    		Relationship relationshipAtoB = null;
        	    		for (Relationship relationship : relationships) {
        	    			if((relationship.getStartNode().equals(lastNode)||relationship.getEndNode().equals(lastNode)) &&
        	    					relationship.getStartNode().equals(endNode)||relationship.getEndNode().equals(endNode)){
        	    				relationshipAtoB = relationship;
        	    			}
        				}
        	    		
        	    		if(relationshipAtoB==null) {
        	    			String[] lexemesArray = {witnessBranch.getName()};
        	    			Relationship rel = lastNode.createRelationshipTo(endNode, ERelations.NORMAL);
        	    			rel.setProperty("lexemes", lexemesArray);
        	    		} else {
        					String[] arr = (String[]) relationshipAtoB.getProperty("lexemes");
        	    			
        	    			String[] lexemesArray = new String[arr.length + 1];
        					for (int index = 0;index < arr.length;index++) {
        						lexemesArray[index] = arr[index];
        					}
        					lexemesArray[arr.length] = witnessBranch.getName();
        	    			
        	    			relationshipAtoB.setProperty("lexemes", lexemesArray);
        	    		}
        	    		
    	            	tx.success();
            		}
        		}		
        	}	
    	}
	}
	
	private class WitnessBranch {
        private Node lastNode;
        private String name;
		public Node getLastNode() {
			return lastNode;
		}
		public void setLastNode(Node lastNode) {
			this.lastNode = lastNode;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
    }
}
