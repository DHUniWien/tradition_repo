package net.stemmaweb.stemmaserver.benachmarktests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.ReturnIdModel;
import net.stemmaweb.model.TraditionMetadataModel;
import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.rest.Witness;
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
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@RunWith(MockitoJUnitRunner.class)

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmarkTestGenerating")
public abstract class BenachmarkTests {
	
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();

	/*
	 * In order not to measure the startup time of jerseytest the startup needs to be done @BeforeClass
	 * so all jerseyTest related objects need to be static.
	 */
	protected static User userResource;
	protected static Tradition traditionResource;
	protected static Witness witnessResource;
	protected static Reading readingResoruce;
	protected static Relation relationResource;
	protected static GraphMLToNeo4JParser importResource ;
	
	protected static String filename = "";
	
	protected static String tradId;
	protected static long duplicateReadingNodeId;
	protected static long theRoot;
	protected static long untoMe;
	
	protected static JerseyTest jerseyTest;

	/**
	 * Measure the speed to change the owner of a Tradition and change it back
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void changeTheOwnerOfATradition(){
		TraditionMetadataModel textInfo = new TraditionMetadataModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("1");
		
		ClientResponse ownerChangeResponse = jerseyTest.resource().path("/tradition/1001").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());
	
		textInfo = new TraditionMetadataModel();
		textInfo.setName("RenamedTraditionName");
		textInfo.setLanguage("nital");
		textInfo.setIsPublic("0");
		textInfo.setOwnerId("0");
		
		ownerChangeResponse = jerseyTest.resource().path("/tradition/1001").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,textInfo);
		assertEquals(Response.Status.OK.getStatusCode(), ownerChangeResponse.getStatus());
	
	}
	
	/**
	 * Measure the speed to get a reading
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getReading(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/getreading/fromtradition/1001/withreadingid/20").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the speed to get all traditions in the database
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllTraditions(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/getalltraditions").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the speed to duplicate a reading and to merge two readings
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void duplicateAndMergeReading(){
		String jsonPayload = "{\"readings\":[" + duplicateReadingNodeId+ "], \"witnesses\":[\"A\",\"B\" ]}";
		List<ReadingModel> duplicatedReadings = jerseyTest.resource().path("/reading/duplicate/" + tradId)
				.type(MediaType.APPLICATION_JSON).post(new GenericType<List<ReadingModel>>() {
				}, jsonPayload);

		ClientResponse response = jerseyTest.resource()
				.path("/reading/merge/" + tradId + "/" + duplicateReadingNodeId + "/" + duplicatedReadings.get(0).getId())
				.type(MediaType.APPLICATION_JSON).post(ClientResponse.class);

		assertEquals(Status.OK, response.getClientResponseStatus());
	}
	
	/**
	 * Measure the time to split a reading and to compress it
	 */
//	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
//	@Test
//	public void splitReadingAndCompressReading(){
//		List<ReadingModel> splitedReadings = jerseyTest.resource().path("/reading/split/" + tradId + "/" + theRoot)
//				.type(MediaType.APPLICATION_JSON).post(new GenericType<List<ReadingModel>>() {});
//		
//		ClientResponse response = jerseyTest.resource()
//				.path("/reading/compress/" + tradId + "/" + splitedReadings.get(0).getDn1() + "/" + splitedReadings.get(1).getDn1())
//				.type(MediaType.APPLICATION_JSON).get(ClientResponse.class);
//		assertEquals(Status.OK, response.getClientResponseStatus());
//	}
	
	/**
	 * Measure the time to get all witnesses out of the database
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllWitnessesOfATradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/witness/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get all relationships of a witness
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllRelationships(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/getallrelationships/formtradition/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	
	/**
	 * Measure the time to export a Tradition as xml
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void exportTraditionAsXML(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/get/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to import a tradition as xml
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void importWithGraphMLParser(){
		GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();
	    
		try {
			importResource.parseGraphML(filename, "1", "Tradition");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
	}
	
	/**
	 * Measure the time to get the next reading of a witness
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getNextReadingOfAWitness(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/next/A/"+untoMe).get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get the previous reading of a witness
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getpreviousReadingOfAWitness(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/previous/A/" +untoMe).get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get all readings of a tradition
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllReadingsOfATradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get all identical readings of a tradition
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getIdenticalReadingsOfATradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/getidenticalreadings/fromtradition/"+tradId+"/fromstartrank/1/toendrank/8").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get all readings which could be identical
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getCouldBeIdentical(){
		ClientResponse actualResponse = jerseyTest.resource().path("/reading/couldbeidenticalreadings/fromtradition/"+tradId+"/fromstartrank/1/toendrank/15").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get a Witness as Plaintext
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAWitnessAsPlainText(){
		ClientResponse actualResponse = jerseyTest.resource().path("/witness/string/1001/W0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get a part of a Witness as Plaintext
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAPartOfAWitnessAsPlainText(){
		ClientResponse actualResponse = jerseyTest.resource().path("/witness/string/rank/1001/W0/2/5").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to get a witness as a json array
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getWitnessAsReadings(){
		ClientResponse actualResponse = jerseyTest.resource().path("/witness/getreadinglist/fromtradition/1001/ofwitness/W0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to create a relationship and remove it
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void createAndDeleteARelationship(){
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId = "";
		relationship.setSource(theRoot + "");
		relationship.setTarget(untoMe + "");
		relationship.setType("grammatical");
		relationship.setAlters_meaning("0");
		relationship.setIs_significant("true");
		relationship.setReading_a("the root");
		relationship.setReading_b("unto me");
		relationship.setScope("local");
		
		ClientResponse actualResponse = jerseyTest.resource().path("reaterelationship/intradition/"+tradId).type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId = actualResponse.getEntity(ReturnIdModel.class).getId();

		ClientResponse removalResponse = jerseyTest.resource().path("deleterelationshipsbyid/fromtradition/"+tradId+"/withrelationship/"+relationshipId).delete(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
	}
	
	/**
	 * Measure the time to get a user
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getUserById(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	

	/**
	 * Measure the time to get all traditions of a user
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getTraditionsOfAUser(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/traditions/0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Measure the time to create and delete a user
	 */
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void createAndDeleteAUser(){
	    String jsonPayload = "{\"isAdmin\":0,\"id\":1337}";
	    ClientResponse response = jerseyTest.resource().path("/user/create").type(MediaType.APPLICATION_JSON).post(ClientResponse.class, jsonPayload);
	    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    	
	    ClientResponse actualResponse = jerseyTest.resource().path("/user/1337").delete(ClientResponse.class);
    	assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());

	}

}
