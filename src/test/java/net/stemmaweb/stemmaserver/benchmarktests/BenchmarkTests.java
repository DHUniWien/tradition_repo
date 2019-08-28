package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.GraphModel;
import net.stemmaweb.model.RelationModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.rest.Root;

import net.stemmaweb.stemmaserver.Util;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;


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
        Response jerseyResult = jerseyTest.target("/tradition")
                //.request(MediaType.MULTIPART_FORM_DATA_TYPE)
                .request()
                .post(Entity.entity(form, MediaType.MULTIPART_FORM_DATA_TYPE));
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

        Response ownerChangeResponse = jerseyTest
                .target("/tradition/1001")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(textInfo));
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("0");

        ownerChangeResponse = jerseyTest
                .target("/tradition/1001")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(textInfo));
        assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());
    }

    /**
     * Measure the speed to get a reading
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getReading(){
        Response actualResponse = jerseyTest
                .target("/reading/20")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the speed to get all traditions in the database
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllTraditions(){
        Response actualResponse = jerseyTest
                .target("/traditions")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }



    /**
     * Measure the time to get all witnesses out of the database
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllWitnesses(){
        Response actualResponse = jerseyTest
                .target("/tradition/1001/witnesses")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all relationships of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllRelationships(){
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relationships")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to export a Tradition as xml
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getTradition(){
        Response actualResponse = jerseyTest
                .target("/tradition/"+tradId)
                .request()
                .get();
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
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/dot")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a Witness as Plaintext
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsText(){
        Response actualResponse = jerseyTest
                .target("/tradition/1001/witness/W0/text")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a part of a Witness as Plaintext
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsTextBetweenRanks(){
        Response actualResponse = jerseyTest
                .target("/tradition/1001/witness/W0/text")
                .queryParam("start", "2")
                .queryParam("end", "5")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get a witness as a json array
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getWitnessAsReadings(){
        Response actualResponse = jerseyTest
                .target("/tradition/1001/witness/W0/readings")
                .request()
                .get(Response.class);
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get the next reading of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getNextReadingOfAWitness(){
        Response actualResponse = jerseyTest
                .target("/reading/" + untoMe + "/next/A")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get the previous reading of a witness
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getpreviousReadingOfAWitness(){
        Response actualResponse = jerseyTest
                .target("/reading/" + untoMe + "/prior/A")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all readings of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllReadingsOfATradition(){
        Response actualResponse = jerseyTest
                .target("/tradition/1001/readings")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all stemmata of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getAllStemmata(){
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/stemmata")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get all identical readings of a tradition
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getIdenticalReadingsOfATradition(){
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/identicalreadings/1/8")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to get all readings which could be identical
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getCouldBeIdentical(){
        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/mergeablereadings/1/15")
                .request()
                .get(Response.class);
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


        Response actualResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(relationship));
        GraphModel readingsAndRelationships =
                actualResponse.readEntity(new GenericType<GraphModel>() {});
        String relationshipId = ((RelationModel) readingsAndRelationships
                .getRelations().toArray()[0]).getId();

        Response removalResponse = jerseyTest
                .target("/tradition/" + tradId + "/relation/" + relationshipId)
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
    }

    /**
     * Measure the time to get a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getUserById(){
        Response actualResponse = jerseyTest
                .target("/user/0")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }


    /**
     * Measure the time to get all traditions of a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void getTraditionsOfAUser(){
        Response actualResponse = jerseyTest
                .target("/user/0/traditions")
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Measure the time to create and delete a user
     */
    @BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
    @Test
    public void createAndDeleteAUser(){
        String jsonPayload = "{\"role\":\"user\",\"id\":1337}";
        Response response = jerseyTest
                .target("/user/1337")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(jsonPayload));
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        Response actualResponse = jerseyTest
                .target("/user/1337")
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
    }
}
