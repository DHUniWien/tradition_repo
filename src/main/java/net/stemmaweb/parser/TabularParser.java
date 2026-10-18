package net.stemmaweb.parser;

import static net.stemmaweb.Util.jsonerror;
import static net.stemmaweb.Util.jsonresp;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.RelationService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import jakarta.ws.rs.core.Response;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.VariantGraphService;

/**
 * Reads a variety of tabular formats (TSV, CSV, XLS, XLSX) and parses the data
 * into a tradition.
 */
public class TabularParser {
    private final Transaction tx;

    public TabularParser(Transaction tx) {
        this.tx = tx;
    }

    /**
     * Parse a comma- or tab-separated file stream into a graph.
     *
     * @param fileData - an InputStream containing the CSV/TSV data
     * @param sectionNode - the section of the tradition to which this collation belongs
     * @param sepChar - the record separator to use (either comma or tab)
     *
     * @return Response
     */
    public Response parseCSV(InputStream fileData, Node sectionNode, char sepChar) {
        // Parse the CSV file
        ArrayList<String[]> csvRows = new ArrayList<>();
        final CSVParser parser = new CSVParserBuilder().withSeparator(sepChar).build();
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(fileData)).withCSVParser(parser).build()) {
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null)
                csvRows.add(nextLine);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(jsonerror(e.getMessage())).build();
        }
        return parseTableToCollation(csvRows, sectionNode);
    }

    /**
     * Parse an Excel file stream into a graph.
     *
     * @param fileData - an InputStream containing the CSV/TSV data
     * @param sectionNode - the section of the tradition to which this collation belongs
     * @param excelType - either 'xls' or 'xlsx'
     *
     * @return Response
     */
    public Response parseExcel(InputStream fileData, Node sectionNode, String excelType) {
        ArrayList<String[]> excelRows;
        try {
            Workbook workbook;
            if (excelType.equals("xls")) {
                workbook = new HSSFWorkbook(fileData);
            } else { // it must be xlsx
                workbook = new XSSFWorkbook(fileData);
            }
            excelRows = getTableFromWorkbook(workbook);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(jsonerror(e.getMessage())).build();
        }
        return parseTableToCollation(excelRows, sectionNode);
    }

    // Extract a table from the first sheet of an Excel workbook.
    private ArrayList<String[]> getTableFromWorkbook (Workbook workbook) throws Exception {
        ArrayList<String[]> excelRows = new ArrayList<>();
        Sheet worksheet = workbook.getSheetAt(0);
        Iterator<Row> rowIterator = worksheet.rowIterator();
        int expectedSize = 0;
        while (rowIterator.hasNext()) {
            ArrayList<String> rowArray = new ArrayList<>();
            Row row = rowIterator.next();

            // Note the size that we expect our rows to be
            if (expectedSize == 0 )
                expectedSize = row.getPhysicalNumberOfCells();
            else if (row.getPhysicalNumberOfCells() > expectedSize)
                throw new Exception(String.format("Spreadsheet row %d has too many columns!", row.getRowNum()));

            for (int i = 0; i < expectedSize; i++)
                rowArray.add(row.getCell(i) == null ? null : row.getCell(i).getStringCellValue());
            excelRows.add(rowArray.toArray(new String[expectedSize]));
        }
        return excelRows;
    }

    private Response parseTableToCollation(ArrayList<String[]> tableData, Node parentNode) {
        String response;
        Response.Status result = Response.Status.OK;

        try {
        	Node traditionNode = VariantGraphService.getTraditionNode(tx, parentNode);
            // Make the start node
            Node startNode = Util.createStartNode(tx, parentNode);
            Node endNode = Util.createEndNode(tx, parentNode);
            endNode.setProperty("rank", (long) tableData.size());

            // Make the COLLATED relation type if it doesn't exist already
            try {
                RelationService.makeDefaultType(tx, traditionNode, "collated");
            } catch (Exception e) {
                return Response.serverError().entity(jsonerror("Error creating default relation type: " + e.getMessage())).build();
            }

            // Get the witnesses from the first row of the table
            String[] witnessList = tableData.getFirst();
            // Strip the byte order mark, if it exists, from the first of the witnesses
            witnessList[0] = witnessList[0].replaceFirst("\\uFEFF", "");
            // Keep a table of the last-spotted reading for each witness
            HashMap<String, Node> lastReading = new HashMap<>();
            // Sort the columns into plain witnesses and layers, of the form XX (YY):
            // - Any layer needs a base witness present.
            // - XX(YY) without the space is also understood as a layer, and spaces inside the parentheses
            //   don't matter, so only one of XX (YY), XX(YY) and XX ( YY ) can appear in a file.
            // - Any non-space after the closing ) will cause an error. Nested parens are right out.
            HashSet<String> baseSigla = new HashSet<>();
            // column heading of each layer -> [base sigil, layer label]
            HashMap<String, String[]> layerWitnesses = new HashMap<>();
            // [base sigil, layer label] -> column heading, to notice two headings for the same layer
            HashMap<List<String>, String> layerHeadings = new HashMap<>();
            for (String columnKey: witnessList) {
                String[] layer = parseLayerHeading(columnKey);
                if (layer == null) // it is not a layered witness
                    baseSigla.add(columnKey);
                else {
                    String other = layerHeadings.putIfAbsent(List.of(layer), columnKey);
                    if (other != null)
                        return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(String.format(
                                "The columns \"%s\" and \"%s\" are both layer \"%s\" of witness \"%s\". "
                                        + "Only one of them can be in the table.", other, columnKey, layer[1], layer[0]))).build();
                    layerWitnesses.put(columnKey, layer);
                }
            }
            for (String layered : layerWitnesses.keySet()) {
                String base = layerWitnesses.get(layered)[0];
                if (!baseSigla.contains(base))
                    return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(String.format(
                            "Layer witness \"%s\" has no base witness \"%s\" in the table. Add a column for \"%s\".",
                            layered, base, base))).build();
            }

            // Add the base witnesses to the graph, and start every column at the start node
            for (String sigil: witnessList) {
                if (!layerWitnesses.containsKey(sigil))
                    Util.findOrCreateExtant(tx, traditionNode, sigil);
                lastReading.put(sigil, startNode);
            }

            // Go through the remaining rows and create the readings
            int distinct = 0;
            for (int idx = 1; idx < tableData.size(); idx++) { // for each row
                String[] collationRow = tableData.get(idx);
                HashMap<String, Node> createdReadings = new HashMap<>();
                HashMap<Relationship, ArrayList<String>> linkWitnesses = new HashMap<>();
                for (int j = 0; j < collationRow.length; j++) {
                    String reading = collationRow[j];
                    String columnKey = witnessList[j];
                    Node lastNode = lastReading.get(columnKey);
                    // Is it an empty reading?
                    if (reading == null || reading.isEmpty()) {
                        // Empty readings count against any other reading here being common
                        distinct++;
                        continue;
                    }
                    // Is it a continuation of a lacuna?
                    if (reading.equals("#LACUNA#"))
                        if (lastNode.hasProperty("is_lacuna"))
                            continue;

                    // Does the reading exist?
                    Node readingNode = createdReadings.get(reading);
                    if (readingNode == null) {
                        readingNode = DatabaseService.createNode(tx, Nodes.READING);
                        readingNode.setProperty("section_id", parentNode.getProperty("id").toString());
                        readingNode.setProperty("rank", (long) idx);
                        readingNode.setProperty("text", reading);
                        if (reading.equals("#LACUNA#"))
                            readingNode.setProperty("is_lacuna", true);
                        else {
                            // Lacunae don't count for detecting a common reading
                            distinct++;
                        }
                        createdReadings.put(reading, readingNode);
                    }
                    // Does the reading have a relationship with lastNode? If not, create it.
                    Relationship existingSeq = Util.getSequenceIfExists(lastNode, readingNode);
                    if (existingSeq == null)
                        existingSeq = lastNode.createRelationshipTo(readingNode, ERelations.SEQUENCE);

                    // Get that relationship's witnesses list, or create it if it doesn't exist.
                    ArrayList<String> seqWitnesses = linkWitnesses.computeIfAbsent(
                            existingSeq, k -> new ArrayList<>());
                    // Add this sigil to the list and store the reading as its last
                    seqWitnesses.add(columnKey);
                    lastReading.put(columnKey, readingNode);
                }
                // Did we create only one reading (and no gaps?) If so it is a common reading.
                boolean common = distinct == 1;
                createdReadings.values().forEach(x -> x.setProperty("is_common", common));
                // Now that we have been through the row, create the witness / layer attributes
                // for the created relationships.
                for (Relationship r : linkWitnesses.keySet()) {
                    setWitnessProperties(r, linkWitnesses.get(r), layerWitnesses);
                }
                // Create a COLLATED link between all non-meta readings created at the same rank, to preserve
                // the collation as it was uploaded.
                List<ReadingModel> collatedReadings = createdReadings.values().stream().map(ReadingModel::new)
                        .filter(x -> !x.isMeta()).toList();
                int i = collatedReadings.size();
                RelationModel rm = new RelationModel();
                rm.setType("collated");
                rm.setAnnotation("Aligned in tabular input");
                while (i > 1) {
                    ReadingModel srdg = collatedReadings.get(i-1);
                    ReadingModel trdg = collatedReadings.get(i-2);
                    // Make sure the readings aren't the same and aren't linked yet
                    if (srdg.equals(trdg))
                        throw new Exception("Same reading twice in createdReadings?!");
                    rm.setSource(srdg.getId());
                    rm.setTarget(trdg.getId());
                    try {
                        RelationService.createLocalRelation(tx, traditionNode.getProperty("id").toString(), rm);
                    } catch (Exception e) {
                        throw new Exception("Problem collating aligned readings: " + e.getMessage());
                    }
                    i--;
                }
            }

            // Tie all the last readings to the end node.
            for (Node readingNode : lastReading.values()) {
                Relationship endRelation = Util.getSequenceIfExists(readingNode, endNode);
                if (endRelation == null) {
                    endRelation = readingNode.createRelationshipTo(endNode, ERelations.SEQUENCE);
                    ArrayList<String> readingWits = new ArrayList<>();
                    lastReading.keySet().forEach(x -> {
                        if (lastReading.get(x).equals(readingNode))
                            readingWits.add(x);
                    });
                    setWitnessProperties(endRelation, readingWits, layerWitnesses);
                } // else we've already connected this reading.
            }

            // We are done!
            result = Response.Status.CREATED;
            response = jsonresp("parentId", parentNode.getProperty("id").toString());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(jsonerror(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            if (result.equals(Response.Status.OK))
                result = Response.Status.INTERNAL_SERVER_ERROR;
            response = jsonerror(e.getMessage());
        }

        return Response.status(result).entity(response).build();

    }

    /**
     * Parses a column heading that denotes a layer of a witness, i.e. a base sigil followed by a single
     * parenthesised layer label, like "XX (YY)". Whitespace around the sigil and the label is not
     * significant, nor is whitespace between the sigil and the opening parenthesis.
     *
     * @param heading - a column heading from the table
     * @return null if the heading is a plain sigil, otherwise [base sigil, layer label]
     * @throws IllegalArgumentException if the heading has parentheses but is not a well-formed layer
     */
    private static String[] parseLayerHeading(String heading) throws IllegalArgumentException {
        int open = heading.indexOf('(');
        int close = heading.indexOf(')');
        if (open < 0 && close < 0)
            return null;
        String base = open < 0 ? "" : heading.substring(0, open).strip();
        String label = open < 0 || close < open ? "" : heading.substring(open + 1, close).strip();
        if (base.isEmpty() || label.isEmpty() || label.indexOf('(') >= 0 || !heading.substring(close + 1).isBlank())
            throw new IllegalArgumentException(String.format("Malformed layer sigil \"%s\": expected the form "
                    + "\"sigil (layer)\", with a single layer label and nothing after the closing parenthesis.", heading));
        return new String[]{base, label};
    }

    /**
     * Sets the witness properties on a sequence link, given the column headings of the witnesses (and
     * layers) that travel along it. The main witnesses go in the "witnesses" property, and the base
     * witnesses of any layers go in a property named for the layer label. A layer whose base witness
     * is already travelling along the link adds nothing.
     */
    private static void setWitnessProperties(Relationship r, List<String> witList,
                                             HashMap<String, String[]> layerWitnesses) {
        HashMap<String, ArrayList<String>> layerMap = new HashMap<>();
        layerMap.put("witnesses", new ArrayList<>());
        for (String w : witList)
            if (layerWitnesses.containsKey(w)) {
                // It's a layer witness. Get the layer label and the base witness
                String baseWit = layerWitnesses.get(w)[0];
                String layerLabel = layerWitnesses.get(w)[1];
                // See if the base witness is already in the list
                if (witList.contains(baseWit))
                    continue;
                // Add the layer label key and the witness.
                if (!layerMap.containsKey(layerLabel))
                    layerMap.put(layerLabel, new ArrayList<>());
                layerMap.get(layerLabel).add(baseWit);
            } else layerMap.get("witnesses").add(w);
        // Finally, set the properties for each layer label
        layerMap.forEach((x, y) -> r.setProperty(x, y.toArray(new String[0])));
    }
}
