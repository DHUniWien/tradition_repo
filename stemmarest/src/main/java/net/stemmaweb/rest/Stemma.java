package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.parser.DotParser;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.exporter.DotExporter;

import org.codehaus.jettison.json.JSONObject;
import org.neo4j.cypher.internal.compiler.v2_0.functions.Str;
import org.neo4j.graphdb.*;

/**
 * Comprises all the api calls related to a stemma.
 * Can be called using http://BASE_URL/stemma
 * @author PSE FS 2015 Team2
 */
public class Stemma {

    private GraphDatabaseService db;
    private String tradId;
    private String name;

    public Stemma (String traditionId, String requestedName) {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
        tradId = traditionId;
        name = requestedName;
    }

    /**
     * Returns JSON string with a Stemma of a tradition in DOT format
     *
     * @return Http Response ok and a stemma model on success or an ERROR in
     *         JSON format
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStemma() {
        Node stemmaNode = getStemmaNode();
        if (stemmaNode == null) {
            return Response.status(Status.NOT_FOUND)
                    .entity(String.format("No stemma %s found for tradition %s", name, tradId)).build();
        }
        StemmaModel result = new StemmaModel(stemmaNode);
        return Response.ok().entity(result).build();
    }

    @POST  // a replacement stemma
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replaceStemma(String dot) {
        DotParser parser = new DotParser(db);
        // Wrap this entire thing in a transaction so that we can roll back
        // the deletion if the replacement import fails.
        try (Transaction tx = db.beginTx()) {
            String originalName = name;
            Response deletionResult = deleteStemma();
            if (deletionResult.getStatus() != 200)
                return deletionResult;

            Response replaceResult = parser.importStemmaFromDot(dot, tradId);
            if (replaceResult.getStatus() != 201)
                return replaceResult;

            // Check that the names matched.
            JSONObject content = new JSONObject(replaceResult.getEntity().toString());
            if (!content.get("name").equals(originalName)) {
                String errormsg = "Name mismatch between original and replacement stemma";
                return Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\":\"" + errormsg + "\"}").build();
            }

            // OK, we can commit it.
            tx.success();
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }


    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteStemma() {
        Node stemmaNode = getStemmaNode();
        assert stemmaNode != null;
        try (Transaction tx = db.beginTx()) {
            Set<Relationship> removableRelations = new HashSet<>();
            Set<Node> removableNodes = new HashSet<>();

            // The stemma is removable
            removableNodes.add(stemmaNode);
            removableRelations.add(stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING));

            // Its HAS_WITNESS relations are removable
            stemmaNode.getRelationships(Direction.OUTGOING, ERelations.HAS_WITNESS)
                .forEach(x -> {
                    removableRelations.add(x);
                    removableNodes.add(x.getEndNode());
                });
            stemmaNode.getRelationships(Direction.OUTGOING, ERelations.HAS_ARCHETYPE)
                    .forEach(removableRelations::add);

            // Its associated TRANSMISSION relations are removable
            removableNodes
                    .forEach(n -> n.getRelationships(ERelations.TRANSMITTED, Direction.BOTH)
                            .forEach(r -> {
                                        if (r.getProperty("hypothesis").toString().equals(name))
                                            removableRelations.add(r);
                                    }
                            ));

            // Its witnesses are removable if they have no links left
            removableRelations.forEach(Relationship::delete);
            removableNodes.stream().filter(x -> !x.hasRelationship()).forEach(Node::delete);
            tx.success();
            return Response.ok().build();
        } catch (Exception e ){
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }
    /**
     * Reorients a stemma tree with a given new root node
     *
     * @param tradId - tradition ID
     * @param name   - stemma name
     * @param nodeId - archetype node
     * @return Http Response ok and DOT JSON string on success or an ERROR in
     *         JSON format
     */
    @POST
    @Path("reorient/{nodeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reorientStemma(@PathParam("tradId") String tradId,
                                   @PathParam("name") String name,
                                   @PathParam("nodeId") String nodeId) {

        try (Transaction tx = db.beginTx())
        {
            // Get the stemma and the witness
            Result foundStemma = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name
                    + "'})-[:HAS_WITNESS]->(w:WITNESS {sigil:'" + nodeId + "'}) return s, w");
            if(!foundStemma.hasNext()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            Map<String, Object> queryRow = foundStemma.next();
            Node stemma    = (Node) queryRow.get("s");
            Node archetype = (Node) queryRow.get("w");

            // Delete its current HAS_ARCHETYPE, if any
            Relationship currentArchetype = stemma.getSingleRelationship(ERelations.HAS_ARCHETYPE, Direction.OUTGOING);
            if (currentArchetype != null)
                currentArchetype.delete();

            // Set the new archetype
            stemma.createRelationshipTo(archetype, ERelations.HAS_ARCHETYPE);
            // and make sure the stemma is directed.
            stemma.setProperty("directed", true);

        tx.success();
        }
        return getStemma();

    }

    private Node getStemmaNode () {
        try (Transaction tx = db.beginTx()) {
            Result query = db.execute("match (:TRADITION {id:'" + tradId
                    + "'})-[:HAS_STEMMA]->(s:STEMMA {name:'" + name + "'}) return s");
            ResourceIterator<Node> foundStemma = query.columnAs("s");
            tx.success();
            if (!foundStemma.hasNext())
                return null;
            return foundStemma.next();
        }
    }

}