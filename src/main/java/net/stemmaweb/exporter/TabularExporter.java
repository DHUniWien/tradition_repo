package net.stemmaweb.exporter;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.WitnessTokensModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.VariantGraphService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A class for writing a graph out to various forms of table: JSON, CSV, Excel, etc.
 */
public class TabularExporter {

    private final GraphDatabaseService db;
    public TabularExporter(GraphDatabaseService db){
        this.db = db;
    }

    public Response exportAsJSON(String tradId, String conflate, List<String> sectionList, boolean excludeLayers) {
        ArrayList<Node> traditionSections;
        try {
            traditionSections = getSections(tradId, sectionList);
            if(traditionSections==null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            return Response.ok(getTraditionAlignment(traditionSections, conflate, excludeLayers),
                    MediaType.APPLICATION_JSON_TYPE).build();
        } catch (TabularExporterException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }


    public Response exportAsCSV(String tradId, char separator, String conflate, List<String> sectionList,
                                boolean excludeLayers) {
        AlignmentModel wholeTradition;
        try {
            wholeTradition = returnFullAlignment(tradId, conflate, sectionList, excludeLayers);
        } catch (TabularExporterException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
        if (wholeTradition == null) return Response.status(Response.Status.NOT_FOUND).build();

        // Got this far? Turn it into CSV.
        // The CSV will go into a string that we can return.
        StringWriter sw = new StringWriter();
        ICSVWriter writer = new CSVWriterBuilder(sw)
                .withSeparator(separator)
                .withQuoteChar(separator == ',' ? ICSVWriter.DEFAULT_QUOTE_CHARACTER : ICSVWriter.NO_QUOTE_CHARACTER)
                .build();

        // First write out the witness list
        writer.writeNext(wholeTradition.getAlignment().stream()
                .map(WitnessTokensModel::constructSigil).toArray(String[]::new));

        // Now write out the normal_form or text for the reading in each "row"
        for (int i = 0; i < wholeTradition.getLength(); i++) {
            AtomicInteger ai = new AtomicInteger(i);
            writer.writeNext(wholeTradition.getAlignment().stream()
                    .map(x -> {
                        ReadingModel rm = x.getTokens().get(ai.get());
                        return rm == null ? null : rm.normalized();
                    }).toArray(String[]::new));
        }

        // Close off the CSV writer and return
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return Response.ok(sw.toString(), MediaType.TEXT_PLAIN_TYPE).build();
    }


    public Response exportAsCharMatrix(String tradId, int maxVars, String conflate, List<String> sectionList,
                                       boolean excludeLayers) {
        AlignmentModel wholeTradition;
        try {
            wholeTradition = returnFullAlignment(tradId, conflate, sectionList, excludeLayers);
            if (wholeTradition==null) return Response.status(Response.Status.NOT_FOUND).build();
        } catch (TabularExporterException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }

        // We will count on the order of the witness columns remaining constant.
        List<String> witnessSigla = wholeTradition.getAlignment().stream()
                .map(WitnessTokensModel::constructSigil).collect(Collectors.toList());
        // Collect the character rows as they are built for each witness.
        HashMap<String, StringBuilder> witnessRows = new HashMap<>();
        for (String sigil : witnessSigla) witnessRows.put(sigil, new StringBuilder());
        // Go rank by rank through all the lists of tokens, converting them into chars
        int totalLength = 0;
        for (int i = 0; i < wholeTradition.getLength(); i++) {
            AtomicInteger ai = new AtomicInteger(i);
            List<ReadingModel> row = wholeTradition.getAlignment().stream()
                    .map(x -> x.getTokens().get(ai.get())).collect(Collectors.toList());
            // Make reading-to-character lookup
            HashMap<String, Character> charMap = new HashMap<>();
            char curr = 'A';
            boolean row_has_null = false;
            boolean row_has_lacuna = false;
            for (ReadingModel rm : row) {
                if (rm == null) {
                    row_has_null = true;
                    continue;
                } else if (rm.getIs_lacuna()) {
                    row_has_lacuna = true;
                    continue;
                }
                if (!charMap.containsKey(rm.getId())) {
                    charMap.put(rm.getId(), curr);
                    curr++;
                }
            }
            // Skip rows that don't diverge
            if (curr == 'B' && !row_has_null && !row_has_lacuna)
                continue;
            // Check that we aren't over the max-character limit
            if (curr > 'A' + maxVars || row_has_null && curr > 'A' + (maxVars - 1))
                continue;

            // Employ it
            totalLength++;
            for (int w = 0; w < witnessSigla.size(); w++) {
                StringBuilder ourRow = witnessRows.get(witnessSigla.get(w));
                ReadingModel ourReading = row.get(w);
                if (ourReading == null) {
                    ourRow.append('X');
                    curr++; // Count this in our maximum of eight characters
                }
                else if (ourReading.getIs_lacuna())
                    ourRow.append('?');
                else
                    ourRow.append(charMap.get(row.get(w).getId()));
            }
        }
        // Now let's build the whole matrix.
        StringBuilder charMatrix = new StringBuilder();
        charMatrix.append(String.format("\t%d\t%d\n", wholeTradition.getAlignment().size(), totalLength));
        for (String sigil : witnessSigla) {
            charMatrix.append(String.format("%-10s", shortenSigil(sigil)));
            charMatrix.append(witnessRows.get(sigil));
            charMatrix.append("\n");
        }

        return Response.ok(charMatrix.toString()).build();
    }

    private AlignmentModel returnFullAlignment(String tradId, String conflate, List<String> sectionList,
                                               boolean excludeLayers)
            throws Exception {
        ArrayList<Node> traditionSections = getSections(tradId, sectionList);
        if(traditionSections==null) return null;
        return getTraditionAlignment(traditionSections, conflate, excludeLayers);
    }

    private static String shortenSigil (String sigil) {
        String shortened = sigil.replaceAll("\\s+", "_")
                .replaceAll("\\W+", "");
        if (shortened.length() > 10)
            shortened = shortened.substring(0, 10);
        return shortened;
    }

    private ArrayList<Node> getSections(String tradId, List<String> sectionList)
    throws TabularExporterException {
        ArrayList<Node> traditionSections = VariantGraphService.getSectionNodes(tradId, db);
        // Does the tradition exist in the first place?
        if (traditionSections.isEmpty()) return null;

        // Are we requesting all sections?
        if (sectionList.size() == 0) return traditionSections;

        // Do the real work
        ArrayList<Node> collectedSections = new ArrayList<>();
        for (String sectionId : sectionList) {
            try (Transaction tx = db.beginTx()) {
                collectedSections.add(db.getNodeById(Long.parseLong(sectionId)));
                tx.success();
            } catch (NotFoundException e) {
                throw new TabularExporterException("Section " + sectionId + " not found in tradition");
            }
        }
        return collectedSections;
    }

    private AlignmentModel getTraditionAlignment(ArrayList<Node> traditionSections, String collapseRelated, boolean excludeLayers)
            throws Exception {
        // Make a new alignment model that has a column for every witness layer across the requested sections.

        // For each section, get the model. Keep track of which layers in which witnesses we have
        // seen with a set.
        HashSet<String> allWitnesses = new HashSet<>();
        ArrayList<AlignmentModel> tables = new ArrayList<>();
        int length = 0;
        for (Node sectionNode : traditionSections) {
            if (collapseRelated != null) VariantGraphService.normalizeGraph(sectionNode, collapseRelated);
            AlignmentModel asJson = new AlignmentModel(sectionNode, excludeLayers);
            if (collapseRelated != null) VariantGraphService.clearNormalization(sectionNode);
            // Save the alignment to our tables list
            tables.add(asJson);
            length += asJson.getLength();
            // Save the witness -> column mapping to our map
            for (WitnessTokensModel witRecord : asJson.getAlignment()) {
                allWitnesses.add(witRecord.constructSigil());
            }
        }

        // Now make an alignment model containing all witness layers present in allWitnesses, filling in
        // if necessary either nulls or the base witness per witness layer, per section.
        AlignmentModel wholeTradition = new AlignmentModel();
        List<String> sortedWits = new ArrayList<>(allWitnesses);
        Collections.sort(sortedWits);
        for (String sigil : sortedWits) {
            String[] parsed = WitnessTokensModel.parseSigil(sigil);

            // Set up the tradition-spanning witness token model for this witness
            WitnessTokensModel wholeWitness = new WitnessTokensModel();
            wholeWitness.setWitness(parsed[0]);
            if (parsed[1] != null) wholeWitness.setLayer(parsed[1]);
            wholeWitness.setTokens(new ArrayList<>());
            // Now fill in tokens from each section in turn.
            for (AlignmentModel aSection : tables) {
                // Find the WitnessTokensModel corresponding to wit, if it exists
                Optional<WitnessTokensModel> thisWitness = aSection.getAlignment().stream()
                        .filter(x -> x.constructSigil().equals(sigil)).findFirst();
                if (thisWitness.isEmpty()) {
                    // Try again for the base witness
                    thisWitness = aSection.getAlignment().stream()
                            .filter(x -> x.getWitness().equals(parsed[0]) && !x.hasLayer()).findFirst();
                }

                if (thisWitness.isPresent()) {
                    WitnessTokensModel witcolumn = thisWitness.get();
                    wholeWitness.getTokens().addAll(witcolumn.getTokens());
                    assert(witcolumn.getTokens().size() == aSection.getLength());
                } else {
                    // Add a bunch of nulls
                    wholeWitness.getTokens().addAll(new ArrayList<>(Collections.nCopies((int) aSection.getLength(), null)));
                }
            }
            // Add the WitnessTokensModel to the new AlignmentModel.
            wholeTradition.addWitness(wholeWitness);
        }
        // Record the length of the whole alignment
        wholeTradition.setLength(length);
        return wholeTradition;
    }

    private static class TabularExporterException extends Exception {
        TabularExporterException (String message) {
            super(message);
        }
    }

}
