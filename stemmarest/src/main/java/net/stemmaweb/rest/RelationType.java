package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static net.stemmaweb.rest.Util.jsonerror;

public class RelationType {
    private GraphDatabaseService db;
    /**
     * The name of a type of reading relationship.
     */
    private String traditionId;
    private String typeName;

    public RelationType(String tradId, String requestedType) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        traditionId = tradId;
        typeName = requestedType;
    }

    /**
     * Gets the information for the given relationship type name.
     *
     * @summary Get relationship type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("net.stemmaweb.model.RelationTypeModel")
    public Response getRelationType() {
        RelationTypeModel rtModel = null;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
            Node foundRelType;
            for (Relationship r : traditionNode.getRelationships(ERelations.HAS_RELATION_TYPE, Direction.OUTGOING)) {
                if (r.getEndNode().getProperty("name").equals(typeName)) {
                    rtModel = new RelationTypeModel(r.getEndNode());
                    break;
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        if (rtModel == null) {
            return Response.noContent().build();
        }
        return Response.ok(rtModel).build();
    }

}
