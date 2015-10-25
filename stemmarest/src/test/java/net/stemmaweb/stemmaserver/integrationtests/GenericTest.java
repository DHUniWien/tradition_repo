package net.stemmaweb.stemmaserver.integrationtests;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.sun.jersey.core.util.MultivaluedMapImpl;
import net.stemmaweb.model.*;
import net.stemmaweb.rest.*;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * Contains all tests for the api calls related to the tradition.
 *
 * @author PSE FS 2015 Team2
 */
public class GenericTest {

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;
    private GraphMLToNeo4JParser importResource;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory()
                .newImpermanentDatabase())
                .getDatabase();

        importResource = new GraphMLToNeo4JParser();
        Root webResource = new Root();

        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("isAdmin", "1");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        jerseyTest = JerseyTestServerFactory
                .newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    // Tradition test

    @Test
    public void test_01() {
        /* Pearl-Specification:
            my $t = Text::Tradition->new( 'name' => 'empty' );
            is( ref( $t ), 'Text::Tradition', "initialized an empty Tradition object" );
            is( $t->name, 'empty', "object has the right name" );
            is( scalar $t->witnesses, 0, "object has no witnesses" );
        */

        // Status: Test not possible, since there is no API-method to create a 'Tradition'
        // TODO: Implement API-method "CreateTradition()"

        String NAME = "empty";

        // Tests:
        // assertEquals(isType(tradition), TRADITION);
        // assertEquals(tradition.getName(), NAME);
        // assertNotEquals(tradition.getId(), Null);
        // assertEquals(hasRelationship(tradition), false);
    }

    @Test
    public void test_02() {
        /* Pearl-Specification (Part a):
            my $simple = 't/data/simple.txt';
            my $s = Text::Tradition->new(
            'name'  => 'inline',
            'input' => 'Tabular',
            'file'  => $simple,
            );
            is( ref( $s ), 'Text::Tradition', "initialized a Tradition object" );
            is( $s->name, 'inline', "object has the right name" );
            is( scalar $s->witnesses, 3, "object has three witnesses" );
        */

        // Status: Done (with .xml-file)
        // TODO make a tabular collation parser

        String tradId;
        /**
         * load a tradition to the test DB
         */
        File testFile = new File("src/TestFiles/simple.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        /**
         * gets the generated id of the inserted tradition
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }

        List<WitnessModel> witnesses = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {
                });
        assert (witnesses.size() == 3) : "Unexpected number of witnesses.";


    /* Pearl-Specification (Part b):
        ## NOW
        my $wit_a = $s->witness('A');
        is( ref( $wit_a ), 'Text::Tradition::Witness', "Found a witness A" );
        if( $wit_a ) {
            is( $wit_a->sigil, 'A', "Witness A has the right sigil" );
        }
        is( $s->witness('X'), undef, "There is no witness X" );
        ok( !exists $s->{'witnesses'}->{'X'}, "Witness key X not created" );
    */

        // Status: Done (so far)
        // TODO implement witness sigil constraints and test where appropriate. The constraint is:
        // subtype 'Sigil',
        // as 'Str',
        // where { $_ =~ /\A$xml10_name_rx\z/ },
        // message { 'Sigil must be a valid XML attribute string' };


        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
//        assertTrue("There is no witness A", found_witness_a);
//        assertFalse("There is an unexpected witness X", found_witness_x);
    }

    // ######## Relationship tests

    @Test
    public void test_03() {
        /*
        ## NOW - test that local and non-local relationship addition and deletion works
        my $cxfile = 't/data/Collatex-16.xml';
        my $t = Text::Tradition->new(
            'name'  => 'inline',
            'input' => 'CollateX',
            'file'  => $cxfile,
        );
        my $c = $t->collation;

        */

        String tradId;

        /**
         * load a tradition to the test DB
         */
        File testFile = new File("src/TestFiles/Collatex-16.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        /**
         * gets the generated id of the inserted tradition
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }

        /* we don't need this ...
        ClientResponse response = jerseyTest
                .resource()
                .path("/relation/getallrelationships/fromtradition/" + tradId)
                .get(ClientResponse.class);
        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/relation/getallrelationships/fromtradition/" + tradId)
                .get(new GenericType<List<RelationshipModel>>() {});
        */

        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});

        /*
        determine ids of nodes that we are going to use in the following
         */
        String n1="", n2="", n12="", n13="", n21="", n22="", n23="", n24="";
        for (ReadingModel cur_reading : listOfReadings) {
            long cur_rank = cur_reading.getRank();
            String cur_text = cur_reading.getText();
            if (cur_rank == 1L && cur_text.equals("when")) {
                n1 = cur_reading.getId();
            } else if (cur_rank == 2L && cur_text.equals("april")) {
                n2 = cur_reading.getId();
            } else if (cur_rank == 10L) {
                switch (cur_text) {
                    case "the":
                        n12 = cur_reading.getId();
                        break;
                    case "teh":
                        n13 = cur_reading.getId();
                        break;
                }
            } else if (cur_rank == 16L) {
                switch (cur_text) {
                    case "unto":
                        n21 = cur_reading.getId();
                        break;
                    case "to":
                        n22 = cur_reading.getId();
                        break;
                    case "teh":
                        n23 = cur_reading.getId();
                        break;
                }
            } else if ((cur_rank == 17L) && (cur_text.equals("the"))) {
                n24 = cur_reading.getId();
            }
        }

        /* ... and we don't need this either
        List<String> stemmata = jerseyTest
                .resource()
                .path("/stemma/getallstemmata/fromtradition/" + tradId)
                .get(new GenericType<List<String>>() {});
        */

        /*
        my @v1 = $c->add_relationship( 'n21', 'n22', { 'type' => 'lexical' } ); # 'unto', 'to'
        is( scalar @v1, 1, "Added a single relationship" );
        is( $v1[0]->[0], 'n21', "Got correct node 1" );
        is( $v1[0]->[1], 'n22', "Got correct node 2" );
         */

        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(n21);
        relationship.setTarget(n22);
        relationship.setType("lexical");
        relationship.setScope("local");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
        ArrayList<GraphModel> tmpGraphModel = response.getEntity(new GenericType<ArrayList<GraphModel>>(){});
        assertEquals(tmpGraphModel.size(), 1L);
        String node1_id = tmpGraphModel.get(0).getReadings().get(0).getId();
        String node2_id = tmpGraphModel.get(0).getReadings().get(1).getId();
        assertTrue((node1_id.equals(n21) && node2_id.equals(n22)) ||
                (node1_id.equals(n22) && node2_id.equals(n21)));
        /* just some example code ...
        ArrayList<GraphModel> readingsAndRelationships1 = actualResponse1.getEntity(new GenericType<ArrayList<GraphModel>>(){});
        GraphModel readingsAndRelationship1 = readingsAndRelationships1.get(0);
        relationshipId1 = readingsAndRelationship1.getRelationships().get(0).getId();
        */

        /*
        my @v2 = $c->add_relationship( 'n24', 'n23',  # 'the', 'teh' near the end
        { 'type' => 'spelling', 'scope' => 'global' } );
        is( scalar @v2, 2, "Added a global relationship with two instances" );
        */

        relationship = new RelationshipModel();
        relationship.setSource(n24);
        relationship.setTarget(n23);
        relationship.setType("spelling");
        relationship.setScope("document");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(new GenericType<ArrayList<GraphModel>>(){}).size(), 2L);


        /*
        @v1 = $c->del_relationship( 'n22', 'n21' );
        is( scalar @v1, 1, "Deleted first relationship" );
         */

        relationship = new RelationshipModel();
        relationship.setSource(n22);
        relationship.setTarget(n21);
        relationship.setScope("local");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, relationship);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(Long.class), Long.valueOf(1));


        /*
        @v2 = $c->del_relationship( 'n12', 'n13', 'everywhere' ); 'the', 'the' before drought/march
        is( scalar @v2, 2, "Deleted second global relationship" );
         */

        relationship = new RelationshipModel();
        relationship.setSource(n12);
        relationship.setTarget(n13);
        relationship.setScope("document");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, relationship);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(Long.class), Long.valueOf(2L));


        /*
        my @v3 = $c->del_relationship( 'n1', 'n2' );  # 'when', 'april'
        is( scalar @v3, 0, "Nothing deleted on non-existent relationship" );
        removeModel = new RelationshipModel();
         */

        relationship.setSource(n1);
        relationship.setTarget(n2);
        relationship.setScope("local");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, relationship);
        assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(Long.class), Long.valueOf(0L));


        /*
        my @v4 = $c->add_relationship( 'n24', 'n23',
                { 'type' => 'spelling', 'scope' => 'global' } );
        is( @v4, 2, "Re-added global relationship" );
        */

        relationship = new RelationshipModel();
        relationship.setSource(n24);
        relationship.setTarget(n23);
        relationship.setType("spelling");
        relationship.setScope("document");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(new GenericType<ArrayList<GraphModel>>(){}).size(), 2L);


        /*
        @v4 = $c->del_relationship( 'n12', 'n13' );  # not everywhere
        is( @v4, 1, "Only specified relationship deleted this time" );
        ok( $c->get_relationship( 'n24', 'n23' ), "Other globally-added relationship exists" );
        */

        relationship = new RelationshipModel();
        relationship.setSource(n12);
        relationship.setTarget(n13);
        relationship.setScope("local");

        // TODO redo this test with a call to /relationships
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class, relationship);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(response.getEntity(Long.class), Long.valueOf(1L));

        MultivaluedMap queryParams = new MultivaluedMapImpl();
        queryParams.add("node1", n24);
        queryParams.add("node2", n23);

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(ClientResponse.class);
        // assertEquals(Status.OK.getStatusCode(), response.getStatus());
        // assertEquals(response.getEntity(String.class), "RELATED");


        /*
        # Test 1.3: attempt relationship with a meta reading (should fail)
        $t1 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/legendfrag.xml' );
        ok( $t1, "Parsed test fragment file" );
        my $c1 = $t1->collation;
        */

        File testFile2 = new File("src/TestFiles/legendfrag.xml");
        try {
            importResource.parseGraphML(testFile2.getPath(), "1", "TraditionB");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        /**
         * gets the generated id of the inserted 2nd tradition
         */
        String tradId2;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId2 = (String) nodes.next().getProperty("id");
            if (tradId2.equals(tradId)) {
                tradId2 = (String) nodes.next().getProperty("id");
            }
            tx.success();
        }

        listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId2 + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});

        String r8_1="", r9_2="";
        for (ReadingModel cur_reading : listOfReadings) {
            long cur_rank = cur_reading.getRank();
            String cur_text = cur_reading.getText();
            if (cur_rank == 9L
                    && cur_reading.getIs_lacuna()) {
                r8_1 = cur_reading.getId();
            } else if (cur_rank == 4L && cur_text.equals("henricus")) {
                r9_2 = cur_reading.getId();
            }
        }


        /*
        try {
            $c1->add_relationship( 'r8.1', 'r9.2', { 'type' => 'collated' } );  # 8.1 is lacuna, 9.2 is "henricus"
            ok( 0, "Allowed a meta-reading to be used in a relationship" );
        } catch ( Text::Tradition::Error $e ) {
            is( $e->message, 'Cannot set relationship on a meta reading',
                    "Relationship link prevented for a meta reading" );
        }
         */

        relationship = new RelationshipModel();
        relationship.setSource(r9_2);
        relationship.setTarget(r8_1);
        relationship.setType("collated");
        relationship.setScope("local");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CONFLICT.getStatusCode(), response.getStatus());


        /* SK: Tests if we are able to create a relationship from the 1st to the 2nd tradition */

        relationship = new RelationshipModel();
        relationship.setSource(r9_2);
        relationship.setTarget(n24);
        relationship.setType("collated");
        relationship.setScope("local");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CONFLICT.getStatusCode(), response.getStatus());
    }


    @Test
    public void test_04() {
        /*
        # Test 2.1: try to equate nodes that are prevented with a real intermediate equivalence
        my $t2;
        warnings_exist {
            $t2 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/legendfrag.xml' );
        } [qr/Cannot set relationship on a meta reading/],
            "Got expected relationship drop warning on parse";
         my $c2 = $t2->collation;
         */

        /**
         * load a tradition to the test DB
         */
        File testFile = new File("src/TestFiles/legendfrag.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        /**
         * gets the generated id of the inserted tradition
         */
        String tradId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }


        /*
        $c2->add_relationship( 'r9.2', 'r9.3', { 'type' => 'lexical' } );
        */

        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {
                });

        String r8_6 = "", r9_2 = "", r9_3 = "", r10_3 = "";
        for (ReadingModel cur_reading : listOfReadings) {
            long cur_rank = cur_reading.getRank();
            String cur_text = cur_reading.getText();
            if (cur_rank == 3L && cur_text.equals("venerabilis")) {
                r8_6 = cur_reading.getId();
            } else if (cur_rank == 4L && cur_text.equals("henricus")) {
                r9_2 = cur_reading.getId();
            } else if (cur_rank == 4L && cur_text.equals("pontifex")) {
                r9_3 = cur_reading.getId();
            } else if (cur_rank == 5L && cur_text.equals("venerabilis")) {
                r10_3 = cur_reading.getId();
            }
        }

        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(r9_2);
        relationship.setTarget(r9_3);
        relationship.setType("lexical");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());


        /*
        my $trel2 = $c2->get_relationship( 'r9.2', 'r9.3' );
        is( ref( $trel2 ), 'Text::Tradition::Collation::Relationship',
                "Created blocking relationship" );
        is( $trel2->type, 'lexical', "Blocking relationship is not a collation" );
        */

        MultivaluedMap queryParams = new MultivaluedMapImpl();
        queryParams.add("node1", r9_2);
        queryParams.add("node2", r9_3);

        // TODO use call to /relationships
        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relationships")
                .queryParams(queryParams)
                .get(ClientResponse.class);
        // assertEquals(Status.OK.getStatusCode(), response.getStatus());
        // assertEquals(response.getEntity(String.class), "RELATED");

        /*
        # This time the link ought to fail
        try {
            $c2->add_relationship( 'r8.6', 'r10.3', { 'type' => 'orthographic' } );
            ok( 0, "Added cross-equivalent bad relationship" );
        } catch ( Text::Tradition::Error $e ) {
            like( $e->message, qr/witness loop/,
                    "Existing equivalence blocked crossing relationship" );
        }
        */

        relationship = new RelationshipModel();
        relationship.setSource(r8_6);
        relationship.setTarget(r10_3);
        relationship.setType("orthographic");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CONFLICT.getStatusCode(), response.getStatus());


        /*
        try {
            $c2->calculate_ranks();
            ok( 1, "Successfully calculated ranks" );
        } catch ( Text::Tradition::Error $e ) {
            ok( 0, "Collation now has a cycle: " . $e->message );
        }
        */

        //TODO (sk_20150928): implement recalculation of ranks!

    }


    @Test
    public void test_05() {
        /*
        # Test 3.1: make a straightforward pair of transpositions.
        my $t3 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/lf2.xml' );
        */

        File testFile = new File("src/TestFiles/lf2.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        /**
         * gets the generated id of the inserted tradition
         */
        String tradId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }


        /**
         * determine node ids
         */

        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {});

        String r28_2 = "", r28_3 = "", r29_2 = "", r29_3 = "", r36_3 = "", r36_4 = "", r38_2 = "", r38_3 = "";
        for (ReadingModel cur_reading : listOfReadings) {
            String  cur_rank = cur_reading.getRank().toString();
            String cur_text = cur_reading.getText();
            switch (cur_rank) {
                case "3":
                    if (cur_text.equals("luminaribus")) {
                        r28_2 = cur_reading.getId();
                    } else if (cur_text.equals("magnis")) {
                        r28_3 = cur_reading.getId();
                    }
                    break;
                case "4":
                    if (cur_text.equals("luminaribus")) {
                        r29_2 = cur_reading.getId();
                    } else if (cur_text.equals("magnis")) {
                        r29_3 = cur_reading.getId();
                    }
                    break;
                case "11":
                    if (cur_text.equals("noticiam")) {
                        r36_3 = cur_reading.getId();
                    } else if (cur_text.equals("cultum")) {
                        r36_4 = cur_reading.getId();
                    }
                    break;
                case "13":
                    if (cur_text.equals("noticiam")) {
                        r38_2 = cur_reading.getId();
                    } else if (cur_text.equals("cultum")) {
                        r38_3 = cur_reading.getId();
                    }
                    break;

            }
        }

        /*
        # Test 1: try to equate nodes that are prevented with an intermediate collation
        my $c3 = $t3->collation;
        try {
            $c3->add_relationship( 'r36.4', 'r38.3', { 'type' => 'transposition' } );
            ok( 1, "Added straightforward transposition" );
        } catch ( Text::Tradition::Error $e ) {
            ok( 0, "Failed to add normal transposition: " . $e->message );
        }
        */

        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(r36_4);
        relationship.setTarget(r38_3);
        relationship.setType("transposition");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());

        /*
        try {
            $c3->add_relationship( 'r36.3', 'r38.2', { 'type' => 'transposition' } );
            ok( 1, "Added straightforward transposition complement" );
        } catch ( Text::Tradition::Error $e ) {
            ok( 0, "Failed to add normal transposition complement: " . $e->message );
        }
        */

        RelationshipModel relationship2 = new RelationshipModel();
        relationship2.setSource(r36_3);
        relationship2.setTarget(r38_2);
        relationship2.setType("transposition");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship2);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        /*
        # Test 3.2: try to make a transposition that could be a parallel.
        try {
            $c3->add_relationship( 'r28.2', 'r29.2', { 'type' => 'transposition' } );
            ok( 0, "Added bad collocated transposition" );
        } catch ( Text::Tradition::Error $e ) {
            like( $e->message, qr/Readings appear to be colocated/,
                    "Prevented bad collocated transposition" );
        }
        */

        RelationshipModel relationship3 = new RelationshipModel();
        relationship3.setSource(r28_2);
        relationship3.setTarget(r29_2);
        relationship3.setType("transposition");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship3);
        assertEquals(Status.CONFLICT.getStatusCode(), response.getStatus());

        /*
        # Test 3.3: make the parallel, and then make the transposition again.
        try {
            $c3->add_relationship( 'r28.3', 'r29.3', { 'type' => 'orthographic' } );
            ok( 1, "Equated identical readings for transposition" );
        } catch ( Text::Tradition::Error $e ) {
            ok( 0, "Failed to equate identical readings: " . $e->message );
        }
        */

        RelationshipModel relationship4 = new RelationshipModel();
        relationship4.setSource(r28_3);
        relationship4.setTarget(r29_3);
        relationship4.setType("orthographic");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship4);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        /*
        try {
            $c3->add_relationship( 'r28.2', 'r29.2', { 'type' => 'transposition' } );
            ok( 1, "Added straightforward transposition complement" );
        } catch ( Text::Tradition::Error $e ) {
            ok( 0, "Failed to add normal transposition complement: " . $e->message );
        }
        */

        RelationshipModel relationship5 = new RelationshipModel();
        relationship5.setSource(r28_2);
        relationship5.setTarget(r29_2);
        relationship5.setType("transposition");

        response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship5);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }


    @Test
    public void test_06() {
        /**
            # Test 4: make a global relationship that involves re-ranking a node first, when
            # the prior rank has a potential match too
            my $t4 = Text::Tradition->new('input' => 'Self', 'file' => 't/data/globalrel_test.xml');
            my $c4 = $t4->collation;
        **/

        Response parseResponse = null;
        File testFile = new File("src/TestFiles/globalrel_test.xml");
        try {
            parseResponse = importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        String tradId = Util.getValueFromJson(parseResponse, "tradId");


        /**
         * determine node ids
         */

        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {
                });

        String r463_2 = "", r463_4 = "";
        for (ReadingModel cur_reading : listOfReadings) {
            switch (String.valueOf(cur_reading.getRank())) {
                case "8":
                    if (cur_reading.getText().equals("hämehen")) {
                        r463_2 = cur_reading.getId();
                    }
                    break;
                case "9":
                    if (cur_reading.getText().equals("Hämehen")) {
                        r463_4 = cur_reading.getId();
                    }
                    break;
            }
        }

        /**
            try {
                $c4->add_relationship( 'r463.2', 'r463.4', { type => 'orthographic',
                                                             scope => 'global' } );
                ok( 1, "Added global relationship without error" );
            } catch ( Text::Tradition::Error $e ) {
                ok( 0, "Failed to add global relationship when same-rank alternative exists: "
                    . $e->message );
            }
         **/

        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(r463_2);
        relationship.setTarget(r463_4);
        relationship.setType("orthographic");
//        relationship.setScope("document");
        relationship.setScope("local");

        ClientResponse response = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        /**
            $c4->calculate_ranks();
            # Do our readings now share a rank?
            is( $c4->reading('r463.2')->rank, $c4->reading('r463.4')->rank,
                "Expected readings now at same rank" );
        **/

        Tradition tradition = new Tradition(tradId);
        assertEquals(tradition.recalculateRank(Long.parseLong(r463_2)), true);

        response = jerseyTest
                .resource()
                .path("/reading/" + r463_2)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        long rank_r463_2 = response.getEntity(ReadingModel.class).getRank();

        response = jerseyTest
                .resource()
                .path("/reading/" + r463_4)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        long rank_r463_4 = response.getEntity(ReadingModel.class).getRank();
        assertEquals(rank_r463_2, rank_r463_4);
    }

    // ######## Collation tests

    @Test
    public void test_07() {
        /**
            my $cxfile = 't/data/Collatex-16.xml';
            my $t = Text::Tradition->new(
                    'name'  => 'inline',
                    'input' => 'CollateX',
                    'file'  => $cxfile,
                    );
            my $c = $t->collation;
        **/

        File testFile = new File("src/TestFiles/COLLATEX-16.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
        /*
        my $rno = scalar $c->readings;
        # Split n21 ('unto') for testing purposes into n21p0 ('un', 'join_next' => 1 ) and n21 ('to')...
        */
        /**
         * gets the generated id of the inserted tradition
         */
        String tradId;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }


        /**
         *  determine node ids
         */

        List<ReadingModel> listOfReadings = jerseyTest.resource()
                .path("/tradition/" + tradId + "/readings")
                .get(new GenericType<List<ReadingModel>>() {
                });

        String n3="", n4="", n9="", n10="", n21 = "";
        for (ReadingModel cur_reading : listOfReadings) {
            long cur_rank = cur_reading.getRank();
            String cur_text = cur_reading.getText();
            if (cur_rank == 3L && cur_text.equals("with")) {
                n3 = cur_reading.getId();
            } else if (cur_rank == 4L && cur_text.equals("his")) {
                n4 = cur_reading.getId();
            } else if (cur_rank == 17L && cur_text.equals("rood")) {
                n9 = cur_reading.getId();
            } else if (cur_rank == 18L && cur_text.equals("root")) {
                n10 = cur_reading.getId();
            } else if (cur_rank == 16L && cur_text.equals("unto")) {
                n21 = cur_reading.getId();
            }
        }

        //TODO (SK): modify 'splitreading', since it wants a 'rank-gap'
        /*
        CharacterModel characterModel = new CharacterModel();
        characterModel.setCharacter("");
        String split_idx = "2";
        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/splitreading/ofreading/" + n21
                        + "/withsplitindex/2")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, characterModel);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        GraphModel readingsAndRelationshipsModel = response.getEntity(GraphModel.class);
        */


        /**
         *  # Combine n3 and n4 ( with his )
         *  $c->merge_readings( 'n3', 'n4', 1 );
         */
        String blub = "/reading/compressreadings/read1id/"+n3+"/read2id/"+n4+"/concatenate/1";
        CharacterModel characterModel = new CharacterModel();
        characterModel.setCharacter(" ");
        ClientResponse response = jerseyTest
                .resource()
                .path("/reading/" + n3 + "/concatenate/" + n4 + "/1")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, characterModel);

        assertEquals(ClientResponse.Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        /**
         *  ok( !$c->reading('n4'), "Reading n4 is gone" );
         */
        response = jerseyTest
                .resource()
                .path("/reading/" + n4)
                .get(ClientResponse.class);

        assertEquals(ClientResponse.Status.NO_CONTENT.getStatusCode(), response.getStatusInfo().getStatusCode());

        /**
         *  is( $c->reading('n3')->text, 'with his', "Reading n3 has both words" );
         */
        response = jerseyTest
                .resource()
                .path("/reading/" + n3)
                .get(ClientResponse.class);

        assertEquals(ClientResponse.Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        ReadingModel reading = response.getEntity(ReadingModel.class);
        assertEquals("with his", reading.getText());


        /**
         *  # Collapse n9 and n10 ( rood / root )
         *  $c->merge_readings( 'n9', 'n10' );
         */

        response = jerseyTest
                .resource()
                .path("/reading/" + n9 + "/merge/" + n10)
                .post(ClientResponse.class, characterModel);

        assertEquals(ClientResponse.Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());

        /**
         *  ok( !$c->reading('n10'), "Reading n10 is gone" );
         */
        response = jerseyTest
                .resource()
                .path("/reading/" + n10)
                .get(ClientResponse.class);

        assertEquals(ClientResponse.Status.NO_CONTENT.getStatusCode(), response.getStatusInfo().getStatusCode());

        /**
         *  is( $c->reading('n9')->text, 'rood', "Reading n9 has an unchanged word" );
         */
        response = jerseyTest
                .resource()
                .path("/reading/" + n9)
                .get(ClientResponse.class);

        assertEquals(ClientResponse.Status.OK.getStatusCode(), response.getStatusInfo().getStatusCode());
        reading = response.getEntity(ReadingModel.class);
        assertEquals("rood", reading.getText());

/*

    # Try to combine n21 and n21p0. This should break.
    my $remaining = $c->reading('n21');
    $remaining ||= $c->reading('n22');  # one of these should still exist
    try {
        $c->merge_readings( 'n21p0', $remaining, 1 );
        ok( 0, "Bad reading merge changed the graph" );
    } catch( Text::Tradition::Error $e ) {
        like( $e->message, qr/neither concatenated nor collated/, "Expected exception from bad concatenation" );
    } catch {
        ok( 0, "Unexpected error on bad reading merge: $@" );
    }

    try {
        $c->calculate_ranks();
        ok( 1, "Graph is still evidently whole" );
    } catch( Text::Tradition::Error $e ) {
        ok( 0, "Caught a rank exception: " . $e->message );
    }

    ## TODO again with the tabular / CSV input.
            # Test right-to-left reading merge.
            my $rtl = Text::Tradition->new(
            'name'  => 'inline',
            'input' => 'Tabular',
            'sep_char' => ',',
            'direction' => 'RL',
            'file'  => 't/data/arabic_snippet.csv'
            );
    my $rtlc = $rtl->collation;
    is( $rtlc->reading('r8.1')->text, 'سبب', "Got target first reading in RTL text" );
    my $pt = $rtlc->path_text('A');
    my @path = $rtlc->reading_sequence( $rtlc->start, $rtlc->end, 'A' );
    is( $rtlc->reading('r9.1')->text, 'صلاح', "Got target second reading in RTL text" );
    $rtlc->merge_readings( 'r8.1', 'r9.1', 1 );
    is( $rtlc->reading('r8.1')->text, 'سبب صلاح', "Got target merged reading in RTL text" );
    is( $rtlc->path_text('A'), $pt, "Path text is still correct" );
    is( scalar($rtlc->reading_sequence( $rtlc->start, $rtlc->end, 'A' )),
    scalar(@path) - 1, "Path was shortened" );
}
*/
    }


    // ## Collation correction tests

    @Test
    public void test_08() {
        /**
            my $st = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/collatecorr.xml' );
            is( ref( $st ), 'Text::Tradition', "Got a tradition from test file" );
            ok( $st->has_witness('Ba96'), "Tradition has the affected witness" );
         */
        File testFile = new File("src/TestFiles/collatecorr.xml");
        try {
            importResource.parseGraphML(testFile.getPath(), "1", "Tradition_08");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        String tradId = null;
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (u:USER)--(t:TRADITION) return t");
            Iterator<Node> nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tradId = (String) nodes.next().getProperty("id");
            tx.success();
        }
        assertNotNull(tradId);

/*
        my $sc = $st->collation;
        my $numr = 17;
        ok( $sc->reading('n131'), "Tradition has the affected reading" );
        is( scalar( $sc->readings ), $numr, "There are $numr readings in the graph" );
        is( $sc->end->rank, 14, "There are fourteen ranks in the graph" );

        # Detach the erroneously collated reading
        my( $newr, @del_rdgs ) = $sc->duplicate_reading( 'n131', 'Ba96' );
        ok( $newr, "New reading was created" );
        ok( $sc->reading('n131_0'), "Detached the bad collation with a new reading" );
        is( scalar( $sc->readings ), $numr + 1, "A reading was added to the graph" );
        is( $sc->end->rank, 10, "There are now only ten ranks in the graph" );

        # Check that the bad transposition is gone
        is( scalar @del_rdgs, 1, "Deleted reading was returned by API call" );
        is( $sc->get_relationship( 'n130', 'n135' ), undef, "Bad transposition relationship is gone" );

        # The collation should not be fixed
        my @pairs = $sc->identical_readings();  # in the Java this is "reading/couldbeidenticalreadings/..." and the pair *should* be found.
        is( scalar @pairs, 0, "Not re-collated yet" );
        # Fix the collation
        ok( $sc->merge_readings( 'n124', 'n131_0' ), "Collated the readings correctly" );
@pairs = $sc->identical_readings( start => 'n124', end => $csucc->id );
        is( scalar @pairs, 3, "Found three more identical readings" );
        is( $sc->end->rank, 11, "The ranks shifted appropriately" );
        $sc->flatten_ranks();
        is( scalar( $sc->readings ), $numr - 3, "Now we are collated correctly" );

        # Check that we can't "duplicate" a reading with no wits or with all wits
        try {
        my( $badr, @del_rdgs ) = $sc->duplicate_reading( 'n124' );
        ok( 0, "Reading duplication without witnesses throws an error" );
        } catch( Text::Tradition::Error $e ) {
        like( $e->message, qr/Must specify one or more witnesses/,
        "Reading duplication without witnesses throws the expected error" );
        } catch {
        ok( 0, "Reading duplication without witnesses threw the wrong error" );
        }

        try {
        my( $badr, @del_rdgs ) = $sc->duplicate_reading( 'n124', 'Ba96', 'Mü11475' );
        ok( 0, "Reading duplication with all witnesses throws an error" );
        } catch( Text::Tradition::Error $e ) {
        like( $e->message, qr/Cannot join all witnesses/,
        "Reading duplication with all witnesses throws the expected error" );
        } catch {
        ok( 0, "Reading duplication with all witnesses threw the wrong error" );
        }

        try {
        $sc->calculate_ranks();
        ok( 1, "Graph is still evidently whole" );
        } catch( Text::Tradition::Error $e ) {
        ok( 0, "Caught a rank exception: " . $e->message );
        }

        ## TODO add output as adjacency list, as tabular, as TEI, etc. etc.

        ## TODO we need an alignment table output! And for it to be tested.
*/
    }

    /**
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
