package net.stemmaweb.parser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.internal.helpers.collection.Iterables;
import org.w3c.dom.Document;

import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;

/**
 * Utility functions for the parsers
 * Created by tla on 14/02/2017.
 */
public class Util {

    // Start and end node creation
    static Node createStartNode(Transaction tx, Node parentNode) {
        Node startNode = tx.createNode(Nodes.READING);
        startNode.setProperty("is_start", true);
        startNode.setProperty("section_id", parentNode.getElementId());
        startNode.setProperty("rank", 0L);
        startNode.setProperty("text", "#START#");
        parentNode.createRelationshipTo(startNode, ERelations.COLLATION);
        return startNode;
    }

    // Start and end node creation
    static Node createEndNode(Transaction tx, Node parentNode) {
        Node endNode = tx.createNode(Nodes.READING);
        endNode.setProperty("is_end", true);
        endNode.setProperty("section_id", parentNode.getElementId());
        endNode.setProperty("text", "#END#");
        parentNode.createRelationshipTo(endNode, ERelations.HAS_END);
        return endNode;
    }

    // Witness node creation
    static Node createWitness(Transaction tx, String sigil, Boolean hypothetical) throws IllegalArgumentException {
        // First check if the sigil has any characters that will cause trouble for REST
        for (String illegal : new String[] {"<", ">", "#", "%", "\"", "{", "}", "|", "\\", "^", "[", "]", "`", "(", ")"})
            if (sigil.contains(illegal))
                throw new IllegalArgumentException("The character " + illegal + " may not appear in a sigil name.");
        Node witnessNode = tx.createNode(Nodes.WITNESS);
        witnessNode.setProperty("sigil", sigil);
        witnessNode.setProperty("hypothetical", hypothetical);
        witnessNode.setProperty("quotesigil", !isDotId(sigil));

        return witnessNode;
    }

    static Node findOrCreateExtant(Transaction tx, Node traditionNode, String sigil) {
        // This list should contain either zero or one items.
        ArrayList<Node> existingWit = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)
                .stream().filter(x -> x.hasProperty("hypothetical")
                        && x.getProperty("hypothetical").equals(false)
                        && x.getProperty("sigil").equals(sigil))
                .collect(Collectors.toCollection(ArrayList::new));
        if (existingWit.isEmpty()) {
            Node witnessNode = createWitness(tx, sigil, false);
            traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
            return witnessNode;
        } else {
            return existingWit.getFirst();
        }
    }

    static void ensureSectionLink (Transaction tx, Node traditionNode, Node sectionNode) {
        String tradId = traditionNode.getProperty("id").toString();
        ArrayList<Node> tsections = VariantGraphService.getSectionNodes(tx, tradId);
        if (!tsections.contains(sectionNode)) {
            traditionNode.createRelationshipTo(sectionNode, ERelations.PART);
            if (!tsections.isEmpty())
                tsections.getLast().createRelationshipTo(sectionNode, ERelations.NEXT);
        }
    }

    private static Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

    // XML parsing utilities
    static Document openFileStream(InputStream filestream) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Zip parsing utilities - public because also used by test suite
    // Returns a structure which is a list of zip
    public static LinkedHashMap<String, InputStream> extractGraphMLZip(InputStream is) throws IOException {
        LinkedHashMap<String, InputStream> result = new LinkedHashMap<>();
        BufferedInputStream buf = new BufferedInputStream(is);
        ZipInputStream zipIn = new ZipInputStream(buf);
        ZipEntry ze;
        while ((ze = zipIn.getNextEntry()) != null) {
            result.put(ze.getName(), new ByteArrayInputStream(zipIn.readAllBytes()));
            zipIn.closeEntry();
        }
        zipIn.close();
        return result;
    }

    // Helper to get any existing SEQUENCE link between two readings.
    // NOTE: For use inside a transaction
    static Relationship getSequenceIfExists (Node source, Node target) {
        Relationship found = null;
        List<Relationship> allseq = DatabaseService.getRelationships(source, Direction.OUTGOING, ERelations.SEQUENCE);
        for (Relationship r : allseq) {
            if (r.getEndNode().equals(target)) {
                found = r;
                break;
            }
        }
        return found;
    }

    // Helper to set colocation flags on all colocated RELATED links.
    // NOTE: For use inside a transaction
    static void setColocationFlags (Transaction tx, Node traditionNode) {
        HashSet<String> colocatedTypes = new HashSet<>();
        for (Relationship r : DatabaseService.getRelationships(traditionNode, Direction.OUTGOING, ERelations.HAS_RELATION_TYPE)) {
            RelationTypeModel relType = new RelationTypeModel(r.getEndNode());
            if (relType.getIs_colocation()) colocatedTypes.add(relType.getName());
        }

        // Traverse the tradition looking for these types
        for (Relationship rel : VariantGraphService.returnTraditionRelations(tx, traditionNode).relationships()) {
            if (colocatedTypes.contains(rel.getProperty("type").toString()))
                rel.setProperty("colocation", true);
            else if (rel.hasProperty("colocation"))
                rel.removeProperty("colocation");
        }
    }

    public static PathExpander<Void> getExpander (Direction d, String stemmaName) {
        final String pStemmaName = stemmaName;
        return new PathExpander<>() {
            @Override
            public ResourceIterable<Relationship> expand(Path path, BranchState branchState) {
                ArrayList<Relationship> goodPaths = new ArrayList<>();
                for (Relationship link : DatabaseService.getRelationships(path.endNode(), d, ERelations.TRANSMITTED)) {
                    if (link.getProperty("hypothesis").equals(pStemmaName)) {
                        goodPaths.add(link);
                    }
                }
				return Iterables.resourceIterable(goodPaths);
            }

            @Override
            public PathExpander<Void> reverse() {
                return null;
            }
        };
    }

}
