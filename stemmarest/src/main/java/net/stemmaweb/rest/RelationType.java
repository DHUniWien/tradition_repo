package net.stemmaweb.rest;

import com.qmino.miredot.annotations.ReturnType;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.util.HashMap;
import java.util.Map;

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
     * Gets the information for the given relation type name.
     *
     * @summary Get relation type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType("net.stemmaweb.model.RelationTypeModel")
    public Response getRelationType() {
        RelationTypeModel rtModel = new RelationTypeModel(typeName);
        Node foundRelType = rtModel.lookup(DatabaseService.getTraditionNode(traditionId, db));
        if (foundRelType == null) {
            return Response.noContent().build();
        }
        return Response.ok(new RelationTypeModel(foundRelType)).build();
    }

    /**
     * Creates or updates a relation type according to the specification given.
     *
     * @summary Create / update relation type specification
     *
     * @param rtModel - a user specification
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success, if an existing type was updated
     * @statuscode 201 on success, if a new type was created
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @ReturnType(clazz = RelationTypeModel.class)
    public Response create(RelationTypeModel rtModel) {
        // Find any existing relation type on this tradition
        Node traditionNode = DatabaseService.getTraditionNode(traditionId, db);
        Node extantRelType = rtModel.lookup(traditionNode);

        if (extantRelType != null) {
            extantRelType = rtModel.update(traditionNode);
            if (extantRelType != null)
                return Response.status(Response.Status.CREATED).entity(rtModel).build();
        } else {
            extantRelType = rtModel.instantiate(traditionNode);
            if (extantRelType != null)
                return Response.ok().build();
        }
        return Response.serverError().build();
    }

    /**
     * Creates a relation type with the given name according to default values.
     * For use internally.
     *
     * @summary Create a default relationship type
     *
     * @return A JSON RelationTypeModel or a JSON error message
     * @statuscode 200 on success, if an existing type was updated
     * @statuscode 201 on success, if a new type was created
     * @statuscode 500 on failure, with an error report in JSON format
     */
    @ReturnType(clazz = RelationTypeModel.class)
    public Response makeDefaultType() {
        Map<String, String> defaultRelations = new HashMap<String, String>() {{
            put("collated", "Internal use only");
            put("orthographic", "These are the same reading, neither unusually spelled.");
            put("punctuation", "These are the same reading apart from punctuation.");
            put("spelling", "These are the same reading, spelled differently.");
            put("grammatical", "These readings share a root (lemma), but have different parts of speech (morphologies).");
            put("lexical", "These readings share a part of speech (morphology), but have different roots (lemmata).");
            put("uncertain", "These readings are related, but a clear category cannot be assigned.");
            put("other", "These readings are related in a way not covered by the existing types.");
            put("transposition", "This is the same (or nearly the same) reading in a different location.");
            put("repetition", "This is a reading that was repeated in one or more witnesses.");
        }};

        Node tradNode = DatabaseService.getTraditionNode(traditionId, db);
        RelationTypeModel relType = new RelationTypeModel(typeName);
        // Does this already exist?
        Node extantRelType = relType.lookup(tradNode);
        if (extantRelType != null)
            return Response.notModified().build();

        // If we don't have any settings for the requested name, use the settings for "other"
        String useType = typeName;
        if (!defaultRelations.containsKey(typeName)) useType = "other";

        relType.setDescription(defaultRelations.get(useType));
        // Set the bindlevel
        int bindlevel = 0;
        switch (useType) {
            case "spelling":
                bindlevel = 1;
                break;
            case "grammatical":
            case "lexical":
                bindlevel = 2;
                break;
            case "collated":
            case "transposition":
            case "repetition":
                bindlevel = 50;
                break;
        }
        relType.setBindlevel(bindlevel);
        // Set the booleans
        relType.setIs_colocation(!(useType.equals("transposition") || useType.equals("repetition")));
        relType.setIs_weak(useType.equals("collated"));
        relType.setIs_transitive(!(useType.equals("collated") || useType.equals("uncertain")
                || useType.equals("other") || useType.equals("repetition")));
        relType.setIs_generalizable(!(useType.equals("collated")|| useType.equals("uncertain")
                || useType.equals("other")));
        relType.setUse_regular(useType.equals("orthographic"));
        // Create the node
        Node result = relType.instantiate(tradNode);
        if (result == null)
            return Response.serverError().build();
        else
            return Response.status(Response.Status.CREATED).entity(relType).build();
    }
}
