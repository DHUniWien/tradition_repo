package net.stemmaweb.exporter;

import com.opencsv.CSVWriter;
import net.stemmaweb.model.AlignmentModel;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A class for writing a graph out to various forms of table: JSON, CSV, Excel, etc.
 */
public class TabularExporter {

    private GraphDatabaseService db;
    public TabularExporter(GraphDatabaseService db){
        this.db = db;
    }

    public Response exportAsJSON(String tradId, List<String> conflate) {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if(traditionNode==null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Get the model.
        AlignmentModel asJson = new AlignmentModel(traditionNode, conflate);
        return Response.ok(asJson, MediaType.APPLICATION_JSON_TYPE).build();
    }

    public Response exportAsJSON(String tradId) {
        return exportAsJSON(tradId, new ArrayList<>());
    }

    public Response exportAsCSV(String tradId, char separator, List<String> conflate) {
        Node traditionNode = DatabaseService.getTraditionNode(tradId, db);
        if(traditionNode==null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Get the model.
        AlignmentModel asJson = new AlignmentModel(traditionNode, conflate);

        // Write the CSV to a string that we can return.
        StringWriter sw = new StringWriter();
        CSVWriter writer = new CSVWriter(sw, separator);
        // First the witnesses...
        ArrayList<String> witnesses = new ArrayList<>();
        for (HashMap<String, Object> witRecord : asJson.getAlignment())
            witnesses.add(witRecord.get("witness").toString());
        writer.writeNext(witnesses.toArray(new String[witnesses.size()]));
        // ...and then each token row.
        for (int i = 0; i < asJson.getLength(); i++) {
            ArrayList<String> readings = new ArrayList<>();
            for (HashMap<String, Object> witRecord : asJson.getAlignment()) {
                ArrayList<HashMap<String, String>> tokenList = (ArrayList<HashMap<String, String>>) witRecord.get("tokens");
                readings.add(tokenList.get(i).get("t"));
            }
            writer.writeNext(readings.toArray(new String[readings.size()]));
        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().entity(e.getMessage()).build();
        }
        return Response.ok(sw.toString(), MediaType.TEXT_PLAIN_TYPE).build();
    }

    public Response exportAsCSV(String tradId, char separator) {
        return exportAsCSV(tradId, separator, new ArrayList<>());
    }

}
