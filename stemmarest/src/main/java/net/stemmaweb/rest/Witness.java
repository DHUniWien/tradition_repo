package net.stemmaweb.rest;

import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.neo4j.cypher.ExecutionEngine;
import org.neo4j.cypher.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.util.StringLogger;

import Exeptions.DataBaseExeption;

/**
 * 
 * @author jakobschaerer/ido
 *
 **/

@Path("/witness")
public class Witness {
	public static final String DB_PATH = "database";

	/*
	 * gets the requested witness as a string
	 */

	@GET
	@Path("{textId}")
	@Produces("text/plain")
	public String getWitnssAsPlainText(@PathParam("textId") String textId)
			throws DataBaseExeption {
		Node node;
		String witnessAsText = "";
		GraphDatabaseService db = new GraphDatabaseFactory()
				.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db, StringLogger.SYSTEM);

		ExecutionResult result;
		try (Transaction tx = db.beginTx()) {

			result = engine.execute("match (n {leximes: '" + textId
					+ "'}) return n");
			Iterator<Node> nodes = (Iterator<Node>) result.columnAs("n");
			if (nodes.hasNext())
				throw new DataBaseExeption("more that one node with same Id");
			node = nodes.next();
			while (nodes.hasNext()) { // TODO not correct! only temporary!
				result = engine.execute("match (" + node
						+ "-[NORMAL]-(b)) return b"); // not sure if this will
														// work
				nodes = (Iterator<Node>) result.columnAs("b");
				node = nodes.next();
				if (nodes.hasNext())
					throw new DataBaseExeption(
							"more that one NORMAL relationship to a single node");

				witnessAsText += "" + node.getProperty("word");  // TODO check property name
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

}
