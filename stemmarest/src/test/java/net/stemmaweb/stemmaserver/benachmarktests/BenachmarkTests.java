package net.stemmaweb.stemmaserver.benachmarktests;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

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
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-600Nodes")
public class BenachmarkTests {
	@ClassRule
	public static TemporaryFolder tempFolder = new TemporaryFolder();
	
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();


	private User userResource = new User();
	private Tradition traditionResource = new Tradition();
	
	private JerseyTest jerseyTest;
	
	@BeforeClass
	public static void prepareTheDatabase(){

		try {
			tempFolder.create();
			File dbPathFile = new File("database");
			Files.move(dbPathFile.toPath(), tempFolder.getRoot().toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		 * Fill the Testbench with a nice graph 9 users 2 traditions 5 witnesses with degree 10
		 */
		RandomGraphGenerator rgg = new RandomGraphGenerator();
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
		
		rgg.role(db, 3, 2, 10, 10);
		db.shutdown();
	}
	
	@Before
	public void setUp() throws Exception {
		/*
		 * Create a JersyTestServer serving the Resource under test
		 * 
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(userResource).addResource(traditionResource).create();
		jerseyTest.setUp();
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getUserByIdBenchmark(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/1").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}

	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void getTraditionsOfAUserBenchmark(){
		ClientResponse actualResponse = jerseyTest.resource().path("/user/traditions/1").get(ClientResponse.class);
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
	public void getAcompleteTradition(){
		ClientResponse actualResponse = jerseyTest.resource().path("/tradition/get/1001").get(ClientResponse.class);
		assertEquals(Response.Status.OK.getStatusCode(),actualResponse.getStatus());
	}
	
	/**
	 * Shut down the jersey server
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		jerseyTest.tearDown();
	}
	
	@AfterClass 
	public static void shutDown(){
		try {
			
			File dbPathFile = new File("database");
			FileUtils.deleteDirectory(dbPathFile);
			Files.move(tempFolder.getRoot().toPath(), dbPathFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
