package net.stemmaweb.parser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
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
        Node startNode = DatabaseService.createNode(tx, Nodes.READING);
        startNode.setProperty("is_start", true);
        startNode.setProperty("section_id", parentNode.getProperty("id").toString());
        startNode.setProperty("rank", 0L);
        startNode.setProperty("text", "#START#");
        parentNode.createRelationshipTo(startNode, ERelations.COLLATION);
        return startNode;
    }

    // Start and end node creation
    static Node createEndNode(Transaction tx, Node parentNode) {
        Node endNode = DatabaseService.createNode(tx, Nodes.READING);
        endNode.setProperty("is_end", true);
        endNode.setProperty("section_id", parentNode.getProperty("id").toString());
        endNode.setProperty("text", "#END#");
        parentNode.createRelationshipTo(endNode, ERelations.HAS_END);
        return endNode;
    }

    // NCName pattern for validation
    private static final String NCNAME_START_CHARS =
            "A-Za-z_\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF"
                    + "\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD";
    private static final String NCNAME_CHARS =
            NCNAME_START_CHARS + "\\-.0-9\\u00B7\\u0300-\\u036F\\u203F\\u2040";
    private static final Pattern NCNAME_PATTERN =
            Pattern.compile("^[" + NCNAME_START_CHARS + "][" + NCNAME_CHARS + "]*$");

    /**
     * Validates that a witness sigil is a legal NCName for use in TEI exports.
     *
     * @param sigil the candidate sigil
     * @throws IllegalArgumentException if sigil is null or not a valid NCName
     */
    public static void validateSigil(String sigil) throws IllegalArgumentException {
        if (sigil == null || !NCNAME_PATTERN.matcher(sigil).matches())
            throw new IllegalArgumentException(String.format(
                    "The sigil \"%s\" is not a valid name: it must start with a letter or underscore "
                            + "and contain only letters, digits, underscores, hyphens, or periods thereafter",
                    sigil));
    }

    // Characters that would break a REST URL path segment.
    private static final String[] PATH_UNSAFE_CHARS =
            {"<", ">", "#", "%", "\"", "{", "}", "|", "\\", "^", "[", "]", "`", "(", ")"};

    /**
     * Validates the sigil of a hypothetical (non-extant) stemma witness. Don't need NCNames
     * but do need REST path compatibility.
     *
     * @param sigil the candidate sigil
     * @throws IllegalArgumentException if sigil is null or empty, or contains a character that
     *         would break a REST URL path segment
     */
    private static void validateHypotheticalSigil(String sigil) throws IllegalArgumentException {
        if (sigil == null || sigil.isEmpty())
            throw new IllegalArgumentException("A witness sigil may not be empty.");
        for (String illegal : PATH_UNSAFE_CHARS)
            if (sigil.contains(illegal))
                throw new IllegalArgumentException("The character " + illegal + " may not appear in a sigil name.");
    }

    /**
     * Validates a sigil for a new witness: full NCName validation (see {@link #validateSigil})
     * for an extant witness, whose sigil ends up as a TEI xml:id on export; the lighter
     * path-safety check for a hypothetical stemma witness, which is never exported to TEI.
     *
     * @param sigil the candidate sigil
     * @param hypothetical whether the witness is hypothetical
     * @throws IllegalArgumentException if the sigil is not valid for this kind of witness
     */
    public static void validateWitnessSigil(String sigil, Boolean hypothetical) throws IllegalArgumentException {
        if (Boolean.TRUE.equals(hypothetical))
            validateHypotheticalSigil(sigil);
        else
            validateSigil(sigil);
    }

    // Witness node creation
    public static Node createWitness(Transaction tx, String sigil, Boolean hypothetical) throws IllegalArgumentException {
        validateWitnessSigil(sigil, hypothetical);
        Node witnessNode = DatabaseService.createNode(tx, Nodes.WITNESS);
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

    public static Boolean isDotId (String nodeid) {
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
