package net.stemmaweb.services;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;

public class WitnessDbService {

private final GraphDatabaseService db;
    
	public WitnessDbService() {
		GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();      
	}

	
	public WitnessModel getWitnessBySigil(String tradId, String sigil) {
        Node tradNode = VariantGraphService.getTraditionNode(tradId, db);
        Node found = null;
        try (Transaction tx = db.beginTx()) {
            for (Relationship r : tradNode.getRelationships(ERelations.HAS_WITNESS, Direction.OUTGOING)) {
                Node wit = r.getEndNode();
                if (wit.hasProperty("sigil") && wit.getProperty("sigil").equals(sigil)) {
                    found = wit;
                    break;
                }
            }
            tx.success();
        }
        return new WitnessModel(found);
    }
}
