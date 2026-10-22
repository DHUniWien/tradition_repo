package net.stemmaweb.exporter;

import static net.stemmaweb.Util.jsonerror;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Resource;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import net.stemmaweb.model.RelationTypeModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.VariantGraphService;
import net.stemmaweb.services.WitnessPath;

/**
 * Streams sections of a tradition as CollateX JSON ("CX"), the format that
 * {@link net.stemmaweb.parser.CollateXJsonParser} reads.
 * <p>
 * A section is written as {@code {"name": ..., "witnesses": [...], "table": [row, ...]}}, where
 * each row holds one cell per witness column, a cell being {@code []} or {@code [token]}. A
 * tradition is written as {@code {"name": ..., "sections": [section, ...]}}.
 * <p>
 * The output is produced rank by rank from one lazy path cursor per witness column, so memory
 * use scales with the number of columns rather than the length of the text (except when
 * {@code conflate} is used, since normalization writes into the transaction state).
 */
public class CollateXJsonExporter {

    private static final String MEDIA_TYPE = "application/json; charset=utf-8";
    private static final String LACUNA = "#LACUNA#";
    private static final JsonFactory JSON_FACTORY = new JsonFactory()
            // A failure mid-stream must not produce a syntactically complete (but truncated) file,
            // and the container owns the output stream.
            .disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    private final GraphDatabaseService db;

    public CollateXJsonExporter(GraphDatabaseService db) {
        this.db = db;
    }

    /** Thrown during pre-stream validation; carries the response to return. */
    private static class ValidationFailure extends Exception {
        private final Response response;
        ValidationFailure(Response.Status status, String message) {
            super(message);
            this.response = Response.status(status).type(MEDIA_TYPE).entity(jsonerror(message)).build();
        }
    }

    /**
     * Export a single section as a CX object.
     *
     * @param tradId        the tradition ID
     * @param sectionId     the section ID
     * @param conflate      a relation type whose related readings should be conflated, or null
     * @param excludeLayers whether to omit witness layer columns
     * @return a streaming 200 response, or a 404/400 with a JSON error body
     */
    public Response exportSection(String tradId, String sectionId, String conflate, boolean excludeLayers) {
        final String sectionNumericId;
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = validateTradition(tx, tradId, conflate);
            sectionNumericId = findSection(tx, traditionNode, sectionId).getProperty("id").toString();
        } catch (ValidationFailure vf) {
            return vf.response;
        }

        StreamingOutput stream = out -> {
            try (Transaction tx = db.beginTx()) {
                Node sectionNode = DatabaseService.findNodeOrThrow(tx, Nodes.SECTION, sectionNumericId);
                if (conflate != null) normalize(tx, sectionNode, conflate);
                JsonGenerator g = JSON_FACTORY.createGenerator(out, JsonEncoding.UTF8);
                writeSection(g, tx, sectionNode, excludeLayers, conflate != null);
                g.close(); // flushes; leaves the container's stream open
                // Never committed - normalization (if any) is discarded on close.
            } catch (Exception e) {
                throw abort(e);
            }
        };
        return Response.ok(stream, MEDIA_TYPE).build();
    }

    /**
     * Export some or all sections of a tradition, as {@code {"name": ..., "sections": [...]}}.
     *
     * @param tradId        the tradition ID
     * @param conflate      a relation type whose related readings should be conflated, or null
     * @param sectionList   the sections to include (in tradition order); all sections if empty
     * @param excludeLayers whether to omit witness layer columns
     * @return a streaming 200 response, or a 404/400 with a JSON error body
     */
    public Response exportTradition(String tradId, String conflate, List<String> sectionList, boolean excludeLayers) {
        final String traditionName;
        final List<String> sectionIds = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node traditionNode = validateTradition(tx, tradId, conflate);
            traditionName = traditionNode.getProperty("name", "").toString();
            // Validate the requested sections, then keep them in tradition order
            LinkedHashSet<String> requested = new LinkedHashSet<>();
            if (sectionList != null)
                for (String sid : sectionList)
                    requested.add(findSection(tx, traditionNode, sid).getProperty("id").toString());
            for (Node s : VariantGraphService.getSectionNodes(tx, tradId)) {
                String sid = s.getProperty("id").toString();
                if (requested.isEmpty() || requested.contains(sid))
                    sectionIds.add(sid);
            }
        } catch (ValidationFailure vf) {
            return vf.response;
        }

        StreamingOutput stream = out -> {
            try (Transaction tx = db.beginTx()) {
                List<Node> sections = new ArrayList<>();
                for (String sid : sectionIds)
                    sections.add(DatabaseService.findNodeOrThrow(tx, Nodes.SECTION, sid));
                // Normalize everything before any output starts
                if (conflate != null)
                    for (Node s : sections) normalize(tx, s, conflate);
                JsonGenerator g = JSON_FACTORY.createGenerator(out, JsonEncoding.UTF8);
                g.writeStartObject();
                g.writeStringField("name", traditionName);
                g.writeArrayFieldStart("sections");
                for (Node s : sections)
                    writeSection(g, tx, s, excludeLayers, conflate != null);
                g.writeEndArray();
                g.writeEndObject();
                g.close(); // flushes; leaves the container's stream open
            } catch (Exception e) {
                throw abort(e);
            }
        };
        return Response.ok(stream, MEDIA_TYPE).build();
    }

    // Validation helpers

    private static Node validateTradition(Transaction tx, String tradId, String conflate) throws ValidationFailure {
        Node traditionNode = VariantGraphService.getTraditionNode(tx, tradId);
        if (traditionNode == null)
            throw new ValidationFailure(Response.Status.NOT_FOUND, "Tradition not found");
        if (conflate != null && new RelationTypeModel(conflate).lookup(traditionNode) == null)
            throw new ValidationFailure(Response.Status.BAD_REQUEST,
                    "Relation type " + conflate + " does not exist in this tradition");
        return traditionNode;
    }

    private static Node findSection(Transaction tx, Node traditionNode, String sectionId) throws ValidationFailure {
        try {
            Node sectionNode = DatabaseService.findNodeOrThrow(tx, Nodes.SECTION, sectionId);
            Relationship part = sectionNode.getSingleRelationship(ERelations.PART, Direction.INCOMING);
            if (part != null && part.getStartNode().equals(traditionNode))
                return sectionNode;
        } catch (NotFoundException | IllegalArgumentException e) {
            // fall through
        }
        throw new ValidationFailure(Response.Status.BAD_REQUEST, "Section " + sectionId + " not found in tradition");
    }

    private static void normalize(Transaction tx, Node sectionNode, String conflate) throws Exception {
        // A bare section with no start node has nothing to normalize
        if (sectionNode.hasRelationship(Direction.OUTGOING, ERelations.COLLATION))
            VariantGraphService.normalizeGraph(tx, sectionNode, conflate);
    }

    // The status line may have gone out already, so the response status can no longer change.
    // Log, and propagate the failure; because AUTO_CLOSE_JSON_CONTENT is disabled the JSON is left
    // unbalanced, so a truncated body cannot parse as a complete document (the container may still
    // end the response normally as HTTP 200, so clients must parse the body to confirm completeness).
    private static IOException abort(Exception e) {
        e.printStackTrace();
        if (e instanceof IOException ioe) return ioe;
        return new IOException("CX export failed mid-stream: " + e.getMessage(), e);
    }

    // Writing

    /** One witness (or witness layer) column, with its path cursor and a one-node lookahead. */
    private static class Column {
        final String name;
        final Iterator<Path> cursor;
        Node next;
        boolean prevLacuna = false;

        Column(String name, Iterator<Path> cursor) {
            this.name = name;
            this.cursor = cursor;
        }

        // The traverser's iterator holds relationship cursors; it is a Resource in the
        // embedded implementation, though the public Traverser API only promises an Iterator.
        void close() {
            if (cursor instanceof Resource r) r.close();
        }

        void advance() {
            next = cursor.hasNext() ? cursor.next().endNode() : null;
        }
    }

    private void writeSection(JsonGenerator g, Transaction tx, Node sectionNode, boolean excludeLayers,
                              boolean normalized) throws IOException {
        String sectId = sectionNode.getProperty("id").toString();
        g.writeStartObject();
        g.writeStringField("name", sectionNode.getProperty("name", "").toString());

        Node startNode = VariantGraphService.getStartNode(tx, sectId);
        Node endNode = VariantGraphService.getEndNode(tx, sectId);
        if (startNode == null || endNode == null) {
            // A bare section with no readings
            g.writeArrayFieldStart("witnesses");
            g.writeEndArray();
            g.writeArrayFieldStart("table");
            g.writeEndArray();
            g.writeEndObject();
            return;
        }

        RelationshipType seqType = normalized ? ERelations.NSEQUENCE : ERelations.SEQUENCE;
        HashMap<String, HashSet<String>> layersByWitness = excludeLayers
                ? new HashMap<>()
                : VariantGraphService.witnessLayers(tx, startNode, seqType);
        Node traditionNode = VariantGraphService.getTraditionNode(tx, sectionNode);

        List<Column> columns = new ArrayList<>();
        try {
            // Open one lazy path cursor per witness layer, and peek at its first node
            for (Node w : DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS)) {
                String sigil = w.getProperty("sigil").toString();
                List<String> layers = new ArrayList<>();
                layers.add("__base__");
                layers.addAll(new TreeSet<>(layersByWitness.getOrDefault(sigil, new HashSet<>())));
                for (String layer : layers) {
                    boolean isBase = layer.equals("__base__");
                    List<String> alternatives = isBase ? new ArrayList<>() : List.of(layer);
                    Iterator<Path> cursor = tx.traversalDescription().depthFirst()
                            .relationships(seqType, Direction.OUTGOING)
                            .evaluator(new WitnessPath(sigil, alternatives, seqType).getEvalForWitness())
                            .uniqueness(Uniqueness.NODE_PATH)
                            .traverse(startNode)
                            .iterator();
                    Column c = new Column(isBase ? sigil : String.format("%s (%s)", sigil, layer), cursor);
                    columns.add(c);
                    c.advance();
                }
            }
            // Drop the columns with no readings in this section
            List<Column> live = new ArrayList<>();
            for (Column c : columns)
                if (c.next != null && !c.next.equals(endNode)) live.add(c);
            live.sort(Comparator.comparing(c -> c.name));

            g.writeArrayFieldStart("witnesses");
            for (Column c : live) g.writeString(c.name);
            g.writeEndArray();

            g.writeArrayFieldStart("table");
            long endRank = rankOf(endNode);
            Node[] cells = new Node[live.size()];
            boolean[] lacunaFill = new boolean[live.size()];
            for (long rank = 1; rank < endRank; rank++) {
                boolean rowHasContent = false;
                for (int i = 0; i < live.size(); i++) {
                    Column c = live.get(i);
                    cells[i] = null;
                    lacunaFill[i] = false;
                    boolean peekIsReading = c.next != null && !c.next.equals(endNode);
                    if (peekIsReading) {
                        long peekRank = rankOf(c.next);
                        if (peekRank < rank)
                            throw new IllegalStateException(String.format(
                                    "Reading %s on path of %s has rank %d, below current rank %d",
                                    c.next.getProperty("id", "?"), c.name, peekRank, rank));
                        if (peekRank == rank) {
                            cells[i] = c.next;
                            c.prevLacuna = isLacuna(c.next);
                            c.advance();
                            rowHasContent = true;
                            continue;
                        }
                    }
                    // No reading at this rank: fill in a lacuna if we are inside one
                    if (c.prevLacuna || (peekIsReading && isLacuna(c.next))) {
                        lacunaFill[i] = true;
                        rowHasContent = true;
                    }
                }
                if (!rowHasContent) continue;

                g.writeStartArray();
                for (int i = 0; i < live.size(); i++) {
                    g.writeStartArray();
                    if (cells[i] != null) writeToken(g, cells[i]);
                    else if (lacunaFill[i]) writeLacunaToken(g);
                    g.writeEndArray();
                }
                g.writeEndArray();
            }
            g.writeEndArray();
        } catch (Throwable t) {
            closeAll(columns, t);
            throw t;
        }
        closeAll(columns, null);
        g.writeEndObject();
    }

    /**
     * Close every column, even if some close() calls fail. A failure is added as suppressed to the
     * exception already propagating, if any; otherwise the first failure is rethrown.
     */
    private static void closeAll(List<Column> columns, Throwable propagating) {
        RuntimeException first = null;
        for (Column c : columns) {
            try {
                c.close();
            } catch (RuntimeException e) {
                if (propagating != null) propagating.addSuppressed(e);
                else if (first == null) first = e;
                else first.addSuppressed(e);
            }
        }
        if (first != null) throw first;
    }

    private static long rankOf(Node n) {
        return ((Number) n.getProperty("rank")).longValue();
    }

    private static boolean isLacuna(Node n) {
        return Boolean.TRUE.equals(n.getProperty("is_lacuna", false));
    }

    private static void writeLacunaToken(JsonGenerator g) throws IOException {
        g.writeStartObject();
        g.writeStringField("t", LACUNA);
        g.writeStringField("n", LACUNA);
        g.writeEndObject();
    }

    private static void writeToken(JsonGenerator g, Node reading) throws IOException {
        if (isLacuna(reading)) {
            writeLacunaToken(g);
            return;
        }
        String text = reading.getProperty("text", "").toString();
        Object normal = reading.getProperty("normal_form", null);
        g.writeStartObject();
        g.writeStringField("t", text);
        g.writeStringField("n", normal == null ? text : normal.toString());
        if (reading.hasProperty("display"))
            g.writeStringField("display", reading.getProperty("display").toString());
        if (Boolean.TRUE.equals(reading.getProperty("join_next", false)))
            g.writeBooleanField("join_next", true);
        if (Boolean.TRUE.equals(reading.getProperty("join_prior", false)))
            g.writeBooleanField("join_prior", true);
        g.writeEndObject();
    }
}
