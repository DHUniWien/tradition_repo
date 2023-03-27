package net.stemmaweb.parser;

import net.sourceforge.olduvai.treejuxtaposer.TreeParser;
import net.sourceforge.olduvai.treejuxtaposer.drawer.Tree;
import net.sourceforge.olduvai.treejuxtaposer.drawer.TreeNode;
import net.stemmaweb.model.StemmaModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;

import static net.stemmaweb.parser.Util.findOrCreateExtant;
import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.Util.jsonresp;

public class NewickParser {
    private final GraphDatabaseService db;

    public NewickParser(GraphDatabaseService db) {
        this.db = db;
    }

    /**
     * Parses the Newick string in a StemmaModel into a stemma object, and returns an appropriate Response.
     *
     * @param tradId     - The ID of the tradition to which this stemma should be added
     * @param stemmaSpec - A StemmaModel containing the specification for the stemma
     * @return a Response whose entity is a JSON response, either {'name':stemmaName} or {'error':errorMessage}
     */
    public Response importStemmaFromNewick(String tradId, StemmaModel stemmaSpec) {
        // Get our tradition
        Node traditionNode = VariantGraphService.getTraditionNode(tradId, db);
        if (traditionNode == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        // Do we already have a stemma by this name? If so, abort.
        try (Transaction tx = db.beginTx()) {
            for (Node priorStemma : DatabaseService.getRelated(traditionNode, ERelations.HAS_STEMMA))
                if (priorStemma.getProperty("name").equals(stemmaSpec.getIdentifier())) return Response.status(Response.Status.CONFLICT)
                        .entity(jsonerror("A stemma by this name already exists for this tradition.")).build();
            tx.close();
        }

        // Parse the tree
        BufferedReader stringReader = new BufferedReader(new StringReader(stemmaSpec.getNewick()));
        TreeParser tp = new TreeParser(stringReader);
        Tree nTree = tp.tokenize(stemmaSpec.getIdentifier());

        // All end nodes are extant, and all intermediate nodes are hypothetical.
        // First ensure that the extant nodes exist as witnesses, then walk the tree making the stemma.
        try (Transaction tx = db.beginTx()) {
            HashMap<Integer,Node> stemmaWits = new HashMap<>();
            // Create the new stemma node
            Node stemmaNode = tx.createNode(Nodes.STEMMA);
            stemmaNode.setProperty("name", stemmaSpec.getIdentifier());
            stemmaNode.setProperty("directed", false);
            if (stemmaSpec.cameFromJobid()) stemmaNode.setProperty("from_jobid", stemmaSpec.getJobid());

            // Create all the nodes (unless they exist already) and store them by ID
            for (TreeNode n : nTree.nodes) {
                Node wit;
                if (n.isLeaf()) {
                    wit = findOrCreateExtant(traditionNode, n.getName());
                } else {
                    // It's a hypothetical node, so make it from scratch.
                    wit = Util.createWitness(traditionNode, String.valueOf(n.getKey()), true);
                }
                stemmaNode.createRelationshipTo(wit, ERelations.HAS_WITNESS);
                stemmaWits.put(n.getKey(), wit);

            }

            // Now go through the tree nodes again and make the relationships to the corresponding neo4j nodes
            // TODO maybe we can do this in one pass, if the nodes are properly ordered in the Newick
            for (TreeNode n : nTree.nodes) {
                Node source = stemmaWits.get(n.getKey());
                for (int i = 0; i < n.numberChildren(); i++) {
                    Node target = stemmaWits.get(n.getChild(i).getKey());
                    Relationship r = source.createRelationshipTo(target, ERelations.TRANSMITTED);
                    r.setProperty("hypothesis", stemmaSpec.getIdentifier());
                }
            }
            // Set an "archetype" even though it's unrooted


            // Finally, connect the stemma to its tradition
            traditionNode.createRelationshipTo(stemmaNode, ERelations.HAS_STEMMA);

            // If the stemma we just imported had a jobID that matches the tradition's stemweb_jobid, clear the latter
            if (stemmaSpec.getJobid() != null && stemmaSpec.getJobid().equals(
                    traditionNode.getProperty("stemweb_jobid", 0)))
                traditionNode.removeProperty("stemweb_jobid");

            tx.close();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonerror(e.getMessage())).build();
        }

        return Response.status(Response.Status.CREATED)
                .entity(jsonresp("name", stemmaSpec.getIdentifier()))
                .build();
    }
}
