package net.stemmaweb.stemmaserver.benachmarktests;

import net.stemmaweb.rest.Relation;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import com.sun.jersey.test.framework.JerseyTest;

/**
 * 
 * @author jakob
 *
 */
@RunWith(MockitoJUnitRunner.class)
@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark-lists")
public class BenachmarkTests {
	@ClassRule
	public static TemporaryFolder folder = new TemporaryFolder();
	  
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();
	/*
	 * Create a Mock object for the dbFactory.
	 */
	@Mock
	protected GraphDatabaseFactory mockDbFactory = new GraphDatabaseFactory();

	/*
	 * Create a Spy object for dbService.
	 */
	@Spy
	protected GraphDatabaseService mockDbService = mockDbFactory.newEmbeddedDatabase(folder.getRoot().getAbsolutePath());

	/*
	 * The Resource under test. The mockDbFactory will be injected into this
	 * resource.
	 */

	@InjectMocks
	private Relation relationResource;

	/*
	 * JerseyTest is the test environment to Test api calls it provides a
	 * grizzly http service
	 */
	private JerseyTest jerseyTest;
	
	@BeforeClass
	public static void prepareTheDatabase(){
		/*
		 * Fill the Testbench with a nice graph 9 users 2 traditions 5 witnesses with degree 10
		 */
		RandomGraphGenerator rgg = new RandomGraphGenerator();
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(folder.getRoot().getAbsolutePath());
		
		rgg.role(db, 9, 2, 5, 100);
		db.shutdown();
	}
	
	@Before
	public void setUp() throws Exception {
    	
    	/*
    	 * Manipulate the newEmbeddedDatabase method of the mockDbFactory to return 
    	 * new TestGraphDatabaseFactory().newImpermanentDatabase() instead
    	 * of dbFactory.newEmbeddedDatabase("database");
    	 */
		Mockito.when(mockDbFactory.newEmbeddedDatabase(Matchers.anyString())).thenReturn(mockDbService);  
		
		/*
		 * Avoid the Databaseservice to shutdown. (Override the shutdown method with nothing)
		 */
		Mockito.doNothing().when(mockDbService).shutdown();
		
		/*
		 * Create a JersyTestServer serving the Resource under test
		 */
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer().addResource(relationResource).create();
		jerseyTest.setUp();
	}
	
	@BenchmarkOptions(benchmarkRounds = 15, warmupRounds = 5)
	@Test
	public void test(){


	}

	
	/**
	 * Shut down the jersey server
	 * @throws Exception
	 */
	@After
	public void tearDown() throws Exception {
		mockDbService.shutdown();
		jerseyTest.tearDown();
	}

}
