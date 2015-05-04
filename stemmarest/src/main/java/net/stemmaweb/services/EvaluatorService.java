package net.stemmaweb.services;

import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

public class EvaluatorService {
	
	public static Evaluator getEvalForWitness(final String WITNESS_ID) {
		Evaluator e = new Evaluator() {
			@Override
			public Evaluation evaluate(org.neo4j.graphdb.Path path) {

				if (path.length() == 0)
					return Evaluation.EXCLUDE_AND_CONTINUE;

				boolean includes = false;
				boolean continues = false;

				if (path.lastRelationship().hasProperty("lexemes")) {
					String[] arr = (String[]) path.lastRelationship()
							.getProperty("lexemes");
					for (String str : arr) {
						if (str.equals(WITNESS_ID)) {
							includes = true;
							continues = true;
						}
					}
				}
				return Evaluation.of(includes, continues);
			}
		};
		return e;
	}
}
