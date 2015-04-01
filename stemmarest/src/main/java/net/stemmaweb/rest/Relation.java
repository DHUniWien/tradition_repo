package net.stemmaweb.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.services.DatabaseService;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;

import Exceptions.DataBaseException;

/**
 * 
 * @author jakob
 *
 */
@Path("/relation")
public class Relation implements IResource {
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	/**
	 * 
	 * @return string
	 */
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "The relation api is up and running";
    }
    
    /**
     * 
     * @param relationshipModel
     * @param textId
     * @return
     */
    @POST
    @Path("{textId}/relationships")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
	public Response create(RelationshipModel relationshipModel,
			@PathParam("textId") String textId) {

    	GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
    	
    	Node readingA = getNodeByTradIdAndReadingId(textId, relationshipModel.getSource(), db);
    	Node readingB = getNodeByTradIdAndReadingId(textId, relationshipModel.getTarget(), db);
    	
    	Relationship relationshipAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATIONSHIP);
    	relationshipAtoB.setProperty("type", relationshipModel.getDe11());
    	relationshipAtoB.setProperty("a_derivable_from_b", relationshipModel.getDe0());
    	relationshipAtoB.setProperty("alters_meaning", relationshipModel.getDe1());
    	relationshipAtoB.setProperty("b_derivable_from_a", relationshipModel.getDe3());
    	relationshipAtoB.setProperty("extra", relationshipModel.getDe5());
    	relationshipAtoB.setProperty("is_significant", relationshipModel.getDe6());
    	relationshipAtoB.setProperty("non_independent", relationshipModel.getDe7());
    	relationshipAtoB.setProperty("reading_a", relationshipModel.getDe8());
    	relationshipAtoB.setProperty("reading_b", relationshipModel.getDe9());
    	
		return Response.status(Response.Status.CREATED).build();
	}
    
    /**
     * 
     * @param traditionId
     * @param readingId
     * @param db
     * @return
     */
    private Node getNodeByTradIdAndReadingId(String traditionId, String readingId, GraphDatabaseService db)
    {
    	
		Node reading = null;
		Node startNode = null;
		
		try {
			DatabaseService service = new DatabaseService(db);
			startNode = service.getStartNode(traditionId);
		} catch (DataBaseException e) {
			return null;
		}
    	
		try (Transaction tx = db.beginTx()) {
			if (startNode.getId()==Integer.parseInt(readingId)) {
				reading = startNode;
			} else {
				Traverser traverser = getReading(startNode, db);
				for (org.neo4j.graphdb.Path path : traverser) {
					long id = path.endNode().getId();
					if (id==Integer.parseInt(readingId)) {
						reading = path.endNode();
						break;
					}
				}
			}
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}
		
		return reading;
    }
    
    /**
     * 
     * @param reading
     * @param db
     * @return
     */
	private Traverser getReading(final Node reading, GraphDatabaseService db) {
		TraversalDescription td = db.traversalDescription().breadthFirst()
				.relationships(ERelations.NORMAL, Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());
		return td.traverse(reading);
	}
}
