package net.stemmaweb.services;

import java.util.Iterator;

import net.stemmaweb.rest.Nodes;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import Exceptions.DataBaseException;

/**
 * 
 * Helper methods for the database
 * @author jakob, ido
 *
 */
public class DatabaseService {

	private GraphDatabaseService db;

	public DatabaseService(GraphDatabaseService db){
		this.db = db;
	}
	
	/**
	 * gets the "start" node of a tradition
	 * @param traditionName
	 * @param userId
	 * 
	 * @return the start node of a witness
	 */
	public Node getStartNode(String tradId) throws DataBaseException {

		ExecutionEngine engine = new ExecutionEngine(db);
		DbPathProblemService problemFinder = new DbPathProblemService();
		Node startNode = null;

		/**
		 * this quarry gets the "Start" node of the witness
		 */
		String witnessQuarry = "match (tradition:TRADITION {id:'" + tradId
				+ "'})--(w:WORD  {dn1:'__START__'}) return w";

		try (Transaction tx = db.beginTx()) {

			ExecutionResult result = engine.execute(witnessQuarry);
			Iterator<Node> nodes = result.columnAs("w");

			if (!nodes.hasNext()) {
				throw new DataBaseException(
						problemFinder.findPathProblem(tradId,db));
			} else
				startNode = nodes.next();

			tx.success();
		}
		return startNode;
	}
	
	/**
	 * creates the root node
	 */
	public void createRootNode() {
		ExecutionEngine engine = new ExecutionEngine(db);
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
    	} finally {
        	db.shutdown();
    	}
	}
}
