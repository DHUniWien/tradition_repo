package net.stemmaweb.parser;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.*;
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

import static net.stemmaweb.services.ReadingService.addWitnessLink;
import static net.stemmaweb.services.ReadingService.removePlaceholder;

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
        String parentId;
        Node startNode;
        Node endNode = null;
        try (Transaction tx = db.beginTx()) {
            parentId = String.valueOf(parentNode.getId());
            tradId = traditionNode.getProperty("id").toString();
            // Set up the start node
            startNode = Util.createStartNode(parentNode, tradId);

            // State variables
            Boolean inHeader = false;
            Boolean inText = false;
            Boolean skip = false;
            // Keep track of the chain of nodes from a particular stream of text
            ArrayList<Node> chain;
            // Keep track of the witnesses represented in a particular reading
            Node documentPrior = startNode;
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
                                endNode = Util.createEndtNode(parentNode, tradId);
                                endNode.setProperty("rank", 0L);
                                Relationship endLink = documentPrior.createRelationshipTo(endNode, ERelations.SEQUENCE);
                                setAllWitnesses(endLink);
                                // Now go through and clean out all the placeholder nodes, linking the tradition.
                                for (Node n : placeholderNodes) removePlaceholder(n);
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
                                    Util.createExtant(traditionNode, sigil);
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
                                documentPrior = parseApp(reader, tradId, documentPrior, false);
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
                            // Make a reading chain of the text
                            chain = makeReadingChain(reader, tradId, readingWitnesses, "witnesses");
                            if (chain.size() != 0) {
                                // Add a placeholder to the end of the chain
                                Node chainEnd = createPlaceholderNode("chainEnd");
                                Relationship ceRel = chain.get(chain.size()-1)
                                        .createRelationshipTo(chainEnd, ERelations.SEQUENCE);
                                ceRel.setProperty("witnesses", readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                // Link inactive witnesses straight from placeholder to placeholder
                                ArrayList<String> inactiveWitnesses = activeWitnesses.keySet().stream()
                                        .filter(x -> !activeWitnesses.get(x)).collect(Collectors.toCollection(ArrayList::new));
                                for (String w : inactiveWitnesses)
                                    addWitnessLink(documentPrior, chainEnd, w, "witnesses");

                                // Link the beginning of the chain to the documentPrior
                                Relationship link = documentPrior.createRelationshipTo(chain.get(0), ERelations.SEQUENCE);
                                link.setProperty("witnesses", readingWitnesses.toArray(new String[readingWitnesses.size()]));

                                // The end of the chain is the new documentPrior
                                documentPrior = chainEnd;
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

        // Merge all mergeable readings, to get rid of duplicates across apparatus entries.
        Long endRank;
        try (Transaction tx = db.beginTx()) {
            endRank = Long.valueOf(endNode.getProperty("rank").toString());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
        Section s = new Section(tradId, parentId);
        for (List<ReadingModel> identSet : s.collectIdenticalReadings(0, endRank)) {
            ReadingModel first = identSet.remove(0);
            Reading rd = new Reading(first.getId());
            for (ReadingModel identical : identSet) {
                Response done = rd.mergeReadings(Long.valueOf(identical.getId()));
                if (done.getStatus() != Response.Status.OK.getStatusCode())
                    return Response.serverError().entity(done.getEntity()).build();
            }
        }

        return Response.status(Response.Status.CREATED)
                .entity(String.format("{\"parentId\":\"%s\"}", parentId)).build();
    }

    // Parse an app, its readings, and its sub-apps if necessary. Return the node that
    // is now the last reading in its chain.
    private Node parseApp(XMLStreamReader reader, String tradId, Node contextPrior, Boolean recursed) {

        // We are at the START_ELEMENT event for the <app> tag.
        // Create a bracket of placeholder nodes, which all readings in this app
        // will connect to and all witnesses will traverse.
        String appId = reader.getAttributeValue(reader.getNamespaceURI("xml"), "id");
        Node appStart = createPlaceholderNode("START_" + appId);
        Node appEnd = createPlaceholderNode("END_" + appId);

        // Set up our state variables
        Boolean skip = false;
        ArrayList<String> readingWitnesses = new ArrayList<>();
        Node readingEnd = null;
        // Keep track of the last node from either an app or a common reading
        // Keep track of the chain of nodes from a particular stream of text
        ArrayList<Node> chain = new ArrayList<>();
        // Keep track of the witness class on a particular reading
        String witClass = "witnesses";

        try (Transaction tx = db.beginTx()) {
            parseloop:
            while (true) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.END_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "app":
                                // Connect the prior node with the app start. Do this here instead of outside the loop
                                // so that inactiveWitnesses is populated if applicable.
                                Relationship r = contextPrior.createRelationshipTo(appStart, ERelations.SEQUENCE);
                                if (!recursed) {
                                    // Connect all witnesses from main app to main app, even if they are missing
                                    setAllWitnesses(r);
                                    // Connect the app start to the app end via all *inactive* witnesses
                                    ArrayList<String> inactiveWitnesses = activeWitnesses.keySet().stream()
                                            .filter(x -> !activeWitnesses.get(x)).collect(Collectors.toCollection(ArrayList::new));
                                    for (String w : inactiveWitnesses)
                                        addWitnessLink(appStart, appEnd, w, "witnesses");
                                } else {
                                    // Connect only the current active witnesses from the enclosing app to this one
                                    ArrayList<String> recursedActive = activeWitnesses.keySet().stream()
                                            .filter(activeWitnesses::get).collect(Collectors.toCollection(ArrayList::new));
                                    r.setProperty("witnesses", recursedActive.toArray(new String[recursedActive.size()]));
                                    // and app start-to-end should be entirely via the readings.
                                }

                                // Check that all active-for-this-app witnesses have a "normal" path through the app;
                                // this is to catch witnesses that appear only via special witness classes, or
                                // <witStart/> / <witEnd/> apps.
                                HashSet<String> hasWitnesses = new HashSet<>();
                                Iterable<Relationship> outgoing = appStart.getRelationships(
                                        ERelations.SEQUENCE, Direction.OUTGOING);
                                // Note the witness links that already exist in this app
                                for (Relationship rel : outgoing)
                                    if (rel.hasProperty("witnesses"))
                                        Collections.addAll(hasWitnesses, (String[]) rel.getProperty("witnesses"));
                                // Add any active wits that are missing in this app
                                activeWitnesses.keySet().stream().filter(activeWitnesses::get)
                                        .filter(x -> !hasWitnesses.contains(x))
                                        .forEach(x -> addWitnessLink(appStart, appEnd, x, "witnesses"));

                                // Promote our new end node and get out of here.
                                contextPrior = appEnd;
                                break parseloop;

                            case "rdg":
                            case "lem":
                                // Hook up the end of the reading to the end of the app
                                Relationship el = readingEnd.createRelationshipTo(appEnd, ERelations.SEQUENCE);
                                el.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                // Clear some state variables
                                readingEnd = null;
                                readingWitnesses.clear();
                                chain.clear();
                                witClass = "witnesses";
                                break;

                        }
                        break;

                    case XMLStreamConstants.START_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "app":
                                // Make the current reading witnesses the only active ones
                                ArrayList<String> savedActive = new ArrayList<>();
                                activeWitnesses.keySet().stream().filter(activeWitnesses::get).forEach(savedActive::add);
                                activeWitnesses.keySet().forEach(x -> activeWitnesses.put(x, false));
                                readingWitnesses.forEach(x -> activeWitnesses.put(x, true));
                                // Send the app for recursive parsing and attach its endpoint to ours
                                readingEnd = parseApp(reader, tradId, readingEnd, true);
                                // Now restore the active witnesses
                                savedActive.forEach(x -> activeWitnesses.put(x, true));
                                break;

                            case "rdg":
                            case "lem":
                                readingEnd = createPlaceholderNode("RDGSTART_" + appId);
                                readingWitnesses = parseWitnesses(reader.getAttributeValue("", "wit"));
                                String variantClass = reader.getAttributeValue("", "type");
                                if (variantClass != null)
                                    witClass = variantClass;
                                Relationship link = appStart.createRelationshipTo(readingEnd, ERelations.SEQUENCE);
                                link.setProperty(witClass, readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                break;

                            case "witStart":
                                if (recursed)
                                    throw new Exception("Cannot have witStart / witEnd in recursed apparatus");
                                readingWitnesses.forEach(x -> activeWitnesses.put(x, true));
                                if (!appSiglorumPresent)
                                    // then we also have to deactivate the false ones explicitly.
                                    for (String w : activeWitnesses.keySet())
                                        if (!readingWitnesses.contains(w))
                                            activeWitnesses.put(w, false);
                                appSiglorumPresent = true;
                                break;
                            case "witEnd":
                                if (recursed)
                                    throw new Exception("Cannot have witStart / witEnd in recursed apparatus");
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
                            if (chain.size() > 0) {
                                // Attach the chain to the reading start; error if there is no reading start
                                Relationship link = readingEnd.createRelationshipTo(chain.get(0), ERelations.SEQUENCE);
                                link.setProperty("witnesses", readingWitnesses.toArray(new String[readingWitnesses.size()]));
                                // Set the reading end to be the end of the chain
                                readingEnd = chain.get(chain.size()-1);
                            }

                        }
                        break;
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contextPrior;
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

    private Node createPlaceholderNode (String name) {
        Node ph = db.createNode(Nodes.READING);
        ph.setProperty("is_placeholder", true);
        if (name != null) ph.setProperty("text", name);
        placeholderNodes.add(ph);
        return ph;
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

}