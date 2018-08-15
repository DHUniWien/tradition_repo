package net.stemmaweb.parser;

import com.opencsv.CSVReader;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.neo4j.graphdb.*;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Reads a variety of tabular formats (TSV, CSV, XLS, XLSX) and parses the data
 * into a tradition.
 */
public class TabularParser {
    private GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();

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
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(fileData), sepChar);
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null)
                csvRows.add(nextLine);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(String.format("{\"error\":\"%s\"", e.getMessage())).build();
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
                    .entity(String.format("{\"error\":\"%s\"", e.getMessage())).build();
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

            row.forEach(x -> rowArray.add(x.getStringCellValue()));
            excelRows.add(rowArray.toArray(new String[expectedSize]));
        }
        return excelRows;
    }

    private Response parseTableToCollation(ArrayList<String[]> tableData, Node parentNode) {
        String response;
        Response.Status result = Response.Status.OK;
        Node traditionNode = DatabaseService.getRelated(parentNode, ERelations.PART).get(0);

        try (Transaction tx = db.beginTx()) {
            // Make the start node
            Node startNode = Util.createStartNode(parentNode);
            Node endNode = Util.createEndNode(parentNode);
            endNode.setProperty("rank", (long) tableData.size());

            // Get the witnesses from the first row of the table
            String[] witnessList = tableData.get(0);
            // Keep a table of the last-spotted reading for each witness
            HashMap<String, Node> lastReading = new HashMap<>();
            // Add the non-layer witnesses to the graph
            HashMap<String, String[]> layerWitnesses = new HashMap<>();
            for (String sigil: witnessList) {
                // See if it is a layered witness, of the form XX (YY)
                String[] sigilParts = sigil.split("\\s+\\(");  // now we have ["XX", "YY)"]
                if (sigilParts.length == 1) // it is not a layered witness
                    Util.findOrCreateExtant(traditionNode, sigil);
                else if (sigilParts.length == 2) // it is a layered witness; store a ref to its base
                    layerWitnesses.put(sigil, sigilParts);
                else   // what is this i don't even
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Malformed sigil " + sigil).build();

                lastReading.put(sigil, startNode);
            }

            // Go through the remaining rows and create the readings
            for (int idx = 1; idx < tableData.size(); idx++) { // for each row
                String[] collationRow = tableData.get(idx);
                HashMap<String, Node> createdReadings = new HashMap<>();
                HashMap<Relationship, ArrayList<String>> linkWitnesses = new HashMap<>();
                for (int j = 0; j < collationRow.length; j++) {
                    String reading = collationRow[j];
                    String sigil = witnessList[j];
                    Node lastNode = lastReading.get(sigil);
                    // Is it an empty reading?
                    if (reading == null || reading.equals(""))
                        continue;
                    // Is it a continuation of a lacuna?
                    if (reading.equals("#LACUNA#"))
                        if (lastNode.hasProperty("is_lacuna"))
                            continue;

                    // Does the reading exist?
                    Node readingNode = createdReadings.getOrDefault(reading, null);
                    if (readingNode == null) {
                        readingNode = db.createNode(Nodes.READING);
                        readingNode.setProperty("section_id", parentNode.getId());
                        readingNode.setProperty("rank", (long) idx);
                        readingNode.setProperty("text", reading);
                        if (reading.equals("#LACUNA#"))
                            readingNode.setProperty("is_lacuna", true);
                        createdReadings.put(reading, readingNode);
                    }
                    // Does the reading have a relationship with lastNode? If not, create it.
                    Relationship existingSeq = Util.getSequenceIfExists(lastNode, readingNode);
                    if (existingSeq == null)
                        existingSeq = lastNode.createRelationshipTo(readingNode, ERelations.SEQUENCE);

                    // Get that relationship's witnesses list, or create it if it doesn't exist.
                    ArrayList<String> seqWitnesses = linkWitnesses.getOrDefault(existingSeq, null);
                    if (seqWitnesses == null) {
                        seqWitnesses = new ArrayList<>();
                        linkWitnesses.put(existingSeq, seqWitnesses);
                    }
                    // Add this sigil to the list and store the reading as its last
                    seqWitnesses.add(sigil);
                    lastReading.put(sigil, readingNode);
                }
                // Now that we have been through the row, create the witness / layer attributes
                // for the created relationships.
                for (Relationship r : linkWitnesses.keySet()) {
                    ArrayList<String> witList = linkWitnesses.get(r);
                    HashMap<String, ArrayList<String>> layerMap = new HashMap<>();
                    layerMap.put("witnesses", new ArrayList<>());
                    for (String w : witList)
                        if (layerWitnesses.containsKey(w)) {
                            // It's a layer witness. Get the layer label and the base witness
                            String baseWit = layerWitnesses.get(w)[0];
                            String ll = layerWitnesses.get(w)[1];
                            String layerLabel = ll.substring(0, ll.indexOf(')'));
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
                    endRelation.setProperty("witnesses", readingWits.toArray(new String[0]));
                } // else we've already connected this reading.
            }

            // We are done!
            result = Response.Status.CREATED;
            response = String.format("{\"parentId\":\"%d\"}", parentNode.getId());
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            if (result.equals(Response.Status.OK))
                result = Response.Status.INTERNAL_SERVER_ERROR;
            response = String.format("{\"error\":\"%s\"}", e.getMessage());
        }

        return Response.status(result).entity(response).build();

    }
}
