package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

public class ReadingService {
	
	/**
	 * Checks if both readings can be found in the same path through the
	 * tradition. If yes when merging these nodes the graph would get cyclic.
	 * 
	 * @param db
	 * @param firstReading
	 * @param secondReading
	 * @return
	 */
	public static boolean wouldGetCyclic(GraphDatabaseService db, Node firstReading, Node secondReading) {
		Node lowerRankReading, higherRankReading;
		if ((Long) firstReading.getProperty("dn14") < (Long) secondReading.getProperty("dn14")) {
			lowerRankReading = firstReading;
			higherRankReading = secondReading;
		} else {
			lowerRankReading = secondReading;
			higherRankReading = firstReading;
		}

		// check if higherRankReading is found in one of the paths
		for (Node node : db.traversalDescription().depthFirst().relationships(ERelations.NORMAL, Direction.OUTGOING)
				.uniqueness(Uniqueness.NONE).evaluator(Evaluators.all()).traverse(lowerRankReading).nodes()) {
			if (node.equals(higherRankReading))
				return true;
		}

		return false;
	}
}
