package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;

import javax.ws.rs.core.Response;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parse a TEI parallel-segmentation file into a tradition graph.
 */
public class TEIParallelSegParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    // Global variables needed for the parsing
    // Keep track of which witnesses are "active" at any given time
    private HashMap<String, Boolean> activeWitnesses = new HashMap<>();
    // Keep a list of the placeholder nodes that are made in this process
    private ArrayList<Node> placeholderNodes = new ArrayList<>();
    // Note whether the witStart / witEnd tags are being used
    private Boolean appSiglorumPresent = false;
    private Boolean spaceSignificant = false;

    public Response parseTEIParallelSeg(InputStream xmldata, Node parentNode) {
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
        Node traditionNode = DatabaseService.getRelated(parentNode, ERelations.PART).get(0);
        String tradId;
        Node startNode;
        try (Transaction tx = db.beginTx()) {
            tradId = traditionNode.getProperty("id").toString();
            // Set up the start node
            startNode = db.createNode(Nodes.READING);
            startNode.setProperty("is_start", true);
            startNode.setProperty("tradition_id", tradId);
            startNode.setProperty("text", "#START#");
            startNode.setProperty("rank", 0);
            parentNode.createRelationshipTo(startNode, ERelations.COLLATION);

            // State variables
            Boolean inHeader = false;
            Boolean inText = false;
            Boolean skip = false;
            // Keep track of the chain of nodes from a particular stream of text
            ArrayList<Node> chain;
            // Keep track of the witnesses represented in a particular reading
            Node globalPrior = startNode;
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

                            case "text":
                                // End of the text; add the end node.
                                Node endNode = db.createNode(Nodes.READING);
                                endNode.setProperty("text", "#END#");
                                endNode.setProperty("tradition_id", tradId);
                                endNode.setProperty("is_end", true);
                                endNode.setProperty("rank", 0);
                                parentNode.createRelationshipTo(endNode, ERelations.HAS_END);
                                Relationship endLink = globalPrior.createRelationshipTo(endNode, ERelations.SEQUENCE);
                                setAllWitnesses(endLink);
                                // Now go through and clean out all the placeholder nodes, linking the tradition.
                                clearPlaceholders();
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
                                    parentNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
                                    // All witnesses start active by default; if we encounter a witStart
                                    // we will start to use an explicit app siglorum.
                                    activeWitnesses.put(sigil, true);
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
                                    String spacePolicy = reader.getAttributeValue(reader.getNamespaceURI("xml"), "space");
                                    if (spacePolicy != null && spacePolicy.equals("preserve"))
                                        spaceSignificant = true;
                                }
                                break;

                            case "app":
                                globalPrior = parseApp(reader, tradId, globalPrior, false);
                                break;

                            case "note":
                                skip = true;

                            // default: mark some text annotation node

                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        // This is text outside an apparatus, and applies to all active witnesses
                        if(inText && !skip && !reader.isWhiteSpace()) {
                            ArrayList<String> readingWitnesses = activeWitnesses.keySet().stream()
                                    .filter(activeWitnesses::get)
                                    .collect(Collectors.toCollection(ArrayList::new));
                            chain = makeReadingChain(reader, tradId, readingWitnesses, "witnesses");
                            // Attach the chain to the relevant prior node
                            if (chain.size() != 0) {
                                Relationship link = globalPrior.createRelationshipTo(chain.get(0), ERelations.SEQUENCE);
                                link.setProperty("witnesses", readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                globalPrior = chain.get(chain.size()-1);
                            }
                        }
                        break;
                }
            } // end parseloop

            // Now calculate the whole tradition.
            tx.success();
        } catch (Exception e) {
            System.out.println(String.format("Error encountered in XML line %d column %d: ",
                    reader.getLocation().getLineNumber(),
                    reader.getLocation().getColumnNumber()));
            e.printStackTrace();
            return Response.serverError().build();
        }
        // Now try re-ranking the nodes.
        Boolean didCalc = new Tradition(tradId).recalculateRank(startNode.getId());
        if (!didCalc)
            return Response.serverError().entity("Could not calculate ranks on new graph").build();

        return Response.status(Response.Status.CREATED)
                .entity(String.format("{\"parentId\":\"%d\"}", parentNode.getId())).build();
    }

    // Parse an app, its readings, and its sub-apps if necessary. Return the node that
    // is now the last reading in its chain.
    private Node parseApp(XMLStreamReader reader, String tradId, Node globalPrior, Boolean recursed) {

        // We are at the START_ELEMENT event for the <app> tag.
        // Create a bracket of placeholder nodes, which all readings in this app
        // will connect to and all witnesses will traverse.
        Node appStart = db.createNode(Nodes.READING);
        appStart.setProperty("is_placeholder", true);
        placeholderNodes.add(appStart);
        Node appEnd = db.createNode(Nodes.READING);
        appEnd.setProperty("is_placeholder", true);
        placeholderNodes.add(appEnd);
        // Connect the app start with whatever was the previous (either global or app placeholder) node
        Relationship r = globalPrior.createRelationshipTo(appStart, ERelations.SEQUENCE);
        setAllWitnesses(r);
        // Connect the app end to the app start via all *inactive* witnesses
        if (!recursed) {
            ArrayList<String> inactiveWitnesses = activeWitnesses.keySet().stream()
                    .filter(x -> !activeWitnesses.get(x)).collect(Collectors.toCollection(ArrayList::new));
            for (String w : inactiveWitnesses) {
                addWitnessLink(appStart, appEnd, w, "witnesses");
            }
        }

        // Set up our state variables
        Boolean skip = false;
        ArrayList<String> readingWitnesses = new ArrayList<>();
        // Keep track of the last node from either an app or a common reading
        // Keep track of the chain of nodes from a particular stream of text
        ArrayList<Node> chain = new ArrayList<>();
        // Keep track of the witness class on a particular reading
        String witClass = "witnesses";

        parseloop:
        while (true) {
            try (Transaction tx = db.beginTx()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.END_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "app":
                                readingWitnesses = new ArrayList<>();
                                activeWitnesses.keySet().stream()
                                        .filter(activeWitnesses::get)
                                        .forEach(readingWitnesses::add);
                                globalPrior = appEnd;
                                appEnd = null;
                                appStart = null;
                                break parseloop;

                            case "rdg":
                            case "lem":
                                // Hook up the chain to the existing apparatus nodes
                                if (chain.size() > 0) {
                                    Node firstNode = chain.get(0);
                                    Node lastNode = chain.get(chain.size()-1);
                                    Relationship sl = appStart.createRelationshipTo(firstNode, ERelations.SEQUENCE);
                                    Relationship el = lastNode.createRelationshipTo(appEnd, ERelations.SEQUENCE);
                                    // Set the witness(es) on the links
                                    sl.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                    el.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                } else {
                                    Relationship l = appStart.createRelationshipTo(appEnd, ERelations.SEQUENCE);
                                    l.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                }

                                readingWitnesses = new ArrayList<>();
                                witClass = "witnesses";
                                break;

                        }
                        break;

                    case XMLStreamConstants.START_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "app":
                                globalPrior = parseApp(reader, tradId, globalPrior, true);
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
                                if (!appSiglorumPresent)
                                    // then we also have to deactivate the false ones explicitly.
                                    for (String w : activeWitnesses.keySet())
                                        if (!readingWitnesses.contains(w))
                                            activeWitnesses.put(w, false);
                                appSiglorumPresent = true;
                                break;
                            case "witEnd":
                                // If we see witEnd before witStart, then all witnesses were implicitly
                                // started and we don't need to deactivate any extras.
                                appSiglorumPresent = true;
                                readingWitnesses.forEach(x -> activeWitnesses.put(x, false));
                                break;

                            case "witDetail":
                                skip = true;
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        if(!skip && !reader.isWhiteSpace()) {
                            chain = makeReadingChain(reader, tradId, readingWitnesses, witClass);
                            // Attach the chain to the relevant prior node
                            if (chain.size() == 0)
                                System.out.println("debug zero-length chain");
                        }
                        break;
                }
                tx.success();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return globalPrior;
    }

    private ArrayList<Node> makeReadingChain(XMLStreamReader reader, String tradId,
                                             ArrayList<String> readingWitnesses, String witClass) {
        // Split the character stream into whitespace-separate words
        String[] words = reader.getText().split("\\s");

        // If the first word is all punctuation, set join_prior on it
        Boolean join_prior = words[0].matches("^\\p{Punct}+$");

        // Make the chain of readings with the remaining words
        ArrayList<Node> chain = new ArrayList<>();
        for (String word : words) {
            if (word.matches("^\\s*$"))
                continue;
            Node wordNode = db.createNode(Nodes.READING);
            wordNode.setProperty("text", word);
            wordNode.setProperty("tradition_id", tradId);
            wordNode.setProperty("rank", 0);
            if (join_prior) {
                wordNode.setProperty("join_prior", true);
                join_prior = false;
            }
            if (!chain.isEmpty()) {
                Node lastNode = chain.get(chain.size() - 1);
                Relationship seq = lastNode.createRelationshipTo(wordNode, ERelations.SEQUENCE);
                seq.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
            }
            chain.add(wordNode);
        }
        // Set join_prior / join_next on the first & last readings
        // if there was a significant lack of whitespace
        if (spaceSignificant)
            if (!reader.getText().matches("\\s+$"))
                chain.get(chain.size()-1).setProperty("join_next", true);
            else if (join_prior || !reader.getText().matches("^\\s+"))
                chain.get(0).setProperty("join_prior", true);
        return chain;
    }

    private void clearPlaceholders () {
        try (Transaction tx = db.beginTx()) {

            for (Node n : placeholderNodes) {
                // Find the links into and out of this placeholder
                Iterable<Relationship> priorRels = n.getRelationships(ERelations.SEQUENCE, Direction.INCOMING);
                Iterable<Relationship> nextRels = n.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);
                // Hashmap of first-node-to-witness and witness-to-last-node
                // node -> class -> witness
                HashMap<Node, HashMap<String, ArrayList<String>>> priorNodes = new HashMap<>();
                for (Relationship r : priorRels) {
                    Node pNode = r.getStartNode();
                    HashMap<String, ArrayList<String>> currMap = priorNodes.containsKey(pNode)
                            ? priorNodes.get(pNode) : new HashMap<>();
                    for (String key : r.getPropertyKeys()) {
                        String[] wits = (String[]) r.getProperty(key);
                        ArrayList<String> classWits = currMap.containsKey(key)
                                ? currMap.get(key) : new ArrayList<>();
                        Collections.addAll(classWits, wits);
                        currMap.put(key, classWits);
                    }
                    priorNodes.put(pNode, currMap);
                }
                // class -> witness -> node
                HashMap<String, HashMap<String,Node>> nextWits = new HashMap<>();
                for (Relationship r : nextRels)
                    for (String key : r.getPropertyKeys()) {
                        HashMap<String, Node> currMap = nextWits.containsKey(key)
                                ? nextWits.get(key) : new HashMap<>();
                        String[] keyWits = (String[]) r.getProperty(key);
                        for (String wit : keyWits)
                            currMap.put(wit, r.getEndNode());
                        nextWits.put(key, currMap);
                    }

                // Now delete the relationships to this node and the node itself.
                priorRels.forEach(Relationship::delete);
                nextRels.forEach(Relationship::delete);
                n.delete();

                // and re-create the relationships based on our hashmaps.
                for (Node pNode : priorNodes.keySet()) {
                    // priorMap = class -> witness for this node
                    HashMap<String, ArrayList<String>> priorMap = priorNodes.get(pNode);
                    for (String key : priorMap.keySet()) {
                        // nextMap = witness -> node for the
                        HashMap<String, Node> nextMap = nextWits.get(key);
                        // else the nonexistent witness will be picked up
// next time there is a reading for it. In theory.
                        priorMap.get(key).stream().filter(nextMap::containsKey).forEach(wit -> {
                            Node eNode = nextWits.get(key).containsKey(wit)
                                    ? nextWits.get(key).get(wit)
                                    : nextWits.get("witnesses").get(wit);
                            addWitnessLink(pNode, eNode, wit, key);
                        }); // else the nonexistent witness will be picked up
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> parseWitnesses (String witString) {
        ArrayList<String> wits = new ArrayList<>();
        for (String w : witString.split("\\s")) {
            wits.add(w.substring(1));
        }
        return wits;
    }

    private void setAllWitnesses(Relationship r) {
        String[] activewits = activeWitnesses.keySet().toArray(new String[activeWitnesses.size()]);
        r.setProperty("witnesses", activewits);
    }

    private void addWitnessLink (Node start, Node end, String sigil, String witClass) {
        try (Transaction tx = db.beginTx()) {
            Relationship link = null;
            for (Relationship r : start.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE))
                if (r.getEndNode().equals(end))
                    link = r;
            if (link == null) {
                link = start.createRelationshipTo(end, ERelations.SEQUENCE);
            }
            if(link.hasProperty(witClass)) {
                String[] witList = (String[]) link.getProperty(witClass);
                ArrayList<String> currentWits = new ArrayList<>(Arrays.asList(witList));
                currentWits.add(sigil);
                link.setProperty(witClass, currentWits.toArray(new String[currentWits.size()]));
            } else {
                String[] witList = {sigil};
                link.setProperty(witClass, witList);
            }
            tx.success();
        }
    }

    private Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

}