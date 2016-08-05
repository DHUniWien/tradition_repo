package net.stemmaweb.parser;

import com.sun.org.apache.xerces.internal.impl.XMLStreamReaderImpl;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.*;

/**
 * Parse a TEI parallel-segmentation file into a tradition graph.
 */
public class TEIParallelSegParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseTEIParallelSeg(InputStream xmldata, String tradId) {
        XMLInputFactory factory;
        XMLStreamReader reader;
        factory = XMLInputFactory.newInstance();
        try {
            reader = factory.createXMLStreamReader(xmldata);
        } catch (XMLStreamException e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error: Parsing of tradition file failed!")
                    .build();
        }

        // Main XML parser loop
        Node startNode;
        try (Transaction tx = db.beginTx()) {

            // Look up the tradition node
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);

            // Set up the start node
            startNode = db.createNode(Nodes.READING);
            startNode.setProperty("is_start", true);
            startNode.setProperty("tradition_id", tradId);
            startNode.setProperty("text", "#START#");
            startNode.setProperty("rank", 0);
            traditionNode.createRelationshipTo(startNode, ERelations.COLLATION);

            // State variables
            Boolean inHeader = false;
            Boolean inText = false;
            Boolean skip = false;
            HashMap<String, Node> priorNode = new HashMap<>();
            HashMap<String, Boolean> activeWitnesses = new HashMap<>();
            ArrayList<String> readingWitnesses = new ArrayList<>();
            String witClass = "witnesses";
            // Fill in the contents of the tradition
            // TODO consider supporting illegible readings, gaps, etc.
            parseloop: while (true) {
                int event = reader.next();

                switch(event) {
                    case XMLStreamConstants.END_DOCUMENT:
                        reader.close();
                        break parseloop;

                    case XMLStreamConstants.END_ELEMENT:
                        skip = false;
                        switch(reader.getLocalName()) {
                            case "teiHeader":
                                inHeader = false;
                                break;
                            case "app":
                                // Leaving an app, reading witnesses are all active witnesses.
                                // Though this doesn't handle nested apps.
                                // TODO We should implement nested apps as a sort of stack.
                                readingWitnesses = new ArrayList<>();
                                activeWitnesses.keySet().stream()
                                        .filter(activeWitnesses::get)
                                        .forEach(readingWitnesses::add);
                                break;

                            case "rdg":
                            case "lem":
                                readingWitnesses = new ArrayList<>();
                                witClass = "witnesses";
                                break;

                            case "text":
                                // End of the text; add the end node.
                                Node endNode = db.createNode(Nodes.READING);
                                endNode.setProperty("text", "#END#");
                                endNode.setProperty("tradition_id", tradId);
                                endNode.setProperty("is_end", true);
                                endNode.setProperty("rank", 0);
                                traditionNode.createRelationshipTo(endNode, ERelations.HAS_END);
                                HashSet<Node> lastNodes = new HashSet<>();
                                activeWitnesses.keySet().forEach(x -> lastNodes.add(priorNode.get(x)));

                                HashSet<Relationship> endLinks = new HashSet<>();
                                lastNodes.forEach(x -> {
                                    if (!x.equals(startNode)) {
                                        Relationship seq = x.createRelationshipTo(endNode, ERelations.SEQUENCE);
                                        endLinks.add(seq);
                                    }
                                });

                                final String wc = witClass;
                                activeWitnesses.keySet().forEach(w -> endLinks.forEach(l -> {
                                    if (l.getStartNode().equals(priorNode.get(w)))
                                        addWitness(l, w, wc);
                                }));
                                break;

                        }
                        break;

                    case XMLStreamConstants.START_ELEMENT:
                        switch(reader.getLocalName()) {

                            // Deal with information from the TEI header
                            case "teiHeader":
                                inHeader = true;
                                break;

                            case "witness":
                                if(inHeader) {
                                    String sigil = reader.getAttributeValue(reader.getNamespaceURI("xml"), "id");
                                    Node witnessNode = db.createNode(Nodes.WITNESS);
                                    witnessNode.setProperty("sigil", sigil);
                                    witnessNode.setProperty("hypothetical", false);
                                    witnessNode.setProperty("quotesigil", isDotId(sigil));
                                    traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
                                    activeWitnesses.put(sigil, false);
                                }
                                break;

                            case "title":
                                if(inHeader && !traditionNode.hasProperty("name")) {
                                    traditionNode.setProperty("name", reader.getElementText());
                                }
                                break;

                            // Now parse the text body
                            case "text":
                                if(!inHeader) {
                                    inText = true;
                                    activeWitnesses.keySet().forEach(x -> priorNode.put(x, startNode));
                                }
                                break;

                            case "rdg":
                            case "lem":
                                readingWitnesses = parseWitnesses(reader.getAttributeValue("", "wit"));
                                String variantClass = reader.getAttributeValue("", "type");
                                if (variantClass != null)
                                    witClass = variantClass;
                                break;

                            case "witStart":
                                readingWitnesses.forEach(x -> activeWitnesses.put(x, true));
                                break;
                            case "witEnd":
                                readingWitnesses.forEach(x -> activeWitnesses.put(x, false));
                                break;

                            case "witDetail":
                            case "note":
                                skip = true;

                            // default: mark some text annotation node

                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        if(inText && !skip && !reader.isWhiteSpace()) {
                            // Split the character stream into whitespace-separate words
                            String[] words = reader.getText().split("\\s");
                            // Make the new chain of reading nodes
                            HashSet<Node> allPriors = new HashSet<>();
                            readingWitnesses.forEach(x -> allPriors.add(priorNode.get(x)));

                            // Make the chain of readings we need
                            ArrayList<Node> chain = new ArrayList<>();
                            for (String word : words) {
                                Node wordNode = db.createNode(Nodes.READING);
                                wordNode.setProperty("text", word);
                                wordNode.setProperty("tradition_id", tradId);
                                wordNode.setProperty("rank", 0);
                                if (!chain.isEmpty()) {
                                    Node lastNode = chain.get(chain.size() - 1);
                                    Relationship seq = lastNode.createRelationshipTo(wordNode, ERelations.SEQUENCE);
                                    seq.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                }
                                chain.add(wordNode);
                            }

                            // Attach the chain to the relevant prior node
                            if (chain.size() == 0)
                                System.out.println("debug");
                            else {
                                Node firstNode = chain.get(0);
                                HashSet<Relationship> priorLinks = new HashSet<>();
                                allPriors.forEach(n -> {
                                    Relationship seq = n.createRelationshipTo(firstNode, ERelations.SEQUENCE);
                                    priorLinks.add(seq);
                                });
                                final String wc = witClass;
                                readingWitnesses.forEach(w -> priorLinks.forEach(l -> {
                                    if (l.getStartNode().equals(priorNode.get(w)))
                                        addWitness(l, w, wc);
                                }));
                            }

                            // Set the new prior node for these witnesses
                            readingWitnesses.forEach(w -> priorNode.put(w, chain.get(chain.size() - 1)));
                        }
                        break;
                }
            } // end parseloop

            // Now calculate the whole tradition.
            tx.success();
        } catch (Exception e) {
            System.out.println(String.format("Error encountered in XML line %d column %d: ",
                    ((XMLStreamReaderImpl) reader).getLineNumber(),
                    ((XMLStreamReaderImpl) reader).getColumnNumber()));
            e.printStackTrace();
            return Response.serverError().build();
        }
        // Now try re-ranking the nodes. TODO figure out why we can't run this inside a transaction
        Boolean didCalc = new Tradition(tradId).recalculateRank(startNode.getId());
        if (!didCalc)
            return Response.serverError().entity("Could not calculate ranks on new graph").build();

        return Response.status(Response.Status.CREATED).entity("{\"tradId\":\"" + tradId + "\"}").build();
    }

    private ArrayList<String> parseWitnesses (String witString) {
        ArrayList<String> wits = new ArrayList<>();
        for (String w : witString.split("\\s")) {
            wits.add(w.substring(1));
        }
        return wits;
    }

    private void addWitness (Relationship r, String sigil, String witClass) {
        try (Transaction tx = db.beginTx()) {
            if(r.hasProperty(witClass)) {
                String[] witList = (String[]) r.getProperty(witClass);
                ArrayList<String> currentWits = new ArrayList<>(Arrays.asList(witList));
                currentWits.add(sigil);
                r.setProperty(witClass, currentWits.toArray(new String[currentWits.size()]));
            } else {
                String[] witList = {sigil};
                r.setProperty(witClass, witList);
            }
            tx.success();
        }
    }

    private Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

}