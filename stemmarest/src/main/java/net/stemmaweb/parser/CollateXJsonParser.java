package net.stemmaweb.parser;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.apache.cxf.helpers.IOUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.*;

public class CollateXJsonParser {

    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseCollateXJson(InputStream filestream, Node parentNode) {
        // parse the JSON
        ArrayList<String> collationWitnesses = new ArrayList<>();
        ArrayList<ArrayList<ReadingModel>> collationTable = new ArrayList<>();

        // JSON parsing block; it needs its own try/catch for JSON exceptions
        try {
            JSONObject table = new JSONObject(IOUtils.toString(filestream, "utf-8"));
            // get the witness list from the clunky JSON interface
            JSONArray jWit = table.getJSONArray("witnesses");
            for (int i = 0; i < jWit.length(); i++) collationWitnesses.add(jWit.getString(i));

            // get the table data from the clunky JSON interface
            JSONArray jAlign = table.getJSONArray("table");
            for (int i = 0; i < jAlign.length(); i++) {
                ArrayList<ReadingModel> row = new ArrayList<>();
                JSONArray jrow = jAlign.getJSONArray(i);
                for (int j = 0; j < jrow.length(); j++) {
                    String rtext = "";
                    String rnormal = "";
                    Boolean joinPrior = false;
                    Boolean joinNext = false;
                    JSONArray jcell = jrow.getJSONArray(j);
                    for (int k = 0; k < jcell.length(); k++) {
                        JSONObject jtoken = jcell.getJSONObject(k);
                        if (k == 0)
                            joinPrior = jtoken.has("join_prior") && jtoken.getBoolean("join_prior");
                        // Patch together reading attributes from the CollateX token object
                        rtext = readingAppend(rtext, jtoken, "t", joinNext);
                        if (jtoken.has("c"))
                            rnormal = readingAppend(rnormal, jtoken, "c", joinNext);
                        else
                            rnormal = readingAppend(rnormal, jtoken, "t", joinNext);
                        joinNext = jtoken.has("join_next") && jtoken.getBoolean("join_next");
                        // LATER get anything else we need for the ReadingModel from this
                    }
                    ReadingModel rdg = new ReadingModel();
                    // These might all be blank
                    rdg.setText(rtext);
                    rdg.setNormal_form(rnormal);
                    rdg.setJoin_next(joinNext);
                    rdg.setJoin_prior(joinPrior);
                    row.add(rdg);
                }
                collationTable.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }

        // Now we have the data in good old Java classes; proceed.
        Node traditionNode = DatabaseService.getTraditionNode(parentNode, db);
        List<Node> tradWitnesses = DatabaseService.getRelated(traditionNode, ERelations.HAS_WITNESS);
        try (Transaction tx = db.beginTx()) {
            // Check that we have all the witnesses
            for (String witString : collationWitnesses) {
                List<String> wit = parseWitnessSigil(witString);
                String sigil = wit.get(0);
                if (tradWitnesses.stream().noneMatch(x -> x.getProperty("sigil").equals(sigil))) {
                    Util.createWitness(traditionNode, sigil, false);
                }
            }

            // Create the start node for the section
            Node startNode = Util.createStartNode(parentNode);
            HashMap<String, Node> lastWitnessReading = new HashMap<>();
            collationWitnesses.forEach(x -> lastWitnessReading.put(x, startNode));

            // Now create all the readings from our models
            long rank = 1L;
            for (ArrayList<ReadingModel> row : collationTable) {
                HashMap<String, Node> createdReadings = new HashMap<>();
                for (int w = 0; w < row.size(); w++) {
                    ReadingModel rm = row.get(w);
                    String thisWitness = collationWitnesses.get(w);
                    List<String> witParts = parseWitnessSigil(thisWitness);
                    String lookupKey = String.join(rm.getText(), rm.getNormal_form(),
                            rm.getJoin_next().toString(), rm.getJoin_prior().toString());
                    Node thisReading;
                    if (createdReadings.containsKey(lookupKey))
                        thisReading = createdReadings.get(lookupKey);
                    else {
                        thisReading = db.createNode(Nodes.READING);
                        thisReading.setProperty("text", rm.getText());
                        thisReading.setProperty("normal_form", rm.getNormal_form());
                        thisReading.setProperty("join_prior", rm.getJoin_prior());
                        thisReading.setProperty("join_next", rm.getJoin_next());
                        thisReading.setProperty("rank", rank);
                        createdReadings.put(lookupKey, thisReading);
                    }
                    Node lastReading = lastWitnessReading.get(thisWitness);
                    Relationship seq = Util.getSequenceIfExists(lastReading, thisReading);
                    if (seq == null)
                        seq = lastReading.createRelationshipTo(thisReading, ERelations.SEQUENCE);
                    addWitnessToRelationship(seq, witParts);
                    lastWitnessReading.put(thisWitness, thisReading);
                }
                if (createdReadings.size() > 0)
                    rank++;
            }

            Node endNode = Util.createEndNode(parentNode);
            endNode.setProperty("rank", rank);
            for (String witString : collationWitnesses) {
                List<String> witParts = parseWitnessSigil(witString);
                Node lastReading = lastWitnessReading.get(witString);
                Relationship seq = Util.getSequenceIfExists(lastReading, endNode);
                if (seq == null)
                    seq = lastReading.createRelationshipTo(endNode, ERelations.SEQUENCE);
                addWitnessToRelationship(seq, witParts);
            }
            tx.success();
            String response = String.format("{\"parentId\":\"%d\"}", parentNode.getId());
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            e.printStackTrace();
            String error = String.format("{\"error\": \"%s\"}", e.getMessage());
            return Response.serverError().entity(error).build();
        }

    }

    private static void addWitnessToRelationship (Relationship seq, List<String> witParts) {
        String propertyName = witParts.size() == 1 ? "witnesses" : witParts.get(1);
        String sigil = witParts.get(0);
        if (seq.hasProperty(propertyName)) {
            ArrayList<String> existingWits = new ArrayList<>(Arrays.asList((String[]) seq.getProperty("witnesses")));
            if (!existingWits.contains(sigil)) {
                existingWits.add(sigil);
                seq.setProperty(propertyName, existingWits.toArray(new String[0]));
            }
        } else {
            String[] existingWits = new String[]{ sigil };
            seq.setProperty(propertyName, existingWits);
        }
    }

    private static List<String> parseWitnessSigil (String sigil) {
        List<String> parts = new ArrayList<>();
        if (sigil.contains("(")) {
            int startExtra = sigil.lastIndexOf('(');
            int endExtra = sigil.lastIndexOf(')');
            String base = sigil.substring(0, startExtra);
            String extra = sigil.substring(startExtra + 1, endExtra);
            base = base.replaceAll("\\s+$", "");
            parts.add(base);
            parts.add(extra);
        } else
            parts.add(sigil);
        return parts;
    }

    private static String readingAppend (String current, JSONObject token, String key, Boolean joinNext)
            throws JSONException {
        StringBuilder prior = new StringBuilder(current);
        Boolean noSpace = prior.length() == 0 || joinNext;
        if (token.has("join_prior") && token.getBoolean("join_prior"))
            noSpace = true;
        if (!noSpace)
            prior.append(" ");
        prior.append(token.get(key));
        return prior.toString();
    }

}
