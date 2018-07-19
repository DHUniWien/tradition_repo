package net.stemmaweb.exporter;

import com.opencsv.CSVWriter;
import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.model.AlignmentModel.WitnessTokensModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class for writing a graph out to various forms of table: JSON, CSV, Excel, etc.
 */
public class TabularExporter {

    private GraphDatabaseService db;
    public TabularExporter(GraphDatabaseService db){
        this.db = db;
    }

    public Response exportAsJSON(String tradId, List<String> conflate, String startSection, String endSection) {
        ArrayList<Node> traditionSections;
        try {
            traditionSections = getSections(tradId, startSection, endSection);
        } catch (TabularExporterException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        if(traditionSections==null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(getTraditionAlignment(traditionSections, conflate),
                MediaType.APPLICATION_JSON_TYPE).build();
    }


    public Response exportAsCSV(String tradId, char separator, List<String> conflate, String startSection, String endSection) {
        ArrayList<Node> traditionSections;
        try {
            traditionSections = getSections(tradId, startSection, endSection);
        } catch (TabularExporterException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        if(traditionSections==null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Get the alignment model from exportAsJSON, and then turn that into CSV.
        AlignmentModel wholeTradition = getTraditionAlignment(traditionSections, conflate);

        // Got this far? Turn it into CSV.
        // The CSV will go into a string that we can return.
        StringWriter sw = new StringWriter();
        CSVWriter writer = new CSVWriter(sw, separator);

        // First write out the witness list
        writer.writeNext(wholeTradition.getAlignment().stream()
                .map(WitnessTokensModel::getWitness).toArray(String[]::new));

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

    private ArrayList<Node> getSections(String tradId, String startSection, String endSection)
    throws TabularExporterException {
        ArrayList<Node> traditionSections = DatabaseService.getSectionNodes(tradId, db);
        // Does the tradition exist in the first place?
        if (traditionSections == null) return null;

        // Are we requesting all sections?
        if (startSection.equals("") && endSection.equals("")) return traditionSections;

        // Do the real work
        Boolean inPart = startSection.equals("");
        ArrayList<Node> collectedSections = new ArrayList<>();
        for (Node section : traditionSections) {
            if (String.valueOf(section.getId()).equals(startSection)) {
                inPart = true;
            }
            if (inPart) collectedSections.add(section);
            if (String.valueOf(section.getId()).equals(endSection)) {
                if (!inPart) throw new TabularExporterException("End section found before start section reached");
                inPart = false;
            }
        }
        if (collectedSections.size() == 0 && !startSection.equals(""))
            throw new TabularExporterException("Specified start section not found");
        if (inPart && !endSection.equals(""))
            throw new TabularExporterException("Specified end section not found");
        return collectedSections;
    }

    private AlignmentModel getTraditionAlignment(ArrayList<Node> traditionSections, List<String> conflate) {
        // Make a new alignment model that has a column for every witness layer across the requested sections.
        // For each section, get the model. Keep track of which witnesses correspond to
        // which columns in which section.
        HashMap<String, String> allWitnesses = new HashMap<>();
        ArrayList<AlignmentModel> tables = new ArrayList<>();
        for (Node sectionNode : traditionSections) {
            AlignmentModel asJson = new AlignmentModel(sectionNode, conflate);
            // Save the alignment to our tables list
            tables.add(asJson);
            // Save the witness -> column mapping to our map
            for (WitnessTokensModel witRecord : asJson.getAlignment()) {
                String wit = witRecord.getWitness();
                String base = witRecord.hasBase() ? witRecord.getBase() : witRecord.getWitness();
                allWitnesses.put(wit, base);
            }
        }

        // Now make an alignment model containing all witness layers in allWitnesses, filling in if necessary
        // either nulls or the base witness per witness layer, per section.
        AlignmentModel wholeTradition = new AlignmentModel();
        ArrayList<String> sortableWits = new ArrayList<>(allWitnesses.keySet());
        Collections.sort(sortableWits);
        for (String wit : sortableWits) {
            // Set up the tradition-spanning witness token model for this witness
            WitnessTokensModel wtm = wholeTradition.new WitnessTokensModel();
            wtm.setWitness(wit);
            wtm.setBase(allWitnesses.get(wit));
            wtm.setTokens(new ArrayList<>());
            // Now fill in tokens from each section in turn.
            for (AlignmentModel aSection : tables) {
                // Find the WitnessTokensModel corresponding to wit, if it exists
                Optional<WitnessTokensModel> thisWitness = aSection.getAlignment().stream()
                        .filter(x -> x.getWitness().equals(wit)).findFirst();
                if (!thisWitness.isPresent()) {
                    // Try again for the base witness
                    String base = allWitnesses.get(wit);
                    thisWitness = aSection.getAlignment().stream()
                            .filter(x -> x.getWitness().equals(base)).findFirst();
                }
                if (thisWitness.isPresent()) {
                    WitnessTokensModel witcolumn = thisWitness.get();
                    wtm.getTokens().addAll(witcolumn.getTokens());
                } else {
                    // Add a bunch of nulls
                    wtm.getTokens().addAll(new ArrayList<>(Collections.nCopies((int) aSection.getLength(), null)));
                }
            }
            // Add the WitnessTokensModel to the new AlignmentModel.
            wholeTradition.addWitness(wtm);
        }
        // Record the length of the whole alignment
        wholeTradition.setLength(wholeTradition.getAlignment().get(0).getTokens().size());
        return wholeTradition;
    }

    private static class TabularExporterException extends Exception {
        TabularExporterException (String message) {
            super(message);
        }
    }

}
