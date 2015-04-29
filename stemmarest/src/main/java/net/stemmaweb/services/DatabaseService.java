package net.stemmaweb.services;

import java.util.Iterator;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * 
 * Helper methods for the database
 * 
 * @author jakob, ido
 *
 */
public class DatabaseService {

	private GraphDatabaseService db;

	/**
	 * @Deprecated Use the static Methods instead
	 * @param db
	 */
	@Deprecated
	public DatabaseService(GraphDatabaseService db) {
		this.db = db;
	}

	/**
	 * gets the "start" node of a tradition
	 * 
	 * @param traditionName
	 * @param userId
	 * 
	 * @return the start node of a witness
	 * 
	 * @Deprecated use getStartNode(String tradId, GraphDatabaseService db)
	 *             instead
	 */
	@Deprecated
	public Node getStartNode(String tradId) {

		ExecutionEngine engine = new ExecutionEngine(db);
		// DbPathProblemService problemFinder = new DbPathProblemService();
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
		}
		return startNode;
	}

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
		}
		return startNode;
	}

	/**
	 * creates the root node
	 * 
	 */
	@Deprecated
	public void createRootNode() {
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
		} finally {
			db.shutdown();
		}
	}

	/**
	 * 
	 * @param db
	 *            the GraphDatabaseService where the Database should be entered
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
		}
		return false;
	}

	//should be exchanged with db.getNodeById(nodeId)
	@Deprecated
	public static Node getReadingById(long readId1, Node startNode,
			GraphDatabaseService db) {

		try (Transaction tx = db.beginTx()) {

			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(Evaluators.all())
					.uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
					.nodes()) {
				if (node.getId()==readId1) {
					tx.success();
					return node;
				}
			}
			tx.success();
			Node node = null;
			return node;
		}
	}	
}
