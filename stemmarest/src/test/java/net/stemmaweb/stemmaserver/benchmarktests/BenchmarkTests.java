package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.GenericType;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphMLToNeo4JParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@RunWith(MockitoJUnitRunner.class)

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmarkTestGenerating")
public abstract class BenchmarkTests {

    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    /*
     * In order not to measure the startup time of jerseytest the startup needs to be done @BeforeClass
     * so all jerseyTest related objects need to be static.
     */
    static Root webResource;
    static GraphMLToNeo4JParser importResource;

    static File testfile;

    static String tradId;
    static long duplicateReadingNodeId;
    static long theRoot;
    static long untoMe;

    static JerseyTest jerseyTest;

    /**
     * Measure the speed to change the owner of a Tradition and change it back
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void changeTraditionMetadata(){
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIsPublic(false);
        textInfo.setOwnerId("1");

        ClientResponse ownerChangeResponse = jerseyTest.resource()
                .path("/tradition/1001")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class,textInfo);
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIsPublic(false);
        textInfo.setOwnerId("0");

        ownerChangeResponse = jerseyTest.resource()
                .path("/tradition/1001")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class,textInfo);
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

    }

    /**
     * Measure the speed to get a reading
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getReading(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/reading/20")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the speed to get all traditions in the database
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllTraditions(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/traditions")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }



    /**
     * Measure the time to get all witnesses out of the database
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllWitnesses(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/1001/witnesses")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all relationships of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllRelationships(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to export a Tradition as xml
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getTradition(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/"+tradId)
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }



    /**
     * Measure the time to import a tradition as xml
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void importGraphMl(){
        try {
            importResource.parseGraphML(testfile.getPath(), "1", "Tradition");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }
    }

    /**
     * Measure the time to export the dot
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getDot(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/dot")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a Witness as Plaintext
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsText(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/1001/witness/W0/text")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a part of a Witness as Plaintext
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsTextBetweenRanks(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/1001/witness/W0/text/2/5")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a witness as a json array
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsReadings(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/1001/witness/W0/readings")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get the next reading of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getNextReadingOfAWitness(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/reading/" + untoMe + "/next/A")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get the previous reading of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getpreviousReadingOfAWitness(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/reading/" + untoMe + "/prior/A")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all readings of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllReadingsOfATradition(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/1001/readings")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all stemmata of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllStemmata(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/stemmata")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get all identical readings of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getIdenticalReadingsOfATradition(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/identicalreadings/1/8")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all readings which could be identical
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getCouldBeIdentical(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/mergeablereadings/1/15")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to create a relationship and remove it
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void createAndDeleteARelationship(){
        RelationshipModel relationship = new RelationshipModel();
        relationship.setSource(theRoot + "");
        relationship.setTarget(untoMe + "");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setReading_a("the root");
        relationship.setReading_b("unto me");
        relationship.setScope("local");


        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, relationship);
        ArrayList<GraphModel> readingsAndRelationships =
                actualResponse.getEntity(new GenericType<ArrayList<GraphModel>>() {});
        String relationshipId = readingsAndRelationships.get(0).getRelationships().get(0).getId();

        ClientResponse removalResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation/" + relationshipId)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
    }

    /**
     * Measure the time to get a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getUserById(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/user/0")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get all traditions of a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getTraditionsOfAUser(){
        ClientResponse actualResponse = jerseyTest.resource()
                .path("/user/0/traditions")
                .get(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to create and delete a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void createAndDeleteAUser(){
        String jsonPayload = "{\"role\":\"user\",\"id\":1337}";
        ClientResponse response = jerseyTest.resource()
                .path("/user")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        ClientResponse actualResponse = jerseyTest.resource()
                .path("/user/1337")
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }
}
