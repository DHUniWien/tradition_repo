package net.stemmaweb.model;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.HashMap;

/**
 * A REST-returnable model for user-defined entities and relationships.
 * Created by tla on 16/02/2017.
 */
public class UserNodeModel {

    private HashMap<String, String[]> nodeProperties;

    public UserNodeModel(Node userNode) {
        GraphDatabaseService db = userNode.getGraphDatabase();
        nodeProperties = new HashMap<>();
        Node systemNode = userNode.getSingleRelationship(ERelations.INSTANCE_OF, Direction.INCOMING).getStartNode();
        try (Transaction tx = db.beginTx()) {
            for (String prop : userNode.getPropertyKeys()) {
                if (!prop.startsWith("__")) {
                    // Find the property data type
                    String dataType = systemNode.getProperty(prop).toString();
                    String[] propInfo = new String[]{dataType, userNode.getProperty(prop).toString()};
                    nodeProperties.put(prop, propInfo);
                }
            }
            tx.success();
        }
    }

    @SuppressWarnings("unused")
    public String[] getValueFor (String key) { return nodeProperties.get(key); }
    @SuppressWarnings("unused")
    public void setValueFor (String key, String type, String val) { nodeProperties.put(key, new String[]{type, val}); }
}
