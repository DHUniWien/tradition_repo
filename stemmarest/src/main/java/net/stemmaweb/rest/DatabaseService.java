package net.stemmaweb.rest;

import java.util.Iterator;

import net.stemmaweb.services.DbPathProblemService;

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
	public Node getStartNode(String tradId) {

		ExecutionEngine engine = new ExecutionEngine(db);
		DbPathProblemService problemFinder = new DbPathProblemService();
		Node startNode = null;

		/**
		 * this quarry gets the "Start" node of the witness
		 */
		String witnessQuarry = "match (tradition:TRADITION {id:'" + tradId
				+ "'})--(w:WORD  {text:'#START#'}) return w";

		try (Transaction tx = db.beginTx()) {

			ExecutionResult result = engine.execute(witnessQuarry);
			Iterator<Node> nodes = result.columnAs("w");

			if (!nodes.hasNext()) {
				throw new DataBaseException(
						problemFinder.findPathProblem(tradId));
			} else
				startNode = nodes.next();

			tx.success();
		}
		return startNode;
	}
}
