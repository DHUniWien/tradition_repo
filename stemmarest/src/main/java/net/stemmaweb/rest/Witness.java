package net.stemmaweb.rest;

import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

//import org.neo4j.cypher.ExecutionEngine;
//import org.neo4j.cypher.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
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

	/*
	 * gets the requested witness as a string
	 */

	@GET
	@Path("{textId}, {userId}, {traditionName}")
	@Produces("text/plain")
	public String getWitnssAsPlainText(@PathParam("userId") String userId,
			@PathParam("traditionName") String traditionName,
			@PathParam("textId") String textId) throws DataBaseException {
		Node node;
		String witnessAsText = "";
		GraphDatabaseService db = new GraphDatabaseFactory()
				.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);

		ExecutionResult result;
		String witnessQuary = "match s,(user:USER {id:'" + userId
				+ "'})-[r]->(tradition:TRADITION {name:'" + traditionName
				+ "'}), (tradition)-[r]->(s:WORD {id:'" + traditionName
				+ "__START__'}), p=(s)--[r]-->(b)--[r]-->(c)"
						+ " where r.id= '"+ textId + "' AND c.id= '"+traditionName+"__END__' return nodes(p)";

		try (Transaction tx = db.beginTx()) {

			result = engine.execute(witnessQuary);
			Iterator<Node> nodes = result.columnAs("s");
			if (nodes.hasNext())
				throw new DataBaseException(
						"a word with more than one relationship with the same id");
			if (!nodes.hasNext())
				throw new DataBaseException(
						"such witness does not exist in the data base");
			else
				node = nodes.next();
			
			
			/*String nextWordQuary = "match (n)-[r {id:'" + node.getId()
					+ "'}]-(b)) return b";

			while (nodes.hasNext()) { // TODO not correct! only temporary!
				result = engine.execute(nextWordQuary);
				nodes = result.columnAs("b");
				node = nodes.next();
				if (nodes.hasNext())
					throw new DataBaseException(
							"more than one NORMAL relationship to a single node");

				witnessAsText += " " + node.getProperty("text");
			}*/
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
