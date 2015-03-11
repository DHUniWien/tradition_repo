package net.stemmaweb.services;

import java.util.Iterator;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

public class DbPathProblemService {

	private GraphDatabaseService db;

	public DbPathProblemService(GraphDatabaseService _db) {
		db = _db;
	}

	public String findPathProblem(String userId, String traditionName,
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
						exceptionString = "no witness found: there is a problem with the data path";
				}
			}
		}
		return exceptionString;
	}

	public GraphDatabaseService getDb() {
		return db;
	}

	public void setDb(GraphDatabaseService db) {
		this.db = db;
	}
}
