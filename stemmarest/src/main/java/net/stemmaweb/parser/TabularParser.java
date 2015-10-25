package net.stemmaweb.parser;

import com.opencsv.CSVReader;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
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
     * @param userId - the ID of the owner
     * @param tradName - the name to be assigned to the tradition
     * @param sepChar - the record separator to use (either comma or tab)
     *
     * @return Response
     */
    public Response parseCSV(InputStream fileData, String userId, String tradName, char sepChar) {
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
        return parseTableToTradition(csvRows, userId, tradName);
    }

    /**
     * Parse an Excel file stream into a graph.
     *
     * @param fileData - an InputStream containing the CSV/TSV data
     * @param userId - the ID of the owner
     * @param tradName - the name to be assigned to the tradition
     * @param excelType - either 'xls' or 'xlsx'
     *
     * @return Response
     */
    public Response parseExcel(InputStream fileData, String userId, String tradName, String excelType) {
        ArrayList<String[]> excelRows;
        try {
            if (excelType.equals("xls")) {
                HSSFWorkbook workbook = new HSSFWorkbook(fileData);
                excelRows = getTableFromWorkbook(workbook);
            } else { // it must be xlsx
                XSSFWorkbook workbook = new XSSFWorkbook(fileData);
                excelRows = getTableFromWorkbook(workbook);
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(String.format("{\"error\":\"%s\"", e.getMessage())).build();
        }
        return parseTableToTradition(excelRows, userId, tradName);
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

    private Response parseTableToTradition(ArrayList<String[]> tableData, String userId, String tradName) {
        String response;
        Response.Status result = Response.Status.OK;
        Node traditionNode;
        String tradId = UUID.randomUUID().toString();
        try (Transaction tx = db.beginTx()) {
            // Make the tradition node and tie it to the user
            traditionNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
            traditionNode.setProperty("id", tradId);
            traditionNode.setProperty("name", tradName);
            Node userNode = db.findNode(Nodes.USER, "id", userId);
            if (userNode == null) {
                result = Response.Status.PRECONDITION_FAILED;
                throw new Exception("No user with ID " + userId + " found!");
            }
            userNode.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
            // Make the start node
            Node startNode = db.createNode(Nodes.READING);
            startNode.setProperty("is_start", true);
            startNode.setProperty("rank", 0L);
            startNode.setProperty("text", "#START#");
            traditionNode.createRelationshipTo(startNode, ERelations.COLLATION);
            Node endNode = db.createNode(Nodes.READING);
            endNode.setProperty("is_end", true);
            endNode.setProperty("rank", tableData.size());
            endNode.setProperty("text", "#END#");
            traditionNode.createRelationshipTo(endNode, ERelations.HAS_END);

            // Get the witnesses from the first row of the table
            String[] witnessList = tableData.get(0);
            // Keep a table of the last-spotted reading for each witness
            HashMap<String, Node> lastReading = new HashMap<>();
            // Add the witnesses to the graph
            for (String sigil: witnessList) {
                Node witnessNode = db.createNode(Nodes.WITNESS);
                witnessNode.setProperty("sigil", sigil);
                witnessNode.setProperty("hypothetical", false);
                witnessNode.setProperty("quotesigil", !isDotId(sigil));
                traditionNode.createRelationshipTo(witnessNode, ERelations.HAS_WITNESS);
                lastReading.put(sigil, startNode);
            }

            // Go through the remaining rows and create the readings
            for (int idx = 1; idx < tableData.size(); idx++) {
                String[] collationRow = tableData.get(idx);
                HashMap<String, Node> createdReadings = new HashMap<>();
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
                        readingNode.setProperty("rank", idx);
                        readingNode.setProperty("text", reading);
                        if (reading.equals("#LACUNA#"))
                            readingNode.setProperty("is_lacuna", true);
                        createdReadings.put(reading, readingNode);
                    }
                    // Does the reading have a relationship with lastNode? If not, create it.
                    Relationship existingSeq = getSequenceIfExists(lastNode, readingNode);
                    if (existingSeq == null) {
                        existingSeq = lastNode.createRelationshipTo(readingNode, ERelations.SEQUENCE);
                    }

                    // Add this witness to that relationship's witnesses list.
                    ArrayList<String> sequenceWitnesses = new ArrayList<>();
                    if (existingSeq.hasProperty("witnesses")) {
                        String[] priorWitnesses = (String[]) existingSeq.getProperty("witnesses");
                        sequenceWitnesses.addAll(Arrays.asList(priorWitnesses));
                    }
                    sequenceWitnesses.add(sigil);
                    existingSeq.setProperty("witnesses", sequenceWitnesses.toArray(new String[sequenceWitnesses.size()]));
                    lastReading.put(sigil, readingNode);
                }
            }

            // Tie all the last readings to the end node.
            for (Node readingNode : lastReading.values()) {
                Relationship endRelation = getSequenceIfExists(readingNode, endNode);
                if (endRelation == null) {
                    endRelation = readingNode.createRelationshipTo(endNode, ERelations.SEQUENCE);
                    ArrayList<String> readingWits = new ArrayList<>();
                    lastReading.keySet().stream().forEach(x -> {
                        if (lastReading.get(x).equals(readingNode))
                            readingWits.add(x);
                    });
                    endRelation.setProperty("witnesses", readingWits.toArray(new String[readingWits.size()]));
                } // else we've already connected this reading.
            }

            // We are done!
            result = Response.Status.CREATED;
            response = String.format("{\"tradId\":\"%s\"}", tradId);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            if (result.equals(Response.Status.OK))
                result = Response.Status.INTERNAL_SERVER_ERROR;
            response = String.format("{\"error\":\"%s\"}", e.getMessage());
        }

        return Response.status(result).entity(response).build();

    }

    private Relationship getSequenceIfExists (Node source, Node target) {
        Relationship found = null;
        try (Transaction tx = db.beginTx()) {
            Iterable<Relationship> allseq = source.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE);
            for (Relationship r : allseq) {
                if (r.getEndNode().equals(target)) {
                    found = r;
                    break;
                }
            }
            tx.success();
        }
        return found;
    }

    // TODO refactor this to a general parser helper class
    private Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }
}
