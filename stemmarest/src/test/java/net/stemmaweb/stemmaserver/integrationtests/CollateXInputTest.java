package net.stemmaweb.stemmaserver.integrationtests;

import com.sun.jersey.test.framework.JerseyTest;
import junit.framework.TestCase;
import net.stemmaweb.model.ReadingModel;
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

import javax.ws.rs.core.Response;
import java.io.FileInputStream;
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

    public void testParseCollateX() throws Exception {
        Response result = importResource.parseCollateX("src/TestFiles/plaetzchen_cx.xml", "1", "Florilegium", "LR");
        assertEquals(Response.Status.CREATED.getStatusCode(), result.getStatus());

        String tradId = Util.getValueFromJson(result, "tradId");
        Tradition tradition = new Tradition(tradId);

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
            if (r.getText().equals("Plätzchen"))
                foundReading = true;
        assertTrue(foundReading);
    }

}
