package net.stemmaweb.services;

import java.util.Iterator;
import net.stemmaweb.rest.Nodes;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * Helper methods for the database
 * 
 * @author PSE FS 2015 Team2
 */
public class DatabaseService {
	/**
	 * 
	 * @param tradId
	 * @param db
	 *            the GraphDatabaseService where the tradition is stored
	 * @return
	 */
	public static Node getStartNode(String tradId, GraphDatabaseService db) {
		ExecutionEngine engine = new ExecutionEngine(db);
		Node startNode = null;

		/**
		 * this quarry gets the "Start" node of the witness
		 */
		String witnessQuarry = "match (tradition:TRADITION {id:'" + tradId
				+ "'})-[:NORMAL]->(w:WORD) return w";

		try (Transaction tx = db.beginTx()) {

			ExecutionResult result = engine.execute(witnessQuarry);
			Iterator<Node> nodes = result.columnAs("w");

			if (!nodes.hasNext()) {
				return null;				
			} else
				startNode = nodes.next();

			tx.success();
		}catch (Exception e){
			return null;
		}
		return startNode;
	}

	/**
	 * 
	 * @param db: the GraphDatabaseService where the Database should be entered
	 * 
	 */
	public static void createRootNode(GraphDatabaseService db) {
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (n:ROOT) return n");
			Iterator<Node> nodes = result.columnAs("n");
			if (!nodes.hasNext()) {
				Node node = db.createNode(Nodes.ROOT);
				node.setProperty("name", "Root node");
				node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
			}
			tx.success();
		}
	}

	/**
	 * This method can be used to determine whether a user with given Id exists
	 * in the DB
	 * 
	 * @param userId
	 * @param db
	 * @return
	 */
	public static boolean checkIfUserExists(String userId,
			GraphDatabaseService db) {
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (userId:USER {id:'"
					+ userId + "'}) return userId");
			Iterator<Node> nodes = result.columnAs("userId");
			if (nodes.hasNext())
				return true;
			tx.success();
		}catch (Exception e) {
			return false;
		} 
		return false;
	}
}
