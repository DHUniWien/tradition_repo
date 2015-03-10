package net.stemmaweb.rest;

import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.IteratorUtil;

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
				+ "'})-[r1]->(tradition:TRADITION {name:'" + traditionName
				+ "'}), (tradition)-[r2]->(s:WORD {id:'" + traditionName
				+ "__START__'}), p=(s)<--(b)<--(c), (s)<-[r]-(b)"
				+ " where r.id= '" + textId + "' AND c.id= '" + traditionName
				+ "__END__' return extract(n IN nodes(p)| n.text) AS extracted";

		try (Transaction tx = db.beginTx()) {

			result = engine.execute(witnessQuary);
			Iterator<String> texts = result.columnAs("n.text");

			if (!texts.hasNext())
				throw new DataBaseException(findPathProblem(userId,
						traditionName, textId));
			else {
				for (String text : IteratorUtil.asIterable(texts))
					witnessAsText = " " + text;
			}

			/*
			 * String nextWordQuary = "match (n)-[r {id:'" + node.getId() +
			 * "'}]-(b)) return b";
			 * 
			 * while (nodes.hasNext()) { // TODO not correct! only temporary!
			 * result = engine.execute(nextWordQuary); nodes =
			 * result.columnAs("b"); node = nodes.next(); if (nodes.hasNext())
			 * throw new DataBaseException(
			 * "more than one NORMAL relationship to a single node");
			 * 
			 * witnessAsText += " " + node.getProperty("text"); }
			 */
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
		GraphDatabaseService db = new GraphDatabaseFactory()
				.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {

			ExecutionResult userResult = engine.execute("match (n:USER {id:'"
					+ userId + "'}) return n");
			Iterator<Node> users = userResult.columnAs("n");
			if (!users.hasNext())
				exceptionString = "such user does not exist in the system";
			else {
				ExecutionResult traditionResult = engine
						.execute("match (n:TRADITION {name:'" + traditionName
								+ "'}) retun n");
				Iterator<Node> traditions = traditionResult.columnAs("n");

				if (!traditions.hasNext())
					exceptionString = "such trsdition does not exist in the system";
				else {
					ExecutionResult witnessResult = engine
							.execute("match (n:TRADITION {id:'" + traditionName
									+ "__START__'}) retun n");
					Iterator<Node> witnesses = witnessResult.columnAs("n");

					if (!witnesses.hasNext())
						exceptionString = "such wtiness does not exist in the system";
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
