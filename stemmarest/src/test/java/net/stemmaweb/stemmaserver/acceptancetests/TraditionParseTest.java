package net.stemmaweb.stemmaserver.acceptancetests;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.TraditionXMLParser;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Try parsing all the traditions from the live database.
 * Created by tla on 11/08/15.
 */
public class TraditionParseTest {

    private GraphDatabaseService db;
    private GraphMLToNeo4JParser importResource;
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        importResource = new GraphMLToNeo4JParser();

        // Create a root node and test user
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = null;
            if (!nodes.hasNext()) {
                rootNode = db.createNode(Nodes.ROOT);
                rootNode.setProperty("name", "Root node");
                rootNode.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
            }

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        // Create the Jersey test server
        Tradition tradition = new Tradition();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(tradition).create();
        jerseyTest.setUp();
    }

    @Test
    public void loadAllTraditionsTest() {
        // import all production traditions into the db
        // TODO make this a benchmark test...?
        File testdir = new File("src/TestProductionFiles");
        assumeTrue(testdir.exists() && testdir.isDirectory());

        HashMap<String, String> traditionNames = new HashMap<String, String>();
        for (File testfile : testdir.listFiles()) {
            if (testfile.getName().endsWith("xml")) {
                // Get its name via direct XML parsing
                String tradId = "";
                SAXParserFactory sfax = SAXParserFactory.newInstance();
                TraditionXMLParser handler = new TraditionXMLParser();
                try {
                    SAXParser parser = sfax.newSAXParser();
                    parser.parse(testfile, handler);
                } catch (FileNotFoundException f) {
                    assertTrue("File unreadable", false);
                } catch (Throwable f) {
                    assertTrue("SAX parser problem", false);
                }

                // Parse it via importGraphML and get the tradition ID
                try {
                    Response response = importResource.parseGraphML(testfile.getPath(), "1", "");
                    assertEquals(response.getStatus(), 200);
                    JSONObject result = new JSONObject(response.getEntity().toString());
                    assertTrue(result.has("tradId"));
                    tradId = result.getString("tradId");
                } catch (FileNotFoundException f) {
                    // this error should not occur
                    assertTrue("File not found", false);
                } catch (JSONException f) {
                    assertTrue("JSON parsing error", false);
                }

                traditionNames.put(tradId, handler.traditionName);
            }
        }

        // Now check to make sure that the names match
        for (TraditionModel tm : jerseyTest.resource()
                .path("/tradition/getalltraditions")
                .get(new GenericType<List<TraditionModel>>() {
                })) {
            assertEquals(tm.getName(), traditionNames.get(tm.getId()));
        }
    }


    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}