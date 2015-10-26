package net.stemmaweb.stemmaserver.acceptancetests;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.printer.GraphViz;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.parser.GraphMLParser;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.TraditionXMLParser;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Response;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Try parsing all the traditions from the live database.
 * Created by tla on 11/08/15.
 */
public class TraditionParseTest {

    private GraphDatabaseService db;
    private GraphMLParser importResource;
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        importResource = new GraphMLParser();

        // Create a root node and test user
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        // Create the Jersey test server
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    @Test
    public void loadAllTraditionsTest() {
        // import all production traditions into the db
        // TODO make this a benchmark test...?
        File testdir = new File("src/TestProductionFiles");
        assumeTrue(testdir.exists() && testdir.isDirectory());

        HashMap<String, TraditionXMLParser> traditionNames = new HashMap<>();
        File[] fileList = testdir.listFiles(x -> x.getName().endsWith("xml"));
        int i = 1;
        for (File testfile : fileList) {
            if (testfile.getName().endsWith("xml")) {
                // Get its name via direct XML parsing
                System.out.println(String.format("Working on %d/%d: %s", i++, fileList.length, testfile.getName()));
                String tradId = testfile.getName().replace(".xml", "");
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
                    Response response = importResource.parseGraphML(testfile.getPath(), "1", "", tradId);
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

                // Now save its parse data by ID
                traditionNames.put(tradId, handler);
            }
        }

        // Now go through each tradition and make sure that the data that
        // we parsed is reflected in the DB.
        for (TraditionModel tm : jerseyTest.resource()
                .path("/traditions")
                .get(new GenericType<List<TraditionModel>>() {})) {

            // Name
            System.out.println("Checking " + tm.getName());
            TraditionXMLParser handler = traditionNames.get(tm.getId());
            assertEquals(tm.getName(), handler.traditionName);

            // Number of nodes
            ArrayList<ReadingModel> dbReadings = jerseyTest.resource()
                    .path("/tradition/" + tm.getId() + "/readings")
                    .get(new GenericType<ArrayList<ReadingModel>>() {});
            assertEquals(handler.numNodes, dbReadings.size());

            // Number of sequence edges
            // Do this with a traversal.
            Node startNode = DatabaseService.getStartNode(tm.getId(), db);
            assertNotNull(startNode);
            AtomicInteger foundEdges = new AtomicInteger(0);
            try (Transaction tx = db.beginTx()) {
                db.traversalDescription().breadthFirst()
                        .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                        .evaluator(Evaluators.all())
                        .uniqueness(Uniqueness.RELATIONSHIP_GLOBAL).traverse(startNode)
                        .relationships().forEach(x -> foundEdges.getAndIncrement());
                tx.success();
            }
            assertEquals(handler.numEdges, foundEdges.get());

            // Number of relationships
            ArrayList<RelationshipModel> dbRelations = jerseyTest.resource()
                    .path("/tradition/" + tm.getId() + "/relationships")
                    .get(new GenericType<ArrayList<RelationshipModel>>() {});
            assertEquals(handler.numRelationships, dbRelations.size());

            // Number of witnesses
            ArrayList<WitnessModel> dbWitnesses = jerseyTest.resource()
                    .path("/tradition/" + tm.getId() + "/witnesses")
                    .get(new GenericType<ArrayList<WitnessModel>>() {});
            assertEquals(handler.numWitnesses, dbWitnesses.size());
        }
    }

    @SuppressWarnings("unused")
    private void toSVG(String traditionID, String outFile)
    {
        DotExporter parser = new DotExporter(db);
        String dot = parser.parseNeo4J(traditionID).getEntity().toString();

        GraphViz gv = new GraphViz();
        String type = "svg";
        File out = new File(outFile + "." + type);   // Linux
        gv.add(dot);
        gv.writeGraphToFile( gv.getGraph( gv.getDotSource(), type ), out );
    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}