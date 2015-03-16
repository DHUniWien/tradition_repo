package net.stemmaweb.services;

import java.util.Iterator;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

public class DbPathProblemService {
	
	public static final String DB_PATH = "database";

	public String findPathProblem(String tradId) {
		
		String exceptionString = "";
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {

			ExecutionResult traditionResult = engine
					.execute("match (t:TRADITION {id:'" + tradId
							+ "'}) return t");
			Iterator<Node> traditions = traditionResult.columnAs("t");

			if (!traditions.hasNext())
				exceptionString = "such trsdition does not exist in the data base";
			else {
				ExecutionResult witnessResult = engine
						.execute("match (tradition:TRADITION {id:'" + tradId
								+ "'})--(w:WORD  {text:'#START#'}) return w");
				Iterator<Node> witnesses = witnessResult.columnAs("w");

				if (!witnesses.hasNext())
					exceptionString = "such witness does not exist in the data base";
				else
					exceptionString = "no witness found: there is a problem with the data path";
			}
		}
		db.shutdown();
		return exceptionString;
	}

}
