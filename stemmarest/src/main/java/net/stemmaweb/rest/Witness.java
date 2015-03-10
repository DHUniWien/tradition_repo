package net.stemmaweb.rest;

import java.util.Iterator;

import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.Transaction;

import Exceptions.DataBaseException;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

/**
 * 
 * @author jakob/ido
 *
 **/

@Path("/witness")
public class Witness {
	public static final String DB_PATH = "database";
	private GraphDatabaseService db = new GraphDatabaseFactory()
			.newEmbeddedDatabase(DB_PATH);

	/*
	 * gets the requested witness as a string
	 */

	@GET
	@Path("{textId}, {userId}, {traditionName}")
	@Produces("text/plain")
	public String getWitnssAsPlainText(@PathParam("userId") String userId,
			@PathParam("traditionName") String traditionName,
			@PathParam("textId") String textId) throws DataBaseException {
		String witnessAsText = "";

		ExecutionEngine engine = new ExecutionEngine(db);

		ExecutionResult result;
		String witnessQuary = "match (user:USER {id:'" + userId
				+ "'})--(tradition:TRADITION {name:'" + traditionName
				+ "'})--(w:WORD  {name:'" + traditionName + "'})-[:NORMAL {lexemes:'"+textId+"'}]->(n:WORD) return n";

		/*
		 * match (user:USER {id:'" + userId +
		 * "'})--(tradition:TRADITION {name:'" + traditionName +
		 * "'})--(w:WORD {name:'" + traditionName + "'}) return w
		 */
		/*
		 * , p=(s)-[:NORMAL*0.. {id:'" + textId + "'}]->(b)" + " where b.id= '"
		 * + traditionName +
		 * "__END__' return extract(n IN nodes(p)| n.text) AS extracted";
		 */

		try (Transaction tx = db.beginTx()) {
			Node witnessNode;
			result = engine.execute(witnessQuary);
			Iterator<Node> nodes = result.columnAs("n");

			if (!nodes.hasNext())
				throw new DataBaseException(findPathProblem(userId,
						traditionName, textId));
			else
				witnessNode = nodes.next();

			for (Node witnessNodes : db.traversalDescription().depthFirst()
					.relationships(Relations.NORMAL, Direction.OUTGOING)
					.evaluator(Evaluators.toDepth(20)).traverse(witnessNode)
					.nodes()) {
				witnessAsText += witnessNodes.getProperty("text") + " ";
			}

		}
		return witnessAsText;
	}

	/**
	 * 
	 * @param textId
	 * @return a witness as a list of readings
	 */

	@Path("{textId}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getWitnessAsReadings(@PathParam("textId") String textId) {

		return textId;
	}

	/**
	 * will find the missing link in the data base path in case of an empty
	 * result in the witness search
	 * 
	 * @param userId
	 * @param traditionName
	 * @param textId
	 * @return the text to be displayed in the exception
	 */
	private String findPathProblem(String userId, String traditionName,
			String textId) {
		String exceptionString = "";
		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {

			ExecutionResult userResult = engine.execute("match (u:USER {id:'"
					+ userId + "'}) return u");
			Iterator<Node> users = userResult.columnAs("u");
			if (!users.hasNext())
				exceptionString = "such user does not exist in the system";
			else {
				ExecutionResult traditionResult = engine
						.execute("match (t:TRADITION {name:'" + traditionName
								+ "'}) return t");
				Iterator<Node> traditions = traditionResult.columnAs("t");

				if (!traditions.hasNext())
					exceptionString = "such trsdition does not exist in the system";
				else {
					ExecutionResult witnessResult = engine
							.execute("match (w:TRADITION {id:'" + traditionName
									+ "__START__'}) return w");
					Iterator<Node> witnesses = witnessResult.columnAs("w");

					if (!witnesses.hasNext())
						exceptionString = "such witness does not exist in the system";
					else
						exceptionString = "there is some unknown problem with the data path";
				}
			}
		}

		return exceptionString;
	}

	public void setDb(GraphDatabaseService graphDb) {
		db = graphDb;
	}
}
