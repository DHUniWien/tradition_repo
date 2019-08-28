package net.stemmaweb.stemmaserver.acceptancetests;


import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.TraditionXMLParser;
import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Entity;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Try parsing all the traditions from the live database.
 * Created by tla on 11/08/15.
 */
public class TraditionParseTest extends JerseyTest{

    private GraphDatabaseService db;

    @Override
    protected Application configure() {
        return new ResourceConfig(this.getClass());
    }

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

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
//        Root webResource = new Root();

    }

    @Test
    public void loadAllTraditionsTest() {
        // import all production traditions into the db
        // TODO make this a benchmark test...?
        File testdir = new File("src/TestFiles");
        assumeTrue(testdir.exists() && testdir.isDirectory());

        HashMap<String, TraditionXMLParser> traditionNames = new HashMap<>();
        File[] fileList = testdir.listFiles(x -> x.getName().endsWith("xml"));
        int i = 1;
        if (fileList == null) fail("No production files present!");
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
                    fail("File unreadable");
                } catch (Throwable f) {
                    fail("SAX parser problem");
                }

                // Parse it via importStemmaweb and get the tradition ID
                try {
                    String fileName = testfile.getPath();
                    FormDataMultiPart form = new FormDataMultiPart();
                    form.field("filetype", "stemmaweb")
                            .field("userId", "1");
                    FormDataBodyPart fdp = new FormDataBodyPart("file",
                            new FileInputStream(fileName),
                            MediaType.APPLICATION_OCTET_STREAM_TYPE);
                    form.bodyPart(fdp);

                    Response jerseyResult = target("/tradition")
                            .request(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .post(Entity.entity(form, form.getMediaType()));
                    assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
                    tradId = Util.getValueFromJson(jerseyResult, "tradId");
                } catch (FileNotFoundException f) {
                    // this error should not occur
                    fail("File not found");
//                } catch (JSONException f) {
//                    assertTrue("JSON parsing error", false);
                }

                // Now save its parse data by ID
                traditionNames.put(tradId, handler);
            }
        }

        // Now go through each tradition and make sure that the data that
        // we parsed is reflected in the DB.
        for (TraditionModel tm : target("/traditions")
                .request()
                .get(new GenericType<List<TraditionModel>>() {})) {

            // Name
            System.out.println("Checking " + tm.getName());
            TraditionXMLParser handler = traditionNames.get(tm.getId());
            assertEquals(tm.getName(), handler.traditionName);

            // Number of nodes
            ArrayList<ReadingModel> dbReadings = target("/tradition/" + tm.getId() + "/readings")
                    .request()
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
            ArrayList<RelationModel> dbRelations = target("/tradition/" + tm.getId() + "/relationships")
                    .request()
                    .get(new GenericType<ArrayList<RelationModel>>() {});
            assertEquals(handler.numRelationships, dbRelations.size());

            // Number of witnesses
            ArrayList<WitnessModel> dbWitnesses = target("/tradition/" + tm.getId() + "/witnesses")
                    .request()
                    .get(new GenericType<ArrayList<WitnessModel>>() {});
            assertEquals(handler.numWitnesses, dbWitnesses.size());
        }
    }

    /*
    private void toSVG(String traditionID, Boolean includeRelatedRelationships, String outFile)
    {
        DotExporter parser = new DotExporter(db);
        String dot = parser.writeNeo4J(traditionID, includeRelatedRelationships).getEntity().toString();

        GraphViz gv = new GraphViz();
        String type = "svg";
        File out = new File(outFile + "." + type);   // Linux
        gv.add(dot);
        gv.writeGraphToFile( gv.getGraph( gv.getDotSource(), type ), out );
    }
    */

    @After
    public void tearDown() throws Exception {
        db.shutdown();
        tearDown();
    }
}