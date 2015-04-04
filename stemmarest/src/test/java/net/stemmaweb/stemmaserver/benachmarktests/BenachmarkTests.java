package net.stemmaweb.stemmaserver.benachmarktests;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.ReturnIdModel;
import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
 * @author jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmarkTestGenerating")
public abstract class BenachmarkTests {
	@ClassRule
	public static TemporaryFolder tempFolder = new TemporaryFolder();
	
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();


	private User userResource = new User();
	private Tradition traditionResource = new Tradition();
	private Witness witnessResource = new Witness();
	private Reading readingResoruce = new Reading();
	private Relation relationResource = new Relation();
	
	String filename = "";

	
	private JerseyTest jerseyTest;
	
	@Before
	public void setUp() throws Exception {
		/*
		 * Create a JersyTestServer serving the Resource under test
		 * 
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
				.addResource(userResource)
				.addResource(traditionResource)
				.addResource(witnessResource)
				.addResource(relationResource)
				.addResource(readingResoruce).create();
		jerseyTest.setUp();
		
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\testTradition.xml";
		else
			filename = "src/TestXMLFiles/testTradition.xml";
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getUserById(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}

	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getTraditionsOfAUser(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/traditions/0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllWitnessesOfATradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/witness/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllTraditions(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/all").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void exportTraditionAsXML(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/get/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAllReadingsOfATradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/readings/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
//	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
//	@Test
//	public void getAReading(){
//		ClientResponse actualResponse = jerseyTest.resource().path("/reading/1001/20").get(ClientResponse.class);
//		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
//	}
//	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAWitnessAsString(){
		ClientResponse actualResponse = jerseyTest.resource().path("/witness/string/1001/W0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
//	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
//	@Test
//	public void getAWitnessFromRankToRank(){
//		ClientResponse actualResponse = jerseyTest.resource().path("/witness/string/1001/W0/30/80").get(ClientResponse.class);
//		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
//	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getAWitnessAsList(){
		ClientResponse actualResponse = jerseyTest.resource().path("/witness/list/1001/W0").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void createAndRemoveARelationShip(){
		RelationshipModel relationship = new RelationshipModel();
		String relationshipId = "";
		relationship.setSource("16");
		relationship.setTarget("23");
		relationship.setDe11("grammatical");
		relationship.setDe1("0");
		relationship.setDe6("true");
		relationship.setDe8("april");
		relationship.setDe9("showers");
		relationship.setDe10("local");
		ClientResponse actualResponse = jerseyTest.resource().path("/relation/1001/relationships").type(MediaType.APPLICATION_JSON).post(ClientResponse.class,relationship);
		relationshipId = actualResponse.getEntity(ReturnIdModel.class).getId();
		
		ClientResponse removalResponse = jerseyTest.resource().path("/relation/1001/relationships/"+relationshipId).delete(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void importWithGraphMLParser(){
		GraphMLToNeo4JParser importResource = new GraphMLToNeo4JParser();
	    
		try {
			importResource.parseGraphML(filename, "1");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
	}
	
	/**
	 * Shut down the jersey server
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		jerseyTest.tearDown();
	}
	


}
