package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.parser.CollateXParser;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Test the CollateX parser
 *
 */
public class CollateXInputTest extends TestCase {

    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
    private CollateXParser importResource;

    public void setUp() throws Exception {
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseService.createRootNode(db);
        try(Transaction tx = db.beginTx())
        {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SEQUENCE);
            tx.success();
        }

        importResource = new CollateXParser();
        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    private ClientResponse createTraditionFromFile(String tName, String tDir, String userId,
                                                   String fName, String fType) throws FileNotFoundException {
        FormDataMultiPart form = new FormDataMultiPart();
        if (fType != null) form.field("filetype", fType);
        if (tName != null) form.field("name", tName);
        if (tDir != null) form.field("direction", tDir);
        if (userId != null) form.field("userId", userId);
        if (fName != null) {
            FormDataBodyPart fdp = new FormDataBodyPart("file",
                    new FileInputStream(fName),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            form.bodyPart(fdp);
        }
        return  jerseyTest.resource()
                .path("/tradition")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .put(ClientResponse.class, form);
    }

    public void testParseCollateX() throws Exception {
        ClientResponse cResult = null;
        try {
            String fileName = "src/TestFiles/plaetzchen_cx.xml";
            cResult = createTraditionFromFile("Auch hier", "LR", "1", fileName, "collatex");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "W2");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        assertEquals("Ich hab auch hier wieder ein Pläzchen", witnessText);

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(10, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Plätzchen"))
                foundReading = true;
        assertTrue(foundReading);
    }

    public void testParseCollateXJersey() throws Exception {
        ClientResponse cResult = null;
        try {
            String fileName = "src/TestFiles/plaetzchen_cx.xml";
            cResult = createTraditionFromFile("Auch hier", "LR", "1", fileName, "collatex");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());
        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        Response result = tradition.getTraditionInfo();
        TraditionModel tradInfo = (TraditionModel) result.getEntity();
        assertEquals("LR", tradInfo.getDirection());
        assertEquals("Auch hier", tradInfo.getName());

        result = tradition.getAllWitnesses();
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(3, allWitnesses.size());

        // Get a witness text
        Witness witness = new Witness(tradId, "W2");
        String witnessText = Util.getValueFromJson(witness.getWitnessAsText(), "text");
        assertEquals("Ich hab auch hier wieder ein Pläzchen", witnessText);

        result = tradition.getAllReadings();
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(10, allReadings.size());
        Boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("Plätzchen")) {
                foundReading = true;
                break;
            }
        assertTrue(foundReading);
    }

    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
