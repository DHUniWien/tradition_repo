package net.stemmaweb.parser;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.Util.jsonresp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.ReadingService;
import net.stemmaweb.services.VariantGraphService;

public class CollateXJsonParser {

    private static final String LACUNA = "#LACUNA#";
    private final Transaction tx;

    public CollateXJsonParser(Transaction tx) {
        this.tx = tx;
    }

    /**
     * Parse a CollateX JSON input stream and attach it to the given (section) parentNode. If
     * the input has multiple sections (and allowSections is true), they are all created and
     * linked in order behind parentNode.
     *
     * @param filestream - The data to parse
     * @param parentNode - The section node that will carry the (first) parsed section
     * @param allowSections - Whether a multi-section wrapper is acceptable here; if it is not, it is rejected
     * @return a Response to indicate the result
     */
    public Response parseCollateXJson(InputStream filestream, Node parentNode, boolean allowSections) {
        JSONObject collation;
        try {
            collation = new JSONObject(IOUtils.toString(filestream, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(jsonerror("Bad CollateX JSON: " + e.getMessage())).build();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }
        if (!collation.has("sections"))
            return parseCollateXJson(collation, parentNode);
        if (!allowSections)
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(
                    "A multi-section CollateX JSON file cannot be added as a single section; "
                            + "upload it as a new tradition")).build();
        return parseSections(collation, parentNode);
    }

    /**
     * Handle a multi-section wrapper of the form {"name": ..., "sections": [ CollateX JSON objects ]}.
     *
     * @param wrapper - The wrapper object
     * @param firstSection - The (already created) section node that will carry the first section
     * @return a Response to indicate the result, naming the ID of the first section on success
     */
    public Response parseSections(JSONObject wrapper, Node firstSection) {
        JSONArray jSections = wrapper.optJSONArray("sections");
        if (jSections == null || jSections.isEmpty())
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(
                    "Bad CollateX JSON: 'sections' must be a non-empty array")).build();
        for (int i = 0; i < jSections.length(); i++)
            if (!(jSections.get(i) instanceof JSONObject))
                return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(
                        String.format("Bad CollateX JSON: section %d is not an object", i + 1))).build();

        // Get the tradition's name if it hasn't been set yet
        Node traditionNode = VariantGraphService.getTraditionNode(tx, firstSection);
        String tradName = traditionNode.getProperty("name", "").toString();
        if (tradName.isBlank() && !wrapper.optString("name", "").isEmpty()) {
            tradName = wrapper.getString("name");
            traditionNode.setProperty("name", tradName);
        }

        for (int i = 0; i < jSections.length(); i++) {
            JSONObject jSection = jSections.getJSONObject(i);
            String name = jSection.optString("name", "");
            Node sectionNode;
            if (i == 0) {
                sectionNode = firstSection;
                if (!name.isBlank())
                    sectionNode.setProperty("name", name);
                else
                    sectionNode.setProperty("name", "uploaded section 1");
            } else {
                sectionNode = DatabaseService.createNode(tx, Nodes.SECTION);
                if (name.isBlank())
                    name = "uploaded section " + (i + 1);
                sectionNode.setProperty("name", name);
                Util.ensureSectionLink(tx, traditionNode, sectionNode);
            }
            // Check that it isn't an empty section before we send it off for parsing
            JSONArray jWit = jSection.getJSONArray("witnesses");
            JSONArray jAlign = jSection.getJSONArray("table");
            if (jWit != null && jWit.isEmpty() && jAlign != null && jAlign.isEmpty())
                continue;

            try (Response result = parseCollateXJson(jSection, sectionNode)) {
                if (result.getStatus() != Response.Status.CREATED.getStatusCode()) {
                    // Keep the status of the failure, but say which section it was
                    String msg;
                    try {
                        msg = new JSONObject(result.getEntity().toString()).getString("error");
                    } catch (JSONException | NullPointerException e) {
                        msg = String.valueOf(result.getEntity());
                    }
                    return Response.status(result.getStatus())
                            .entity(jsonerror(String.format("section %d (%s): %s", i + 1,
                                    sectionNode.getProperty("name", name), msg))).build();
                }
            } catch (Exception e) {
                // Something unexpected and un-caught happened
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(jsonerror(String.format("Error parsing CollateX JSON section %d",i + 1))).build();
            }

        }
        return Response.status(Response.Status.CREATED)
                .entity(jsonresp("parentId", firstSection.getProperty("id").toString())).build();
    }

    /**
     * Parse an already-read CollateX JSON object and attach it to the given (section) parentNode.
     *
     * @param collation - The CollateX JSON output object
     * @param parentNode - The section node that will carry the parsed data
     * @return a Response to indicate the result
     */
    public Response parseCollateXJson(JSONObject collation, Node parentNode) {
        // parse the JSON
        ArrayList<String> collationWitnesses = new ArrayList<>();
        ArrayList<ArrayList<ReadingModel>> collationTable = new ArrayList<>();
        String collationName = "__DEFAULT__";

        // JSON parsing block; turn CollateX JSON into our model classes.
        // Needs its own try/catch for JSON exceptions
        try {
            // see if there is a section name here
            if (collation.has("name")) {
                collationName = collation.getString("name");
            }
            // get the witness list from the clunky JSON interface
            JSONArray jWit = collation.getJSONArray("witnesses");
            // Is the list of witnesses a list of sigla, or a list of id/token objects? If the latter,
            // tell the user they are trying to use CollateX input.
            if (jWit.isEmpty())
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("Bad CollateX JSON: the witness list is empty")).build();
            Object firstWit = jWit.get(0);
            if (firstWit instanceof JSONObject)
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(jsonerror("Bad format: is this CollateX JSON input instead of output?")).build();
            for (int i = 0; i < jWit.length(); i++) collationWitnesses.add(jWit.getString(i));

            // get the table data from the clunky JSON interface
            JSONArray jAlign = collation.getJSONArray("table");
            for (int i = 0; i < jAlign.length(); i++) {
                ArrayList<ReadingModel> row = new ArrayList<>();
                JSONArray jrow = jAlign.getJSONArray(i);
                if (jrow.length() != collationWitnesses.size())
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(jsonerror(String.format(
                                    "Bad CollateX JSON: table row %d has %d cells but there are %d witnesses",
                                    i, jrow.length(), collationWitnesses.size()))).build();
                for (int j = 0; j < jrow.length(); j++) {
                    String rtext = "";
                    String rnormal = "";
                    String rdisplay = "";
                    boolean joinPrior = false;
                    Boolean joinNext = false;
                    // A null cell is an empty cell, just like []
                    JSONArray jcell = jrow.isNull(j) ? new JSONArray() : jrow.getJSONArray(j);
                    JSONArray rownames = new JSONArray();
                    JSONArray rowsource = new JSONArray();
                    for (int k = 0; k < jcell.length(); k++) {
                        JSONObject jtoken = jcell.getJSONObject(k);
                        if (k == 0) {
                            joinPrior = jtoken.has("join_prior") && jtoken.getBoolean("join_prior");
                            jtoken.remove("join_prior");
                        }
                        // Patch together reading attributes from the CollateX token object:
                        // Reading text
                        String thisToken = jtoken.getString("t");
                        rtext = readingAppend(rtext, jtoken, "t", joinNext);
                        // Normal form
                        if (jtoken.has("normal_form"))
                            rnormal = readingAppend(rnormal, jtoken, "normal_form", joinNext);
                        else if (jtoken.has("n"))
                            rnormal = readingAppend(rnormal, jtoken, "n", joinNext);
                        else
                            rnormal = readingAppend(rnormal, jtoken, "t", joinNext);
                        if (jtoken.has("display"))
                            rdisplay = readingAppend(rdisplay, jtoken, "display", joinNext);
                        else
                            rdisplay = readingAppend(rdisplay, jtoken, "t", joinNext);
                        jtoken.remove("t");
                        jtoken.remove("normal_form");
                        jtoken.remove("n");
                        jtoken.remove("display");
                        // Join_next attribute; the last value will prevail
                        joinNext = jtoken.has("join_next") && jtoken.getBoolean("join_next");
                        jtoken.remove("join_next");
                        // Save the remaining token contents as a string in the annotation field, for future reference
                        if (!jtoken.isEmpty()) {
                            rownames.put(thisToken);
                            rowsource.put(jtoken);
                        }
                    }
                    ReadingModel rdg = new ReadingModel();
                    // These might all be blank
                    rdg.setText(rtext);
                    rdg.setNormal_form(rnormal);
                    // Only set the display value if it differs from the token itself
                    if (!rdisplay.equals(rtext))
                        rdg.setDisplay(rdisplay);
                    rdg.setJoin_next(joinNext);
                    rdg.setJoin_prior(joinPrior);
                    if (rowsource.length() > 1)
                        rdg.setExtra(rowsource.toJSONObject(rownames).toString());
                    else if (rowsource.length() == 1)
                        rdg.setExtra(rowsource.getJSONObject(0).toString());
                    row.add(rdg);
                }
                collationTable.add(row);
            }
        } catch (JSONException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(jsonerror("Bad CollateX JSON: " + e.getMessage())).build();
        }

        // Now we have the data in our own model classes; proceed.
        try {
        	Node traditionNode = VariantGraphService.getTraditionNode(tx, parentNode);
            // Set the section name if we found one and it isn't already set
            if (!collationName.equals("__DEFAULT__")
                    && parentNode.getProperty("name", "__DEFAULT__").equals("__DEFAULT__")) {
                parentNode.setProperty("name", collationName);
            }
            // Check that we have all the witnesses
            for (String witString : collationWitnesses) {
                List<String> wit = parseWitnessSigil(witString);
                String sigil = wit.getFirst();
                Util.findOrCreateExtant(tx, traditionNode, sigil);
            }

            // Create the start node for the section
            Node startNode = Util.createStartNode(tx, parentNode);
            HashMap<String, Node> lastWitnessReading = new HashMap<>();
            collationWitnesses.forEach(x -> lastWitnessReading.put(x, startNode));

            // Now create all the readings from our models
            long rank = 1L;
            for (ArrayList<ReadingModel> row : collationTable) {
                HashMap<String, Node> createdReadings = new HashMap<>();
                int distinct = 0;
                for (int w = 0; w < row.size(); w++) {
                    ReadingModel rm = row.get(w);
                    String thisWitness = collationWitnesses.get(w);
                    List<String> witParts = parseWitnessSigil(thisWitness);
                    String lookupKey = String.join(rm.getText(), rm.getNormal_form(), rm.getDisplay(),
                            rm.getJoin_next().toString(), rm.getJoin_prior().toString());
                    if (lookupKey.equals("nullfalsefalse")) {
                        distinct++;
                        continue;  // Don't add blank readings
                    }
                    boolean isLacuna = rm.getText().equals(LACUNA);
                    // A run of lacuna cells in one witness column is a single lacuna reading
                    if (isLacuna && lastWitnessReading.get(thisWitness).hasProperty("is_lacuna"))
                        continue;
                    Node thisReading;
                    if (createdReadings.containsKey(lookupKey)) {
                        thisReading = createdReadings.get(lookupKey);
                        // Only tokens that carry extra data contribute to the extra field
                        if (rm.getExtra() != null)
                            thisReading.setProperty("extra",
                                    expandExtraField(thisReading.getProperty("extra", "{}").toString(),
                                            witParts, rm.getExtra()));
                    } else {
                        thisReading = DatabaseService.createNode(tx, Nodes.READING);
                        thisReading.setProperty("text", rm.getText());
                        thisReading.setProperty("normal_form", rm.getNormal_form());
                        if (rm.getDisplay() != null)
                            thisReading.setProperty("display", rm.getDisplay());
                        thisReading.setProperty("join_prior", rm.getJoin_prior());
                        thisReading.setProperty("join_next", rm.getJoin_next());
                        if (rm.getAnnotation() != null)
                            thisReading.setProperty("annotation", rm.getAnnotation());
                        if (rm.getExtra() != null) {
                            // Wrap the reading's "extra" value in a hash value keyed on the witness.
                            JSONObject thisExtra = new JSONObject();
                            thisExtra.put(thisWitness, new JSONObject(rm.getExtra()));
                            thisReading.setProperty("extra", thisExtra.toString());
                        }
                        thisReading.setProperty("rank", rank);
                        if (isLacuna)
                            thisReading.setProperty("is_lacuna", true);
                        thisReading.setProperty("section_id", parentNode.getProperty("id").toString());
                        createdReadings.put(lookupKey, thisReading);
                        // Lacunae don't count for detecting a common reading
                        if (!isLacuna)
                            distinct++;
                    }
                    Node lastReading = lastWitnessReading.get(thisWitness);
                    ReadingService.addWitnessLink(lastReading, thisReading, witParts.get(0), witParts.get(1));
                    lastWitnessReading.put(thisWitness, thisReading);
                }
                if (!createdReadings.isEmpty()) {
                    // Increment the rank
                    rank++;
                    // Set commonality attribute on all readings created
                    boolean common = distinct == 1;
                    createdReadings.values().forEach(x -> x.setProperty("is_common", common));
                }
            }

            Node endNode = Util.createEndNode(tx, parentNode);
            endNode.setProperty("rank", rank);
            for (String witString : collationWitnesses) {
                List<String> witParts = parseWitnessSigil(witString);
                Node lastReading = lastWitnessReading.get(witString);
                ReadingService.addWitnessLink(lastReading, endNode, witParts.get(0), witParts.get(1));
            }
            return Response.status(Response.Status.CREATED).entity(jsonresp("parentId", parentNode.getProperty("id").toString())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(jsonerror(e.getMessage())).build();
        }

    }

    private static String expandExtraField (String origJson, List<String> witness, String newJSON) throws JSONException {
        JSONObject currvalue = new JSONObject(origJson);
        JSONObject newValue = new JSONObject(newJSON);
        // Check to see whether we are adding a redundant line
        String sigil;
        if (!witness.get(1).equals("witnesses")) {
            // We are in a witness layer
            if (currvalue.has(witness.get(0))
                    && currvalue.getJSONObject(witness.get(0)).toString().equals(newValue.toString()))
                return currvalue.toString();
            sigil = String.format("%s (%s)", witness.get(0), witness.get(1));
        } else { // ...we are assuming for our own sanity that layers are declared after main witnesses.
            sigil = witness.getFirst();
        }
        currvalue.put(sigil, newValue);
        return currvalue.toString();
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
        } else {
            parts.add(sigil);
            parts.add("witnesses");
        }
        return parts;
    }

    private static String readingAppend (String current, JSONObject token, String key, Boolean joinNext)
            throws JSONException {
        StringBuilder prior = new StringBuilder(current);
        boolean noSpace = prior.isEmpty() || joinNext;
        if (token.has("join_prior") && token.getBoolean("join_prior"))
            noSpace = true;
        if (!noSpace)
            prior.append(" ");
        prior.append(token.get(key));
        return prior.toString();
    }

}
