package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;

public class ReadingsDbService {
	
	private final GraphDatabaseService db;
    
	public ReadingsDbService() {
		GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();      
	}

	public List<ReadingModel> sectionReadings(String sectId) {
        ArrayList<ReadingModel> readingModels = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node startNode = VariantGraphService.getStartNode(sectId, db);
            if (startNode == null) throw new Exception("Section " + sectId + " has no start node");
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .relationships(ERelations.EMENDED, Direction.OUTGOING)
                    .evaluator(Evaluators.all())
                    .uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
                    .nodes().forEach(node -> readingModels.add(new ReadingModel(node)));
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return readingModels;
    }
}
