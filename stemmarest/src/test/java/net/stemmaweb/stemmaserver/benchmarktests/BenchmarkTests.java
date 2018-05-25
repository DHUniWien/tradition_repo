package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Root;

import net.stemmaweb.stemmaserver.Util;
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
    static File testfile;

    static String tradId;
    static long duplicateReadingNodeId;
    static long theRoot;
    static long untoMe;

    static JerseyTest jerseyTest;

    public static String createTraditionFromFile(String tName, String tDir, String userId,
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
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .post(ClientResponse.class, form);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(tradId.length() != 0);
        return  tradId;
    }

    /**
     * Measure the speed to change the owner of a Tradition and change it back
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void changeTraditionMetadata(){
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("1");

        ClientResponse ownerChangeResponse = jerseyTest.resource()
                .path("/tradition/1001")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class,textInfo);
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("0");

        ownerChangeResponse = jerseyTest.resource()
                .path("/tradition/1001")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class,textInfo);
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
    public void importStemmaweb(){
        try {
            String fileName = testfile.getPath();
            createTraditionFromFile("Tradition", "LR", "1", fileName, "stemmaweb");
        } catch (FileNotFoundException f) {
            // this error should not occur
            fail();
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
                .path("/tradition/1001/witness/W0/text")
                .queryParam("start", "2")
                .queryParam("end", "5")
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
        RelationModel relationship = new RelationModel();
        relationship.setSource(theRoot + "");
        relationship.setTarget(untoMe + "");
        relationship.setType("grammatical");
        relationship.setAlters_meaning(0L);
        relationship.setIs_significant("yes");
        relationship.setScope("local");


        ClientResponse actualResponse = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relation")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, relationship);
        GraphModel readingsAndRelationships =
                actualResponse.getEntity(new GenericType<GraphModel>() {});
        String relationshipId = ((RelationModel) readingsAndRelationships
                .getRelations().toArray()[0]).getId();

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
                .path("/user/1337")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        ClientResponse actualResponse = jerseyTest.resource()
                .path("/user/1337")
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }
}
